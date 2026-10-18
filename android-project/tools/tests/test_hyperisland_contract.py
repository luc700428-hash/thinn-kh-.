"""R12.0 source-level safeguards (Android full compile still required in CI)."""
import unittest
from pathlib import Path
J=Path(__file__).resolve().parents[2]/'app/src/main/java/io/atmos/weather'
def read(s): return (J/s).read_text(encoding='utf-8')

class HyperIslandContract(unittest.TestCase):
    def test_official_permission_and_os_version(self):
        x=read('notifications/XiaomiFocusBridge.kt')
        for key in ('notification_focus_protocol','canShowFocus','miui.focus.param','param_v2',
                    'miui.focus.actions','smallIslandArea','bigIslandArea'):
            self.assertIn(key,x)
        self.assertIn('shouldUseAlarmIsland',x)
        self.assertNotIn('android.os.SystemProperties',x)
    def test_p0_never_displaces_official_eew(self):
        x=read('notifications/IslandPriorityPolicy.kt')
        self.assertIn('SEISMIC(0,',x)
        self.assertIn('priority.rank <= active.priority.rank',x)
    def test_alarm_fallback_full_screen_and_real_buttons(self):
        x=read('alarm/WeatherAlarmAudioService.kt')
        self.assertIn('if (!islandPreferred) b.setFullScreenIntent',x)
        self.assertIn('XiaomiFocusBridge.decorate(ctx,note',x)
        self.assertIn('val islandPreferred = XiaomiFocusBridge.shouldUseAlarmIsland(ctx)',x)
        self.assertIn('ACTION_SNOOZE',x)
        self.assertIn('ACTION_STOP',x)
    def test_no_fake_minute_rain(self):
        x=read('notifications/IslandEventPublisher.kt')
        self.assertIn('VerifiedRainEvidence',x)
        self.assertIn('10 * 60_000L',x)
        self.assertIn('minutesUntilRain !in 1..30',x)
    def test_p3_real_task_and_private_payload(self):
        x=read('task/TaskReminderScheduler.kt')
        self.assertIn('Kind.TASK_REMINDER',x)
        self.assertIn('"個人事項提醒"',x)
        self.assertIn('task.note.ifBlank',x)
    def test_all_standard_notifications_remain(self):
        x=read('notifications/WeatherNotificationDispatcher.kt')
        self.assertIn('manager.notify(id, notification)',x)
        self.assertIn('notification.extras.remove("miui.focus.param")',x)

if __name__=='__main__': unittest.main()
