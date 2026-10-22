"""NEXT iOS-inspired pages; source and policy tests, not pixel/device acceptance."""
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path
P=Path(__file__).resolve().parents[1]
S=P/'app/src/main/java/io/atmos/weather/next'
read=lambda name:(S/name).read_text(encoding='utf-8')
class IosPages(unittest.TestCase):
 def test_weather_real_hourly_ten_day_and_hero(self):
  weather=read('features/IosWeatherPage.kt')
  self.assertIn('IosText(city.name,35',weather)
  self.assertIn('95,FontWeight.ExtraLight',weather)
  self.assertIn('reading!!.hourly',weather)
  self.assertIn('reading!!.daily',weather)
  self.assertIn('10 日天氣預報',weather)
  self.assertIn('cityList=true',weather)
 def test_weather_forecast_is_upstream_not_placeholder(self):
  api=read('data/OpenMeteoWeather.kt')
  self.assertIn('hourly=temperature_2m,weather_code,precipitation_probability',api)
  self.assertIn('daily=temperature_2m_min,temperature_2m_max',api)
  self.assertIn('forecast_days=10',api)
  self.assertIn('parseHourly',api);self.assertIn('parseDaily',api)
 def test_weather_cache_source_disclosed(self):
  weather=read('features/IosWeatherPage.kt')
  self.assertIn('DataFreshnessPolicy.display',weather)
  self.assertIn('is WeatherLoad.Cached',weather)
 def test_clock_iOS_style_editing_and_fixed_wheels(self):
  alarm=read('features/IosAlarmPage.kt')
  for part in ('WheelPicker(listOf("上午","下午")','WheelPicker(hourItems','WheelPicker(minuteItems',
               '重複','標籤','提示聲','稍後提醒','刪除鬧鐘','IosColor.orange'):
   self.assertIn(part,alarm)
  self.assertNotIn('MaterialDatePicker',alarm)
 def test_clock_real_scheduler_and_explicit_permission(self):
  source=read('alarm/NextAlarms.kt')
  self.assertIn('.setAlarmClock(',source)
  self.assertIn('canScheduleExactAlarms()',source)
  self.assertIn('NextAlarmBootReceiver',source)
  ui=read('features/IosAlarmPage.kt')
  self.assertIn('ACTION_REQUEST_SCHEDULE_EXACT_ALARM',ui)
  self.assertIn('POST_NOTIFICATIONS',ui)
  self.assertIn('NextAlarmScheduler.schedule(ctx,alarm)',ui)
 def test_clock_safe_lab_storage(self):
  self.assertIn('next_alarms_v1',read('alarm/NextAlarms.kt'))
  self.assertNotIn('thinn_personal_tasks',read('alarm/NextAlarms.kt'))
 def test_settings_uses_grouped_drilldowns_and_state(self):
  settings=read('features/IosSettingsPage.kt')
  for t in ('IosGroup','IosSettingRow','天氣','鬧鐘與聲音','通知與即時動態','外觀與動態',
            '資料來源與隱私','攝氏度','華氏度','onFahrenheit','onHaptics'):
   self.assertIn(t,settings)
  root=read('features/NextApp.kt')
  for t in ('fahrenheit','reduceMotion','haptics','IosSettingsPage','IosAlarmPage','IosWeatherPage'):
   self.assertIn(t,root)
 def test_manifest_has_real_alarm_receivers(self):
  m=ET.parse(P/'app/src/main/AndroidManifest.xml').getroot()
  raw=ET.tostring(m,encoding='unicode')
  for t in ('NextAlarmRingActivity','NextAlarmReceiver','NextAlarmBootReceiver',
            'SCHEDULE_EXACT_ALARM','RECEIVE_BOOT_COMPLETED','POST_NOTIFICATIONS'):
   self.assertIn(t,raw)
 def test_only_independent_test_package_is_installable(self):
  g=(P/'app/build.gradle.kts').read_text()
  self.assertIn('applicationIdSuffix = ".nextioslab"',g)
  self.assertIn('FOUNDATION_NOT_FOR_RELEASE',g)
  self.assertNotIn('signingConfig = signingConfigs.getByName("debug")',g)
 def test_minimum_touch_text_and_no_material_visuals(self):
  gradle=(P/'app/build.gradle.kts').read_text()
  self.assertNotIn('material3:material3',gradle)
  for file in S.rglob('*.kt'):
   txt=file.read_text()
   self.assertNotIn('import androidx.compose.material',txt,str(file))
   for match in re.finditer(r'(?<!font)size=(\d+)',txt):
    self.assertGreaterEqual(int(match.group(1)),14,str(file))
if __name__=='__main__':unittest.main()
