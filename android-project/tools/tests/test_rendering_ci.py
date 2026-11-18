"""Exercise CI watchdog/failure evidence and completed-device-result acceptance."""
from pathlib import Path
import importlib.util
import os
import re
import shutil
import subprocess
import tempfile
import unittest

TOOLS = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('rendering_results', TOOLS/'verify_rendering_results.py')
results = importlib.util.module_from_spec(spec)
spec.loader.exec_module(results)


def report(names, extra=''):
    cases = ''.join(f'<testcase classname="{results.CLASS}" name="{name}">{extra}</testcase>' for name in names)
    return '<testsuite>' + cases + '</testsuite>'


class ResultTests(unittest.TestCase):
    def check_report(self, text):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root/'TEST-device.xml').write_text(text)
            return results.verify(root)

    def test_expected_results_match_every_instrumentation_method(self):
        source=(TOOLS.parent/'app/src/androidTest/java/io/atmos/weather/ui/RenderingRegressionTest.kt').read_text()
        names=set(re.findall(r'@Test\s*(?:\([^)]*\))?\s+fun\s+(\w+)',source))
        self.assertEqual(names,results.EXPECTED)

    def test_requires_every_real_passing_result(self):
        self.assertEqual(self.check_report(report(results.EXPECTED)), len(results.EXPECTED))

    def test_rejects_partial_or_empty_reports(self):
        for names in ([], sorted(results.EXPECTED)[:2]):
            with self.assertRaisesRegex(ValueError, 'Missing'):
                self.check_report(report(names))

    def test_rejects_failures_errors_and_skips(self):
        for extra in ('<failure/>', '<error/>', '<skipped/>'):
            with self.assertRaisesRegex(ValueError, 'did not pass'):
                self.check_report(report(results.EXPECTED, extra))

    def test_rejects_duplicate_results(self):
        with self.assertRaisesRegex(ValueError, 'duplicate'):
            self.check_report(report(list(results.EXPECTED)*2))


class AttemptTests(unittest.TestCase):
    CRASH = '<failure></failure>'
    REAL = '<failure message="java.lang.AssertionError: Held lens hid the scene">java.lang.AssertionError: Held lens hid the scene\n\tat RenderingRegressionTest</failure>'

    def attempts(self, *reports):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        roots = []
        for index, text in enumerate(reports, 1):
            root = Path(directory.name)/str(index); root.mkdir()
            (root/'TEST-device.xml').write_text(text)
            roots.append(root)
        return roots

    def test_tests_lost_with_the_emulator_are_rerun_and_then_accepted(self):
        names = sorted(results.EXPECTED)
        first = report(names[:5]) .replace('</testsuite>', '') + report([names[5]], self.CRASH)[len('<testsuite>'):]
        roots = self.attempts(first)
        left = results.remaining(roots).split(',')
        self.assertEqual(left, [f'{results.CLASS}#{n}' for n in names[5:]])
        roots = self.attempts(first, report(names[5:]))
        self.assertEqual(results.verify_attempts(roots), len(results.EXPECTED))

    def test_a_real_assertion_failure_is_never_retried_away(self):
        names = sorted(results.EXPECTED)
        roots = self.attempts(report(names[:1], self.REAL), report(names))
        with self.assertRaisesRegex(ValueError, 'did not pass'):
            results.remaining(roots[:1])
        with self.assertRaisesRegex(ValueError, 'did not pass'):
            results.verify_attempts(roots)

    def test_errors_and_skips_are_not_treated_as_crashes(self):
        for extra in ('<error></error>', '<skipped/>'):
            with self.assertRaisesRegex(ValueError, 'did not pass'):
                results.verify_attempts(self.attempts(report(results.EXPECTED, extra)))

    def test_a_crash_that_never_reran_is_still_missing(self):
        names = sorted(results.EXPECTED)
        with self.assertRaisesRegex(ValueError, 'Missing'):
            results.verify_attempts(self.attempts(report(names[:-1]) .replace('</testsuite>', '')
                                                  + report(names[-1:], self.CRASH)[len('<testsuite>'):]))


