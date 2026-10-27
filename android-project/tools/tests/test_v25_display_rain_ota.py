"""Integration and contrast contracts alongside numerical Kotlin physics/version tests."""
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[2]
B=ROOT/'app/src/main/java/io/atmos/weather'
def src(p):return (B/p).read_text()
def luminance(c):
 rgb=[int(c[i:i+2],16)/255 for i in (0,2,4)]
 return sum(w*(x/12.92 if x<=.04045 else ((x+.055)/1.055)**2.4) for w,x in zip((.2126,.7152,.0722),rgb))
def contrast(a,b):
 x,y=sorted((luminance(a),luminance(b)));return (y+.05)/(x+.05)
class LatestPhysicsContract(unittest.TestCase):
    def test_retained_updates_are_revalidated_and_completed_flags_clear(self):
        s=src('update/OtaRetainedSession.kt')
        for term in ('isNewerThanInstalled','reconcileInstalled','pending.value=null','ready.value=false','lastError.value=null','installerPrompted.value=false'):
            self.assertIn(term,s)
        self.assertIn('NextOtaState.UpToDate',src('update/AppUpdatePage.kt'))
        self.assertIn('你的軟體已是最新版本',src('next/NextOtaPresentation.kt'))
        self.assertIn('currentCerts != updateCerts',src('update/AppUpdater.kt'))
    def test_rain_has_one_visible_collision_world_and_real_card_geometry(self):
        home=src('presentation/home/WeatherHomeScreen.kt')
        sky=home.split('private fun SkyEffects',1)[1].split('private fun AtmosphereCanvas',1)[0]
        self.assertIn('RainCollisionScene(rainIntensity',home)
        self.assertNotIn('ContinuousRain(',sky);self.assertIn('precipitation=false',sky)
        for page in ('ui/AppleComponents.kt','ui/ThinnAtmospherePanel.kt'):self.assertIn('weatherCollisionSurface(',src(page))
        render=src('ui/RainCollisionScene.kt')
        for term in ('positionInWindow()','withFrameNanos','world.sparks.forEach','neckAge'):self.assertIn(term,render)
    def test_no_fixed_visual_frame_cap_remains(self):
        for page in ('presentation/home/WeatherSurface.kt','earthquake/SeismicWarningOverlay.kt','earthquake/SeismicLiveMapPage.kt'):
            s=src(page);self.assertNotIn('delay(33L)',s);self.assertNotIn('delay(120)',s)
        self.assertIn('HighRefreshHost()',src('ui/ThinnDesignTokens.kt'))
        self.assertIn('setRequestedFrameRate',src('ui/HighRefreshWindow.kt'))
    def test_radar_foregrounds_have_body_text_contrast_in_both_modes(self):
        radar=src('ui/RadarThreeHourCard.kt')
        self.assertIn('val ink=cards?.ink?:MaterialTheme.colorScheme.onSurface',radar);self.assertIn('color=muted',radar)
        for ink,bg in [('000000','FFFFFF'),('4B5563','D4E4ED'),('FFFFFF','1C1C1E'),('C7D4E0','1C1C1E'),('8A4300','D4E4ED')]:
            self.assertGreaterEqual(contrast(ink,bg),4.5,(ink,bg))
    def test_liquid_effect_samples_background_with_safe_fallback(self):
        s=src('ui/LiquidOpticsEngine.kt');host=src('ui/ThinnBackdrop.kt')
        for term in ('RuntimeShader','scene.eval(uv)','createRuntimeShaderEffect','focus','strength'):self.assertIn(term,s)
        self.assertIn('Build.VERSION.SDK_INT>=33',host)
        self.assertIn('source in LocalBackdropAncestors.current',host)
        self.assertIn('ancestors+source',host)
        self.assertIn('clipRect(sample.left,sample.top,sample.right,sample.bottom)',host)
        self.assertIn('glass.clip=true',host)
        self.assertIn('appearance.glassFrost',host);self.assertIn('appearance.edgeLight',host)
        self.assertIn('input!==lastInput',s)
    def test_storms_have_direction_timestamps_and_separate_id(self):
        s=src('typhoon/TyphoonCenterPage.kt')
        for term in ('focusStorm','it.stormId==focused','drawDirectedTrack','drawTrackTime'):self.assertIn(term,s)
        self.assertIn('dashPathEffect',src('typhoon/DirectedTrackPaint.kt'))
        self.assertNotIn('Dialog(onDismissRequest',src('typhoon/StormTimelineOverlay.kt'))
if __name__=='__main__':unittest.main()
