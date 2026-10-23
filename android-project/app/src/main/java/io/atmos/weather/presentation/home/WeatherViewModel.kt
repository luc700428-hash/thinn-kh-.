package io.atmos.weather.presentation.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.atmos.weather.AppContainer
import io.atmos.weather.domain.model.*
import io.atmos.weather.domain.repository.WeatherDataRepository
import io.atmos.weather.domain.repository.WeatherLoadResult
import io.atmos.weather.domain.visual.*
import io.atmos.weather.widgets.WidgetSync
import io.atmos.weather.widgets.WidgetSnapshotStore
import io.atmos.weather.alerts.CwaCountyHazardFeed
import io.atmos.weather.alerts.CwaCountyHazard
import io.atmos.weather.alerts.CwaWarningFeed
import io.atmos.weather.alerts.CwaAlertWorker
import io.atmos.weather.alerts.CwaWarning
import io.atmos.weather.location.SavedCityStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface WeatherIntent {
    data class SelectCity(val city: WeatherCity) : WeatherIntent
    data object Refresh : WeatherIntent
}
/** GPS is separate from weather loading so slow location providers never appear inert. */
enum class LocationPhase { IDLE, PERMISSION, LOCATING, GEOCODING, WEATHER, SUCCESS, ERROR, DISABLED, DENIED }
data class LocationFeedback(
    val phase: LocationPhase = LocationPhase.IDLE,
    val detail: String = ""
) {
    val inProgress: Boolean
        get() = phase == LocationPhase.PERMISSION || phase == LocationPhase.LOCATING ||
            phase == LocationPhase.GEOCODING || phase == LocationPhase.WEATHER
}

