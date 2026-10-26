"""Source-level UI contract checks, not a replacement for emulator/real-device tests."""
from pathlib import Path
import unittest

BASE = Path(__file__).resolve().parents[2] / 'app/src/main/java/io/atmos/weather'

class IOSUIContractTests(unittest.TestCase):
    def source(self,path): return (BASE / path).read_text(encoding='utf-8')

    def test_shared_tokens_used_by_theme(self):
        tokens=self.source('ui/ThinnDesignTokens.kt')
        self.assertIn('fun ThinnAppTheme',tokens)
        self.assertIn('darkColorScheme',tokens)
        self.assertIn('fun GlassAppTheme',self.source('ui/GlassSheets.kt'))

    def test_settings_update_and_tools_tasks_are_wired(self):
        settings=self.source('ui/GlassNavigation.kt')
        weather=self.source('presentation/home/WeatherHomeScreen.kt')
        self.assertIn('onUpdateCenter',settings)
        self.assertIn('onTasks',settings)
        self.assertIn('onTasks = { tasksOpen = true }',weather)
        self.assertIn('onUpdateCenter = { updatePageOpen = true }',weather)
        self.assertIn('TaskCenterPage(onClose={tasksOpen=false})',weather)

    def test_time_selector_is_not_canvas_only(self):
        clock=self.source('ui/GlassClockDial.kt')
        self.assertIn('IOSClockDrums(',clock)
        shared=self.source('ui/IOSDrumPicker.kt')
        self.assertIn('contentDescription=label',shared)
        self.assertIn('customActions=',shared)
        self.assertIn('detectVerticalDragGestures',shared)
        self.assertNotIn('Canvas(',clock)

    def test_personal_tasks_have_persistent_crud_and_optional_reminders(self):
        store=self.source('task/TaskStore.kt')
        editor=self.source('task/TaskCenterPage.kt')
        worker=self.source('task/TaskReminderScheduler.kt')
        self.assertIn('SharedPreferences' if False else 'getSharedPreferences',store)
        for keyword in ['新增事項','編輯事項','刪除事項','儲存','完成']:
            self.assertTrue(keyword in editor or keyword in self.source('ui/GlassNavigation.kt'),keyword)
        self.assertIn('OneTimeWorkRequestBuilder',worker)
        self.assertIn('POST_NOTIFICATIONS',worker)
        self.assertIn('open_tasks',worker)
        self.assertIn('!it.completed',editor)
        self.assertIn('TaskReminderScheduler.sync(',self.source('MainActivity.kt'))

    def test_reduced_motion_and_real_download_progress(self):
        source=self.source('update/AppUpdatePage.kt')
        self.assertIn('reduce_motion',source)
        # NEXT progress comes from AppUpdater's actual downloaded/total bytes.
        # Do not require the removed legacy ThinnProgressLine control.
        service=self.source('update/OtaDownloadService.kt')
        self.assertIn('downloaded.longValue=bytes',service)
        self.assertIn('OtaDownloadService.start(context,release)',source)
        self.assertIn('totalBytes.value=total',service)
        self.assertIn('NextOtaState.Downloading(',source)
        self.assertIn('downloaded.toFloat()/it',source)
        self.assertIn('NextOtaStatusCard(',source)
        next_card=self.source('next/NextOtaPresentation.kt')
        self.assertIn('state.progress.coerceIn(0f, 1f)',next_card)
        self.assertIn('ProgressBarRangeInfo(p, 0f..1f)',next_card)
        self.assertIn('暫停下載',source)
        self.assertIn('OtaDownloadService.cancel(context)',source)
        self.assertIn('ensureActive()',self.source('update/AppUpdater.kt'))

    def test_accessible_core_controls(self):
        controls=self.source('ui/GlassControls.kt')
        self.assertIn('Modifier.toggleable(',controls)
        self.assertIn('Role.Switch',controls)
        self.assertIn('setProgress {',controls)
        self.assertIn('detectHorizontalDragGestures(',controls)
        self.assertIn('progressBarRangeInfo=',controls)
        self.assertIn('ThinnDesignTokens.radiusControl',controls)
        self.assertIn('contentDescription=label',self.source('ui/GlassNavigation.kt'))

if __name__ == '__main__':unittest.main()