class RunnerTests(unittest.TestCase):
    def run_fixture(self, mode, qemu='1', attempt='1', seed=None, xml=None):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name, text in (seed or {}).items():
                target = root/'build/ui-regression/attempts'/name/'TEST-device.xml'
                target.parent.mkdir(parents=True); target.write_text(text)
            tools = root/'tools'; tools.mkdir()
            for name in ('run_rendering_regression.sh', 'verify_rendering_results.py'):
                shutil.copy2(TOOLS/name, tools/name)
            binaries = root/'bin'; binaries.mkdir()
            adb = binaries/'adb'
            adb.write_text('''#!/usr/bin/env python3
import os,sys
from pathlib import Path
with open(os.environ['ADB_TRACE'],'a') as f:f.write(' '.join(sys.argv[1:])+'\\n')
if sys.argv[1:]==['shell','getprop','ro.kernel.qemu']:print(os.environ['FIXTURE_QEMU'])
''')
            gradle = binaries/'gradle'
            gradle.write_text('''#!/usr/bin/env python3
import os,sys,time
from pathlib import Path
mode=os.environ['FIXTURE_MODE']
Path(os.environ['GRADLE_TRACE']).write_text(' '.join(sys.argv[1:]))
if mode=='timeout':time.sleep(60)
if mode=='failure':sys.exit(7)
if mode=='success':
 p=Path(os.environ['FIXTURE_ROOT'])/'app/build/outputs/androidTest-results/connected/TEST-device.xml'
 p.parent.mkdir(parents=True,exist_ok=True);p.write_text(os.environ['FIXTURE_XML'])
''')
            adb.chmod(0o755); gradle.chmod(0o755)
            env = dict(os.environ, PATH=str(binaries)+os.pathsep+os.environ['PATH'],
                       THINN_UI_TIMEOUT_SECONDS='1', FIXTURE_MODE=mode, FIXTURE_QEMU=qemu,
                       FIXTURE_ROOT=str(root), FIXTURE_XML=xml or report(results.EXPECTED), THINN_UI_ATTEMPT=attempt,
                       ADB_TRACE=str(root/'adb.trace'), GRADLE_TRACE=str(root/'gradle.trace'))
            process = subprocess.run(['bash', str(tools/'run_rendering_regression.sh')],
                                     env=env, capture_output=True, text=True, timeout=10)
            evidence = root/'build/ui-regression'
            self.gradle_args = (root/'gradle.trace').read_text() if (root/'gradle.trace').exists() else ''
            adb_trace = (root/'adb.trace').read_text() if (root/'adb.trace').exists() else ''
            return (process, adb_trace, (root/'gradle.trace').exists(),
                    {p.name:p.read_bytes() for p in evidence.rglob('*') if p.is_file()})

    def test_device_failure_is_preserved_and_collects_diagnostics(self):
        process, trace, ran, evidence = self.run_fixture('failure')
        self.assertTrue(ran)
        self.assertEqual(process.returncode, 7, process.stderr)
        self.assertEqual(evidence['exit-code.txt'], b'7\n')
        self.assertIn('logcat -d', trace)
        self.assertIn('dumpsys activity top', trace)
        self.assertIn('screens', ' '.join(evidence) + trace)

    def test_host_watchdog_returns_failure_and_evidence(self):
        process, trace, ran, evidence = self.run_fixture('timeout')
        self.assertTrue(ran)
        self.assertEqual(process.returncode, 124, process.stderr)
        self.assertEqual(evidence['exit-code.txt'], b'124\n')
        self.assertIn('kill -3', trace)
        self.assertIn('logcat -d', trace)

    def test_empty_success_cannot_open_the_candidate_gate(self):
        process, _, _, _ = self.run_fixture('empty')
        self.assertNotEqual(process.returncode, 0)
        self.assertIn('Missing completed', process.stderr)

    def test_completed_success_keeps_evidence_and_passes(self):
        process, _, _, evidence = self.run_fixture('success')
        self.assertEqual(process.returncode, 0, process.stderr)
        self.assertEqual(evidence['exit-code.txt'], b'0\n')
        self.assertIn(f'{len(results.EXPECTED)}/{len(results.EXPECTED)} passed', process.stdout)

    def test_physical_device_is_refused_before_test_or_log_clear(self):
        process, trace, ran, _ = self.run_fixture('success', qemu='0')
        self.assertEqual(process.returncode, 2)
        self.assertFalse(ran)
        self.assertNotIn('logcat -c', trace)

    def test_second_attempt_reruns_only_the_tests_the_crashed_emulator_lost(self):
        names = sorted(results.EXPECTED)
        lost = names[:6].copy()
        first = report(names[6:]).replace('</testsuite>', '') + report(lost[:1], AttemptTests.CRASH)[len('<testsuite>'):]
        process, _, ran, _ = self.run_fixture('success', attempt='2', seed={'1': first}, xml=report(lost))
        self.assertTrue(ran)
        self.assertEqual(process.returncode, 0, process.stderr)
        selected = re.search(r'class=(\S+)', self.gradle_args).group(1).split(',')
        self.assertEqual(selected, [f'{results.CLASS}#{n}' for n in lost])
        self.assertIn(f'{len(results.EXPECTED)}/{len(results.EXPECTED)} passed', process.stdout)

    def test_retry_refuses_to_rerun_a_real_assertion_failure(self):
        names = sorted(results.EXPECTED)
        first = report(names[:1], AttemptTests.REAL)
        process, _, ran, _ = self.run_fixture('success', attempt='2', seed={'1': first})
        self.assertNotEqual(process.returncode, 0)
        self.assertFalse(ran)
        self.assertIn('did not pass', process.stderr)

    def test_invalid_attempt_number_is_refused(self):
        process, _, ran, _ = self.run_fixture('success', attempt='4')
        self.assertEqual(process.returncode, 2)
        self.assertFalse(ran)
