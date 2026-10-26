"""R6 source-level invariants. NOT Android/JVM/physical-device validation."""
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[2]
APP=ROOT/'app/src/main/java/io/atmos/weather'
def src(path):return (APP/path).read_text()

class SeismicR6Tests(unittest.TestCase):
    def test_urgent_ui_no_old_verbose_disclaimers(self):
        ui=src('earthquake/SeismicWarningOverlay.kt')
        self.assertNotIn('僅在前景收到有效來源資料時顯示',ui)
        self.assertNotIn('震波到達倒數：缺少可驗證資料',ui)
        self.assertIn('if(replay)"歷史地震演練" else "地震速報"',ui)
        self.assertIn('資料來源：${incident.source}',ui)
    def test_only_real_source_intensities(self):
        source=src('earthquake/SeismicWarningCounties.kt')
        self.assertIn('SeismicWavePhysics.intensityLabel(values[county])',source)
        self.assertIn('allFromProvider',source)
        self.assertNotIn('epicentralKm(',source)
        ui=src('earthquake/SeismicWarningOverlay.kt')
        self.assertIn('待來源提供',ui)
    def test_map_p_s_is_not_a_fake_countdown(self):
        ui=src('earthquake/SeismicWarningOverlay.kt')
        self.assertIn('SeismicWavePhysics.wavefront(',ui)
        self.assertIn('SeismicGeo.kmPerPixel(',ui)
        self.assertIn('rememberDisplayWallClock',ui)
        self.assertIn('withFrameNanos',src('ui/DisplayFrameClock.kt'))
        self.assertIn('非到達預測',ui)
    def test_alarm_bounded_stops_on_dismiss(self):
        service=src('earthquake/SeismicContinuousWarning.kt')
        self.assertIn('postDelayed(timeout,35_000L)',service)
        self.assertIn('SeismicAlertPolicy.soundEnabled',service)
        self.assertIn('ringerMode',service)
        self.assertIn('currentInterruptionFilter',service)
        ui=src('earthquake/SeismicWarningOverlay.kt')
        self.assertIn('onDispose { if(!replay && !simulation) SeismicContinuousWarning.stop() }',ui)
        self.assertIn('if(!replay && !simulation) SeismicContinuousWarning.start(context,incident)',ui)
    def test_notification_triggers_tone(self):
        self.assertIn('SeismicContinuousWarning.start(this,quake)',src('earthquake/ExpTechSeismicService.kt'))
    def test_earthquake_report_banner_sources(self):
        home=src('presentation/home/WeatherHomeScreen.kt')
        self.assertIn('中央氣象署 · 最新地震報告',home)
        self.assertIn('最新地震報告 · 第三方彙整',home)
        self.assertIn('RecentEarthquakeNoticeStore.current(context)',home)
        self.assertIn('RecentEarthquakeNoticeStore.dismiss(context,report.id)',home)
    def test_report_persists_only_verified_new_event(self):
        worker=src('earthquake/EarthquakeReportWorker.kt')
        self.assertIn('RecentEarthquakeNoticeStore.save(ctx,latest,isOfficial)',worker)
        cache=src('earthquake/RecentEarthquakeNotice.kt')
        self.assertIn('MAX_AGE_MS=30*60*1000L',cache)
        self.assertIn('report.origin.toEpochMilli()',cache)
    def test_no_copied_dpip_sources(self):
        src_java=str(APP)
        self.assertTrue((APP/'earthquake/SeismicWarningOverlay.kt').is_file())
        self.assertNotIn('com.expt.eew',src_java)
    def test_replay_is_not_current_quake(self):
        warning=src('earthquake/SeismicWarningOverlay.kt')
        self.assertIn('過去事件回放 · 非即時警報',warning)
        self.assertIn('if(!replay && !simulation)',warning)
    def test_native_eew_title(self):
        self.assertIn('收到地震預警 · 第三方來源',src('earthquake/SeismicLiveMapPage.kt'))
if __name__=='__main__':unittest.main()