data class WeatherUiState(
    val city: WeatherCity = SampleCities.all.first(),
    val locationFeedback: LocationFeedback = LocationFeedback(),
    val loading: Boolean = false,
    val weather: WeatherSnapshot? = null,
    val scene: WeatherScene? = null,
    val fromCache: Boolean = false,
    val stale: Boolean = false,
    val error: String? = null,
    val searching: Boolean = false,
    val searchResults: List<WeatherCity> = emptyList(),
    val savedCities: List<WeatherCity> = emptyList(),
    val searchExecuted: Boolean = false,
    val searchError: String? = null,
    val officialHazards: List<CwaCountyHazard> = emptyList(),
    val officialHazardsStatus: String = "尚未檢查縣市警特報",
    val cwaWarnings: List<CwaWarning> = emptyList(),
    val cwaCheckStatus: String = "尚未查詢中央氣象署特報",
    val cwaNationalNotices: List<CwaWarning> = emptyList(),
    val cwaNationalStatus: String = "尚未查詢全國官方公告"
)
class WeatherViewModel(
    private val repository: WeatherDataRepository,
    private val context: Context
) : ViewModel() {
    private val prefs = context.getSharedPreferences("atmos_settings", Context.MODE_PRIVATE)
    private val startCity = run {
        if (prefs.contains("gps_lat") && prefs.contains("gps_lon")) {
            WeatherCity("gps", prefs.getString("gps_name", "目前定位") ?: "目前定位",
                GeoPoint(Double.fromBits(prefs.getLong("gps_lat",0)),Double.fromBits(prefs.getLong("gps_lon",0))))
        } else {
            val id=prefs.getString("city_id",null)
            SampleCities.all.find { it.id==id } ?: if (id!=null && prefs.contains("lat"))
                WeatherCity(id,prefs.getString("city_name","自訂地點") ?: "自訂地點",
                    GeoPoint(Double.fromBits(prefs.getLong("lat",0)),Double.fromBits(prefs.getLong("lon",0))))
            else SampleCities.all.first()
        }
    }
    private val mutableState = MutableStateFlow(WeatherUiState(city = startCity, savedCities=SavedCityStore.all(context)))
    val uiState: StateFlow<WeatherUiState> = mutableState.asStateFlow()
    private var requestJob: Job? = null
    private var searchJob: Job? = null
    private var warningJob: Job? = null
    /** All GPS statuses are rendered both on the home screen and in the location sheet. */
    fun setLocationFeedback(phase: LocationPhase, text: String) {
        mutableState.update { it.copy(locationFeedback = LocationFeedback(phase, text)) }
    }
    fun clearLocationFeedback(phase: LocationPhase) {
        mutableState.update {
            if (it.locationFeedback.phase == phase) it.copy(locationFeedback=LocationFeedback()) else it
        }
    }
    fun useGpsCity(city: WeatherCity, fromRecentFix: Boolean = false) {
        setLocationFeedback(LocationPhase.WEATHER,
            if (fromRecentFix) "使用最近 10 分鐘的位置：${city.name}，正在更新天氣…"
            else "已找到 ${city.name}，正在更新當地天氣…")
        onIntent(WeatherIntent.SelectCity(city))
    }
    init {
        if (!prefs.contains("city_id")) {
            prefs.edit().putString("city_id",startCity.id).putString("city_name",startCity.name)
                .putLong("lat",startCity.coordinate.latitude.toBits())
                .putLong("lon",startCity.coordinate.longitude.toBits()).apply()
        }
        load(false); loadWarnings()
    }
    fun onIntent(intent: WeatherIntent) = when (intent) {
        is WeatherIntent.SelectCity -> {
            val c = intent.city
            val edit=prefs.edit()
            // Background alerts remain subscribed to the last actual GPS position.
            // Saved-city previews never silently replace the user's home location.
            if (c.id=="gps") {
                edit.putString("gps_name",c.name).putLong("gps_lat",c.coordinate.latitude.toBits())
                    .putLong("gps_lon",c.coordinate.longitude.toBits())
            }
            if (c.id=="gps" || !prefs.contains("gps_lat")) {
                edit.putString("city_id", c.id).putString("city_name", c.name)
                    .putLong("lat", c.coordinate.latitude.toBits()).putLong("lon", c.coordinate.longitude.toBits())
            }
            edit.apply()
            WidgetSnapshotStore.clear(context)
            mutableState.update { it.copy(city=c, weather=null, scene=null, error=null, officialHazards=emptyList(), cwaWarnings=emptyList(), searchResults= emptyList(),searchExecuted=false) }
            load(false)
            loadWarnings()
            CwaAlertWorker.checkNow(context)
        }
        WeatherIntent.Refresh -> load(true)
    }
    fun addSavedCity(city:WeatherCity) {
        mutableState.update{it.copy(savedCities=SavedCityStore.add(context,city))}
    }
    fun removeSavedCity(id:String) {
        mutableState.update{it.copy(savedCities=SavedCityStore.remove(context,id))}
    }
    /** Debounced query or explicit search; preserve earlier saved locations. */
    fun search(query: String) {
        searchJob?.cancel()
        if (query.trim().length < 2) {
            mutableState.update { it.copy(searchResults=emptyList(),searching=false,
                searchExecuted=true,searchError="請至少輸入兩個字") }
            return
        }
        searchJob = viewModelScope.launch {
            mutableState.update { it.copy(searching=true,searchExecuted=true,searchError=null,searchResults=emptyList()) }
            try {
                val name=query.trim().replace('臺','台')
                val alternatives=listOf(query.trim(),name,
                    name.substringAfterLast('·').trim(),
                    name.removeSuffix("鄉").removeSuffix("鎮").removeSuffix("區").removeSuffix("市"))
                    .filter { it.length >= 2 }.distinct().take(4)
                val localCandidates=(SampleCities.all+mutableState.value.savedCities)
                    .distinctBy { it.id }
                    .filter { io.atmos.weather.location.TaiwanPlaceSearch.matches(name,it.name) }
                val candidates=mutableListOf<WeatherCity>()
                var apiSucceeded=false
                for(term in alternatives) {
                    try {
                        val incoming=AppContainer.geoApi.search(term,count=40).results.orEmpty().map { it.asCity() }
                        candidates.addAll(incoming)
                        apiSucceeded=true
                    } catch (cancel: CancellationException) { throw cancel }
                      catch (_:Exception) { /* try another spelling; never invent locations */ }
                    if(candidates.size>=30) break
                }
                val results=(localCandidates+candidates).distinctBy { it.id }.take(40)
                mutableState.update {it.copy(searching=false,searchResults=results,
                    searchError=if(!apiSucceeded && localCandidates.isEmpty())
                        "目前無法連線搜尋地點，請稍後再試" else null)}
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { mutableState.update { it.copy(searching=false,searchResults=emptyList(),
                searchError="地點搜尋失敗，請確認網路後重試") } }
        }
    }
    fun refreshWarnings() {
        loadWarnings()
        CwaAlertWorker.checkNow(context)
    }
    private fun loadWarnings() {
        warningJob?.cancel()
        val city = mutableState.value.city
        if (!io.atmos.weather.location.LocationLabels.probablyTaiwan(city.coordinate) &&
            !city.name.startsWith("台灣") && !city.name.startsWith("臺灣")) {
            mutableState.update { it.copy(cwaWarnings = emptyList(), officialHazards=emptyList(),
                cwaNationalNotices=emptyList(),cwaNationalStatus="非臺灣定位",
                officialHazardsStatus="非台灣定位", cwaCheckStatus = "非台灣定位，無本地中央氣象署特報") }
            return
        }
        warningJob = viewModelScope.launch {
            val official=CwaCountyHazardFeed.fetch(city.name,city.coordinate)
            val result=CwaWarningFeed.fetch(city.name,city.coordinate)
            // Separate nationally published RSS notices from *verified active* county hazards.
            // These are notices only, not CAP-level alerts or proof of local applicability.
            val national=CwaWarningFeed.fetchNational()
            if (mutableState.value.city.id == city.id && mutableState.value.city.coordinate == city.coordinate) {
                mutableState.update { it.copy(
                    officialHazards=official.hazards, officialHazardsStatus=official.detail,
                    cwaWarnings=result.warnings, cwaCheckStatus=result.detail,
                    cwaNationalNotices=national.warnings,cwaNationalStatus=national.detail) }
            }
        }
    }
    private fun load(force: Boolean) {
        if (force) loadWarnings()
        requestJob?.cancel()
        val city = mutableState.value.city
        requestJob = viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            when (val result = repository.load(city, force)) {
                is WeatherLoadResult.Success -> {
                    mutableState.update {
                        it.copy(loading = false, weather = result.value, scene = sceneFor(result.value),
                            fromCache = result.fromCache, stale = result.stale)
                    }
                    if (city.id=="gps" && mutableState.value.locationFeedback.phase==LocationPhase.WEATHER) {
                        setLocationFeedback(LocationPhase.SUCCESS,"已定位至 ${city.name}，天氣更新完成")
                    }
                    if(mutableState.value.city.id == city.id) {
                        WidgetSnapshotStore.publish(context,result.value)
                        WidgetSync.updateVisible(context)
                    }
                }
                is WeatherLoadResult.Failure -> {
                    mutableState.update { it.copy(loading=false,error=result.cause) }
                    if (city.id=="gps" && mutableState.value.locationFeedback.phase==LocationPhase.WEATHER) {
                        setLocationFeedback(LocationPhase.ERROR,"定位成功，但天氣更新失敗：${result.cause}")
                    }
                }
            }
        }
    }
    class Factory(private val repository: WeatherDataRepository, private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T: ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(WeatherViewModel::class.java))
            return WeatherViewModel(repository, context.applicationContext) as T
        }
    }
}
