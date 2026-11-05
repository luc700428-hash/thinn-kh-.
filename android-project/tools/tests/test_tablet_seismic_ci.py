"""Evidence acceptance behavior; does not pretend to execute Android rendering."""
from pathlib import Path
import importlib.util
import tempfile
import unittest

TOOLS=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('tablet_seismic_results',TOOLS/'verify_tablet_seismic_results.py')
results=importlib.util.module_from_spec(spec)
spec.loader.exec_module(results)


class TabletSeismicEvidenceTests(unittest.TestCase):
    def report(self,omit=None,extra=''):
        cases=''.join(f'<testcase classname="{name}" name="{method}">{extra}</testcase>'
                      for name,methods in results.EXPECTED.items() if name != omit for method in methods)
        return '<testsuite>'+cases+'</testsuite>'

    def check(self,text):
        with tempfile.TemporaryDirectory() as d:
            (Path(d)/'TEST-fixture.xml').write_text(text)
            return results.verify(d)

    def test_complete_results_are_accepted(self):
        self.assertEqual(sum(len(m) for m in results.EXPECTED.values()),self.check(self.report()))

    def test_missing_class_cannot_be_reported_as_passing(self):
        for name in results.EXPECTED:
            with self.assertRaisesRegex(ValueError,'Missing completed'):
                self.check(self.report(omit=name))

    def test_failures_errors_and_skips_close_the_gate(self):
        for status in ('failure','error','skipped'):
            with self.assertRaisesRegex(ValueError,'did not pass'):
                self.check(self.report(extra=f'<{status}/>'))

    def test_unrelated_failures_also_close_the_gate(self):
        text=self.report().replace('</testsuite>','<testcase classname="Other" name="other"><failure/></testcase></testsuite>')
        with self.assertRaisesRegex(ValueError,'did not pass'):
            self.check(text)

    def test_duplicate_results_are_rejected(self):
        name=next(iter(results.EXPECTED));method=next(iter(results.EXPECTED[name]))
        text=self.report().replace('</testsuite>',f'<testcase classname="{name}" name="{method}"/></testsuite>')
        with self.assertRaisesRegex(ValueError,'duplicate'):
            self.check(text)

    def test_empty_reports_are_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            with self.assertRaisesRegex(ValueError,'Missing completed'):
                results.verify(d)
