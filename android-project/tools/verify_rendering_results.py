#!/usr/bin/env python3
"""Require real passing device results for all three rendering regressions."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

CLASS = 'io.atmos.weather.ui.RenderingRegressionTest'
EXPECTED = {
    'glassSamplingCannotPaintOverItsForegroundSiblings',
    'deferredChildRecordingCannotSampleItsOwnAncestor',
    'weatherScrollAndRepeatedSettingsNavigationKeepVisibleText',
}


def verify(root):
    seen = set()
    for report in sorted(Path(root).rglob('TEST-*.xml')):
        tree = ET.parse(report)
        for case in tree.iter('testcase'):
            if case.get('classname') != CLASS:
                continue
            name = case.get('name')
            if name in seen or name not in EXPECTED:
                raise ValueError(f'Unexpected or duplicate rendering result: {name}')
            if any(case.find(kind) is not None for kind in ('failure', 'error', 'skipped')):
                raise ValueError(f'Rendering test did not pass: {name}')
            seen.add(name)
    if seen != EXPECTED:
        raise ValueError('Missing completed rendering tests: ' + ', '.join(sorted(EXPECTED - seen)))
    return len(seen)


if __name__ == '__main__':
    try:
        print(f'Android rendering regression: {verify(sys.argv[1])}/3 passed, none skipped.')
    except (ValueError, OSError, ET.ParseError) as error:
        raise SystemExit(f'Android UI evidence rejected: {error}')
