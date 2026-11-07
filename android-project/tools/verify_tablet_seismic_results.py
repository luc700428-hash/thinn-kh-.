#!/usr/bin/env python3
"""Require completed passing tablet/EEW tests, preserving failures in the whole suite."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

EXPECTED = {'io.atmos.weather.ui.TransparentDockTransmissionRenderingTest': {'liveBackgroundRemainsVisibleThroughEveryDockEdgeInBothThemes'},
 'io.atmos.weather.ui.RealWeatherDockContinuityRenderingTest': {'realWeatherHasNoBackgroundSeamAlongAnyReservedDockLane'},
 'io.atmos.weather.earthquake.SeismicCriticalAudioPolicyTest': {'alarmsOnlyModePermitsCriticalAlarmAudio',
                                                                'criticalModeCannotRaiseAZeroAlarmVolume',
                                                                'criticalModeUsesAlarmsWhenRingerIsSilent',
                                                                'ordinaryModeRespectsSilentAndDnd',
                                                                'ordinaryModeUsesNotificationAudioOnly',
                                                                'priorityDndNeedsBothPolicyGrantAndActualChannelException',
                                                                'totalSilenceAndUnknownModesRemainSystemVetoes'},
 'io.atmos.weather.earthquake.SeismicCriticalDeliveryTest': {'aPrivateExpiringDrillNeverBecomesALiveIncident',
                                                             'aRealAcceptedWarningPreemptsThePendingDrill',
                                                             'cancellingBeforeTheDelayPreventsNotificationDelivery',
                                                             'enablingModePreservesAnExistingNotificationBlock',
                                                             'enablingModePreservesAnExistingSilentChannel',
                                                             'forgedTestExtrasCannotWakeTheDevice',
                                                             'modeIsExplicitAndOnlyChangesEewAudioChannels'},
 'io.atmos.weather.earthquake.SeismicPaneGeometryTest': {'aSplitWindowUsesItsUsableWidthRatherThanTheDeviceName',
                                                         'invalidWindowMeasurementsCannotProduceInvalidLayoutConstraints',
                                                         'largerTextStacksBeforeSqueezingCriticalInformation',
                                                         'shortLandscapeAndTabletopPanesKeepTheMapReachable',
                                                         'wideTabletsReserveReadableMapAndInspectorPanes'},
 'io.atmos.weather.ui.FloatingDockContentTest': {'fourDestinationsFormACompactCapsule',
                                                 'invalidScaleAndEmptyActionListsHaveFiniteSafeSizes',
                                                 'largerReadingSizesGrowCellsWithoutStretchingToAWindow'},
 'io.atmos.weather.ui.SeismicTabletDashboardRenderingTest': {'mapKeepsItsLayerAndZoomAcrossTabletAndSplitWindows'},
 'io.atmos.weather.ui.SeismicTabletSettingsRenderingTest': {'panelsReflowWithoutLosingTheSelectedTab'},
 'io.atmos.weather.ui.SeismicTabletWarningRenderingTest': {'criticalValuesAndLargerMapReflowTogether'},
 'io.atmos.weather.ui.TabletDockGeometryTest': {'aNarrowOrShortPaneFallsBackWithoutChangingTheUsersPreference',
                                                'everyEdgeReservesASeparateLaneAtTabletAndFoldedPaneSizes',
                                                'floatingCapsuleFitsFourCellsInsteadOfGrowingWithTheWindow',
                                                'phoneAndNarrowOuterScreensKeepTheirExistingNavigationMode',
                                                'resizingAndHingeChangesCannotProduceNegativePadding'},
 'io.atmos.weather.ui.TabletDockPositionsRenderingTest': {'allFourEdgesStayReadableInBothThemesAndRetainLiveFormState'}}


def verify(root):
    seen = set()
    total = 0
    reports = sorted(Path(root).rglob('TEST-*.xml'))
    if not reports:
        raise ValueError('Missing completed JVM test reports')
    for report in reports:
        tree = ET.parse(report)
        for case in tree.iter('testcase'):
            key = (case.get('classname'), case.get('name'))
            if any(case.find(kind) is not None for kind in ('failure', 'error', 'skipped')):
                raise ValueError(f'Test did not pass: {key}')
            total += 1
            if key[0] in EXPECTED:
                if key in seen or key[1] not in EXPECTED[key[0]]:
                    raise ValueError(f'Unexpected or duplicate result: {key}')
                seen.add(key)
    required = {(name, method) for name, methods in EXPECTED.items() for method in methods}
    if not required or seen != required:
        raise ValueError('Missing completed tablet/seismic results: '+str(sorted(required-seen)))
    return total


if __name__ == '__main__':
    try:
        print(f'JVM/native regression: {verify(sys.argv[1])} passed; required tablet/seismic classes complete, none skipped.')
    except (ValueError, OSError, ET.ParseError) as error:
        raise SystemExit(f'Tablet/seismic evidence rejected: {error}')
