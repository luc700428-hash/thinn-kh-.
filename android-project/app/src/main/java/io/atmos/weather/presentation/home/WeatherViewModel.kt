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
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.atmos.weather.location.*

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
    val cwaCheckStatus: String = "尚未查詢中央氣象署特報"
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
    private val normalizedStartCity = LocationLabels.city(startCity)
    private val mutableState = MutableStateFlow(WeatherUiState(city = normalizedStartCity, savedCities=SavedCityStore.all(context)))
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
        WidgetSnapshotStore.select(context,normalizedStartCity)
        WidgetSync.refreshCached(context)
        load(false); loadWarnings()
    }
    fun onIntent(intent: WeatherIntent) = when (intent) {
        is WeatherIntent.SelectCity -> {
            val c = LocationLabels.city(intent.city)
            RecentCityStore.record(context,c)
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
            WidgetSnapshotStore.select(context,c)
            WidgetSync.refreshCached(context)
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
    private var searchGeneration=0
    /** Local results appear first; old network responses cannot replace a newer query. */
    fun search(query:String) {
        searchJob?.cancel();val generation=++searchGeneration
        val q=query.trim()
        if(q.isBlank()){mutableState.update{it.copy(searchResults=emptyList(),searching=false,searchExecuted=false,searchError=null)};return}
        searchJob=viewModelScope.launch {
            val saved=mutableState.value.savedCities
            val local=withContext(Dispatchers.Default){CitySearchRules.distinct(
                OfflineCitySearch.search(context,q)+(saved+RecentCityStore.read(context)).filter{CitySearchRules.score(q,it.name)>0})}
            mutableState.update{it.copy(searchResults=local,searching=q.length>=2,searchExecuted=true,searchError=null)}
            if(q.length<2||CitySearchRules.coordinate(q)!=null){mutableState.update{it.copy(searching=false)};return@launch}
            var succeeded=false
            val remote=supervisorScope {
                val requests=listOf(async {withTimeoutOrNull(6500){runCatching{AppContainer.geoApi.search(q,count=40).results.orEmpty().mapNotNull{runCatching{it.asCity()}.getOrNull()}}.getOrNull()}},
                    async {withTimeoutOrNull(6500){runCatching{AppContainer.geoApi.search(q.replace('臺','台'),count=30,countryCode="TW").results.orEmpty().mapNotNull{runCatching{it.asCity()}.getOrNull()}}.getOrNull()}})
                requests.flatMap {it.await()?.also{succeeded=true}.orEmpty()}
            }
            if(generation==searchGeneration)mutableState.update{it.copy(searching=false,
                searchResults=CitySearchRules.distinct(local+remote),searchError=if(!succeeded)"網路搜尋暫不可用，已保留本機地點" else null)}
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
                officialHazardsStatus="非台灣定位", cwaCheckStatus = "非台灣定位，無本地中央氣象署特報") }
            return
        }
        warningJob = viewModelScope.launch {
            val official=CwaCountyHazardFeed.fetch(city.name,city.coordinate)
            val result=CwaWarningFeed.fetch(city.name,city.coordinate)
            if (mutableState.value.city.id == city.id && mutableState.value.city.coordinate == city.coordinate) {
                mutableState.update { it.copy(
                    officialHazards=official.hazards, officialHazardsStatus=official.detail,
                    cwaWarnings=result.warnings, cwaCheckStatus=result.detail) }
            }
        }
    }
    private fun load(force: Boolean) {
        if (force) loadWarnings()
        requestJob?.cancel()
        val city = mutableState.value.city
        requestJob = viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            val result = repository.load(city, force)
            if(mutableState.value.city.id!=city.id||mutableState.value.city.coordinate!=city.coordinate)return@launch
            when (result) {
                is WeatherLoadResult.Success -> {
                    mutableState.update {
                        it.copy(loading = false, weather = result.value, scene = sceneFor(result.value),
                            fromCache = result.fromCache, stale = result.stale)
                    }
                    if (city.id=="gps" && mutableState.value.locationFeedback.phase==LocationPhase.WEATHER) {
                        setLocationFeedback(LocationPhase.SUCCESS,"已定位至 ${city.name}，天氣更新完成")
                    }
                    if(mutableState.value.city.id == city.id) {
                        if(WidgetSnapshotStore.publish(context,result.value))WidgetSync.updateVisible(context)
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
