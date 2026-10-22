"""Source-level stage 9 smoke tests: Android CI + device tests still required."""
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
APP=ROOT/'app/src/main/java/io/atmos/weather'

class V25Stage9WidgetTaskIsland(unittest.TestCase):
    @staticmethod
    def src(path):return (APP/path).read_text(encoding='utf-8')

    def test_widgets_never_call_cached_values_live(self):
        src=self.src('widgets/AtmosWidgets.kt')
        core=self.src('widgets/WidgetFreshness.kt')
        self.assertIn('WidgetFreshness.state(stamp,System.currentTimeMillis())',src)
        self.assertIn('WidgetFreshness.detail(freshness)',src)
        self.assertNotIn('thinn-khì  /  LIVE',src)
        self.assertIn('nowMillis < fetchedAtMillis',core)
        self.assertIn('90L * 60_000L',core)

    def test_tasks_cannot_notify_before_due_or_after_edit(self):
        src=self.src('task/TaskReminderScheduler.kt')
        policy=self.src('task/TaskReminderPolicy.kt')
        self.assertIn('.putLong("due_millis",task.dueMillis())',src)
        self.assertIn('TaskReminderPolicy.sameSchedule(task.dueMillis(),scheduledAt)',src)
        self.assertIn('TaskReminderPolicy.isDue(task.dueMillis(),now)',src)
        self.assertIn('nowMillis >= currentDueMillis',policy)
        self.assertNotIn('task.dueMillis()>now+60_000L',src)

    def test_private_notes_never_emitted_to_public_vendor_summaries(self):
        src=self.src('task/TaskReminderScheduler.kt')
        self.assertIn('"個人事項提醒","解鎖 App 查看事項內容",XiaomiFocusBridge.Kind.TASK_REMINDER',src)
        self.assertIn('.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)',src)
        self.assertNotIn('task.title.take(36),"個人事項提醒"',src)

    def test_vendor_fallback_cleans_all_extensions_and_island_state(self):
        src=self.src('notifications/WeatherNotificationDispatcher.kt')
        for key in ('miui.focus.param','miui.focus.pics','miui.focus.actions','XiaomiFocusBridge.finish(id)'):
            self.assertIn(key,src)
        self.assertIn('manager.notify(id, notification)',src)

if __name__=='__main__':unittest.main()
