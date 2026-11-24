package io.atmos.weather.report

import android.app.Application
import android.app.Notification
import androidx.core.app.NotificationCompat
import org.robolectric.RuntimeEnvironment
import io.atmos.weather.domain.model.*
import io.atmos.weather.notifications.RichNotification
import io.atmos.weather.notifications.XiaomiFocusBridge
import io.atmos.weather.widgets.WidgetEvent
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class R27FeaturesTest {
    private val ctx get()=RuntimeEnvironment.getApplication()
    private fun snapshot(c:WeatherCondition)=WeatherSnapshot(WeatherCity("gps","嘉義市",GeoPoint(23.48,120.45)),Instant.EPOCH,Instant.EPOCH,"t",
        22.0,24.0,77,5.0,1012.0,c,true,null,null,emptyList())

    @Test fun aReportReplacesTheSkyNearbyForAnHourOnly() {
        val now=1_800_000_000_000L
        val r=WeatherReports.Report(WeatherReports.Kind.HEAVY_RAIN,now,23.48,120.45)
        assertEquals(r,WeatherReports.applicable(listOf(r),23.50,120.46,now+10*60_000))
        assertNull(WeatherReports.applicable(listOf(r),23.48,120.45,now+WeatherReports.LIFETIME_MS))
        assertNull(WeatherReports.applicable(listOf(r),24.0,121.0,now+60_000))
        val shown=WeatherReports.apply(snapshot(WeatherCondition.CLOUDY),r,ZoneId.of("UTC"))
        assertEquals(WeatherCondition.RAIN,shown.condition);assertEquals(65,shown.weatherCode)
        assertTrue(shown.observationDescription!!.startsWith("大雨（你"))
        assertEquals(22.0,shown.temperatureC,0.0)
        assertTrue(WeatherReports.disagrees(snapshot(WeatherCondition.CLOUDY),WeatherReports.Kind.HEAVY_RAIN))
        assertFalse(WeatherReports.disagrees(snapshot(WeatherCondition.CLOUDY),WeatherReports.Kind.OVERCAST))
        assertEquals(111.2,WeatherReports.distanceKm(23.0,120.0,24.0,120.0),0.5)
        WeatherReports.save(ctx,r);assertEquals(1,WeatherReports.load(ctx).size)
        WeatherReports.clear(ctx,23.48,120.45);assertTrue(WeatherReports.load(ctx).isEmpty())
    }

    @Test fun widgetsGetEveryEventInOrderForTheRotatingBadge() {
        val now=1_800_000_000_000L
        val warnings=JSONArray().put(JSONObject().put("t","陸上強風特報").put("s",now-1000).put("e",now+3600_000))
            .put(JSONObject().put("t","大雨特報").put("s",now-500).put("e",now+7200_000))
            .put(JSONObject().put("t","低溫特報").put("s",now+3600_000).put("e",now+9000_000))
        val quake=JSONObject().put("t","M 5.1").put("d","最大震度 3").put("u",now+600_000).toString()
        val all=WidgetEvent.pickAll(null,warnings.toString(),quake,now,ZoneId.of("Asia/Taipei"))
        assertEquals(listOf("陸上強風特報","大雨特報","低溫特報","M 5.1"),all.map{it.title})
        assertTrue(all[2].detail.startsWith("預告"))
        assertEquals(all.first(),WidgetEvent.pickAll(null,warnings.toString(),null,now,ZoneId.of("Asia/Taipei")).first())
    }

    @Test fun notificationsGetAPictureCollapsedAndEveryFactExpanded() {
        val plain=NotificationCompat.Builder(ctx,"c").setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("大雨特報").setContentText("嘉義市 · 有效至 18:00")
            .setStyle(NotificationCompat.BigTextStyle().bigText("長文說明"))
            .addExtras(RichNotification.card(RichNotification.Card("大雨特報","嘉義市",listOf("類型" to "大雨","有效至" to "18:00"),"長文說明")))
            .build()
        val rich=RichNotification.enrich(ctx,plain,"大雨特報","嘉義市 · 有效至 18:00",XiaomiFocusBridge.Kind.WEATHER_WARNING)
        @Suppress("DEPRECATION") assertNotNull(rich.contentView)
        @Suppress("DEPRECATION") assertNotNull(rich.bigContentView)
        assertEquals("大雨特報",rich.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        val card=RichNotification.cardFor(ctx,plain,"大雨特報","x",XiaomiFocusBridge.Kind.WEATHER_WARNING)
        assertEquals(2,card.facts.size);assertTrue(card.footer.startsWith("thinn-khì"))
        // Promoted live updates keep the system's own look.
        val live=NotificationCompat.Builder(ctx,"c").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("t")
            .addExtras(android.os.Bundle().apply{putBoolean("android.requestPromotedOngoing",true)}).build()
        assertSame(live,RichNotification.enrich(ctx,live,"t","d",XiaomiFocusBridge.Kind.SEISMIC_EEW))
    }
}
