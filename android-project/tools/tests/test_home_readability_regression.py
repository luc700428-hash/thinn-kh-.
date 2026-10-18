"""Regression gates based on 2026-10-16 screenshot: text wall and generic house."""
from pathlib import Path
import json
import unittest
ROOT=Path(__file__).resolve().parents[2]
JAVA=ROOT/'app/src/main/java/io/atmos/weather'
ASSETS=ROOT/'app/src/main/assets/landmarks'

class HomeReadabilityTests(unittest.TestCase):
    def test_no_raw_diagnostic_wall_on_home(self):
        home=(JAVA/'presentation/home/WeatherHomeScreen.kt').read_text()
        self.assertIn('DailyGuidanceCard(snapshot,state.stale)',home)
        self.assertIn('showLifestyleDetails',home)
        self.assertNotIn('Text("其他天氣提醒"',home)
        self.assertNotIn('Text("判斷依據：${plan.evidence}"',home)
        # Detailed weather model/audit text is retained, but only inside a sheet.
        self.assertIn('ModalBottomSheet(onDismissRequest={showLifestyleDetails=false})',home)
    def test_key_weather_and_outfit_are_not_hidden_behind_a_detail_link(self):
        home=(JAVA/'presentation/home/WeatherHomeScreen.kt').read_text()
        model=(JAVA/'presentation/home/HomeWeatherBriefing.kt').read_text()
        # In the weather page itself (before any modal), the critical weather
        # values and both actionable clothing/rain rows remain visible.
        self.assertIn('HomeKeyWeatherCard(snapshot, state.stale)',home)
        self.assertIn('HomeAdviceRow("穿著建議"',home)
        self.assertIn('HomeAdviceRow("雨具建議"',home)
        self.assertNotIn('查看詳情  →',home)
        # V23 R8: the requested homepage RSS alert summary is removed.
        # Real emergency weather advisories remain available via workers / notices.
        visible=home.split('// 依本次需求，首頁不再顯示中央氣象署 RSS 特報摘要區塊')[1].split('// Atmospheric light and live data')[0]
        self.assertNotIn('state.cwaWarnings.isNotEmpty()',visible)
        self.assertLess(visible.index('HomeKeyWeatherCard('),visible.index('DailyGuidanceCard('))
        self.assertIn('未來六小時降雨',model)
        self.assertIn('尚無降雨機率資料',model)
        self.assertIn('美國標準模型推估',model)
        self.assertIn('非臺灣環境部官方 AQI',model)

    def test_optical_live_weather_replaces_2d_landmarks(self):
        home=(JAVA/'presentation/home/WeatherHomeScreen.kt').read_text()
        light=(JAVA/'ui/ThinnAtmosphereFocus.kt').read_text()
        self.assertIn('ThinnAtmosphereFocus(',home)
        self.assertNotIn('LandmarkCanvas(',home)
        self.assertIn('snapshot.windKmh',light)
        self.assertIn('snapshot.humidityPct',light)
    def test_chiayi_county_now_has_separate_place_models(self):
        c=json.loads((ASSETS/'catalog_v2.json').read_text())
        expected=['阿里山森林鐵道','北回歸線太陽館','故宮南院','奮起湖車站','中央噴水圓環']
        for title in expected:
            matches=[p for p in c['landmarks'] if p['name']==title]
            self.assertEqual(len(matches),1)
            place=matches[0]
            self.assertEqual(place['geometry_status'],'original_prototype')
            for key in ['model']:
                asset=ASSETS.parent/place[key]
                self.assertGreater(asset.stat().st_size,1000,place['name'])
            self.assertEqual((ASSETS.parent/place['model']).read_bytes()[:4],b'glTF')
    def test_weather_forecast_title_and_glass_contrast(self):
        forecast=(JAVA/'presentation/home/InteractiveForecast.kt').read_text()
        self.assertIn('Text("24 小時預報",fontSize=21.sp',forecast)
        glass=(JAVA/'presentation/home/WeatherHomeScreen.kt').read_text()
        self.assertIn('ThinnOpticalMaterial.surface',glass)
        self.assertIn('val rim:',(JAVA/'ui/ThinnOpticalMaterial.kt').read_text())
    def test_release_notes_reflect_real_limited_progress(self):
        notes=(ROOT/'app/src/main/assets/current_release_notes.md').read_text()
        self.assertIn('穿搭」長文',notes)
        self.assertIn('獨立 GLB 原型',notes)
        self.assertIn('實機',notes)
if __name__=='__main__': unittest.main()
