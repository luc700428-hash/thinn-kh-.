#!/usr/bin/env python3
"""Require real passing device results for every rendering regression."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

CLASS = 'io.atmos.weather.ui.RenderingRegressionTest'
EXPECTED = {
    'themeColorChoicesUpdateVisibleMaterialsAndPersist',
    'themeMotionHasIntermediateFramesAndHonorsReducedMotion',
    'nativeTemperatureOutlinesReceiveRainAndKeepTheForegroundVisible',
    'rapidAlternatingSliderDragsSurviveRecomposition',
    'textSizeAndWeightApplyOnceAcrossNestedThemes',
    'preferenceChoicesExposeOneSelectionInBothAppearances',
    'bubbleSliderIsTransparentAndKeepsDragAndAccessibilityValues',
    'glassSamplingCannotPaintOverItsForegroundSiblings',
    'dockKeepsLiveTransparencyCapsuleSelectionAndScrubbing',
    'deferredChildRecordingCannotSampleItsOwnAncestor',
    'weatherScrollAndRepeatedSettingsNavigationKeepVisibleText',
    'allToolsGroupsAndFilteredLastGroupRenderInBothAppearances',
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


def _crashed(case):
    """A device that vanished mid-test leaves an empty <failure/>: no message, no stack trace."""
    failure = case.find('failure')
    return (failure is not None and case.find('error') is None and case.find('skipped') is None
            and not (failure.get('message') or '').strip() and not (failure.text or '').strip())


def merge(roots):
    """Combine the attempts of one CI run. Returns (passed, crashed) method names.

    Only a test whose emulator disappeared may be retried; a real assertion failure, an error or
    a skip in any attempt still rejects the run, so a retry can never hide a UI regression.
    """
    passed, crashed = set(), set()
    for root in roots:
        seen = set()
        for report in sorted(Path(root).rglob('TEST-*.xml')):
            for case in ET.parse(report).iter('testcase'):
                if case.get('classname') != CLASS:
                    continue
                name = case.get('name')
                if name not in EXPECTED or name in seen:
                    raise ValueError(f'Unexpected or duplicate rendering result: {name}')
                seen.add(name)
                if _crashed(case):
                    crashed.add(name)
                elif any(case.find(kind) is not None for kind in ('failure', 'error', 'skipped')):
                    raise ValueError(f'Rendering test did not pass: {name}')
                else:
                    passed.add(name)
    return passed, crashed - passed


def remaining(roots):
    """Methods a fresh emulator still has to run, as runner arguments (Class#method,...)."""
    passed, _ = merge(roots)
    return ','.join(f'{CLASS}#{name}' for name in sorted(EXPECTED - passed))


def verify_attempts(roots):
    passed, _ = merge(roots)
    if passed != EXPECTED:
        raise ValueError('Missing completed rendering tests: ' + ', '.join(sorted(EXPECTED - passed)))
    return len(passed)


if __name__ == '__main__':
    try:
        if sys.argv[1] == '--remaining':
            print(remaining(sys.argv[2:]))
        else:
            total = verify_attempts(sys.argv[1:])
            print(f'Android rendering regression: {total}/{len(EXPECTED)} passed, none skipped.')
    except (ValueError, OSError, ET.ParseError) as error:
        raise SystemExit(f'Android UI evidence rejected: {error}')
