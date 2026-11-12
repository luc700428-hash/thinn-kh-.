#!/usr/bin/env python3
"""Require completed passing tablet/EEW tests, preserving failures in the whole suite."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

EXPECTED = {'io.atmos.weather.ui.WeatherSurfacePhysicsTest': {'scrollingEitherWayEjectsAccumulationWithTheCardsMomentum', 'aFastScrollAlsoDetachesWaterIntoMovingDroplets', 'roundedCornersAndFastSideHitsUseContinuousCollision', 'theSameSnowSimulationAgreesAtSixtyAndOneTwentyHz', 'snowAccumulatesOnTheActualRoofAndShedsWhenThePileIsHeavy', 'anAttachedLeafLastsMinutesThenBlowsAwayWithoutResticking', 'strikesSelectExistingCardCornersAndFadeWithoutPersistentDamage', 'rainAndSnowLevelsHaveDistinctPhysicalProfiles', 'dryStrongWindEmitsLeavesWithoutInventingRainOrSnow'}, 'io.atmos.weather.ui.ThemeMotionTest': {'textStaysReadableDuringColorAndAppearanceChanges', 'closingAnOverlayKeepsTheOriginalThemeHostAlive', 'allPastelsHaveReadableSurfaceAndWidgetRoles', 'weatherDayNightIsIndependentOfFormAppearanceAndHasReadableInk', 'changingColorsHasIntermediateFramesAndExactEndpoints'}, 'io.atmos.weather.ui.PhoneAppearanceRenderingTest': {'realPhoneShowsSquarePastelsAndReadableWeatherAboveItsDock'}, 'io.atmos.weather.ui.WeatherPhysicsRenderingTest': {'glyphsCardsSnowLeavesAndLightningRenderFromTheirActualColliders'},
 'io.atmos.weather.ui.AppThemeColorTest': {'missingOrUnknownPreferencesDefaultToForest','textRolesRemainReadableInEveryThemeAndAppearance','tintKeepsGlassAlphaAndMaterialLuminance','warningRolesDoNotChangeWithTheTheme','allFilledSelectionsKeepWhiteTextReadable'},
 'io.atmos.weather.ui.ThemeColorPickerRenderingTest': {'everyColorAppliesAndPersistsWithoutResettingFormState'},
 'io.atmos.weather.ui.ThemeWorkspaceRenderingTest': {'weatherToolsSettingsAndQuakesFollowTheSelectedTheme'},
 'io.atmos.weather.ui.TransparentDockTransmissionRenderingTest': {'liveBackgroundRemainsVisibleThroughEveryDockEdgeInBothThemes'},
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


# Keep the fluid-control, theme preview and wind/lightning regressions mandatory.
EXPECTED.update({'io.atmos.weather.ui.LightningLeafMotionTest': {'lightningBranchesAreDeterministicAndEndOnTheActualImpact', 'coherentNoiseIsSmoothBoundedAndDifferentForEachLeaf', 'leafPhysicsAgreesAtSixtyAndOneHundredSixtySixHz', 'attachedLeavesFlutterIndependentlyAroundTheirContact', 'flashProfilesHaveIrregularIntervalsAndAtMostThreeReturnStrokes', 'navigationForegroundsRemainReadableInEveryThemeAndAppearance'}, 'io.atmos.weather.ui.ThemePreviewCancellationRenderingTest': {'returningCancelsThePreviewWithoutChangingSavedThemeOrWidget'}, 'io.atmos.weather.ui.FluidGlassContrastRenderingTest': {'selectedLensCoversItsCellAndDarkIconsRemainVisibleOverOppositeScenes'}, 'io.atmos.weather.ui.RefreshRatePolicyTest': {'accepts119Point88AndFallsBackOn90HzHardware', 'prefers144WhenHardwareSupportsIt', 'ignoresCorruptModesAndKeepsResolution', 'selectsExact166AndNeverChangesTheCurrentResolution', 'accepts165Point9AndDoesNotPreferAFasterModeUnnecessarily', 'doesNotChangeScreenResolutionToGetAHighRate'}, 'io.atmos.weather.next.DockLiquidGeometryTest': {'densityScalesMaterialAndNeckTogether', 'touchLiftsAndPullingDeformsTheMaterialWithoutMovingTheHitCell', 'overshootAndFastJumpsStayInsideTheDockAndNeverHighlightEveryTab', 'invalidMeasurementsAreNotPainted', 'restingLensesMatchTheInnerDockHeightAcrossDensitiesAndAxes'}})

EXPECTED['io.atmos.weather.ui.OverflowDockRenderingTest']={'overflowUsesTheFourthSlotAndNamesItsSelectedDestination'}

# Keep resize growth and seismic material isolation mandatory.
EXPECTED.update({'io.atmos.weather.ui.SeismicSelectorContrastRenderingTest': {'mapLayersKeepTheirOwnReadableMaterialOverAnUnrelatedWeatherBackdrop'}, 'io.atmos.weather.widgets.WidgetResizeRenderingTest': {'enlargedWidgetsGrowTheirTypeAndTheSquareBuddyUsesBothColumns'}, 'io.atmos.weather.widgets.WidgetLayoutPlanTest': {'missingAndInvalidMeasurementsStayFinite', 'resizingGrowsTheTypographyAsWellAsTheAvailableSpace', 'sizeChangesInformationDensityInsteadOfScalingOneLayout', 'largeTextIsFittedToAvailableSpaceAfterSystemScaling'}})

EXPECTED['io.atmos.weather.widgets.WidgetResizeRenderingTest'].add('photoSizedWidgetsKeepTemperatureTimeAndDateVisibleAfterResize')
EXPECTED['io.atmos.weather.presentation.home.AppearanceSaveControllerTest']={
    'continuousDragPreviewsDoNotWriteAtDisplayFrameRate',
    'backgroundOrDisposalKeepsTheFinalValueAndRefreshesPlacedWidgetsOnce',
    'resumingDragCancelsTheOutdatedLauncherRefresh',
}

# Phone defaults, automatic foreground alerts and independent launcher typography.
EXPECTED.update({
 'io.atmos.weather.ui.TabletSeismicWarningRenderingTest': {'emergencySummaryAndMapInspectorRemainReachableAndDismissibleAtAnyWidth'},
 'io.atmos.weather.ui.PhoneDockProportionRenderingTest': {'actualPhoneModeKeepsLensAndAllFourLabelsInsideOneCenteredCapsule'},
 'io.atmos.weather.ui.SeismicSingleScreenRenderingTest': {'drillAndLiveEssentialsFitShortPhonesLargeTextAndLandscapeWithoutScrolling'},
 'io.atmos.weather.ui.SeismicReadinessRenderingTest': {'permissionStatusesAndTestActionUseDistinctFullWidthRowsAtLargeReadingSize'},
 'io.atmos.weather.ui.WidgetTypographySettingsRenderingTest': {'launcherFontControlPreviewsPersistsAndLeavesAppReadingSizeAlone'},
 'io.atmos.weather.widgets.WidgetTypographyRenderingTest': {'independentPreferenceGrowsWeatherAndClockTextAndFitsEveryFamily'},
 'io.atmos.weather.earthquake.SeismicForegroundLaunchTest': {'acceptedLiveWarningLaunchesThePrivateScreenWithoutTappingANotification','explicitFiveSecondDrillOpensInTheVisibleAppWithoutPublishingALiveIncident','backgroundAndPausedWindowsLeavePresentationToTheSystemNotification','dismissedTicketAndPauseBetweenDeliveryAndLaunchCannotOpenAStaleWarning'},
})
EXPECTED['io.atmos.weather.widgets.WidgetLayoutPlanTest'].add('launcherPreferenceIsBoundedAndChangesReadingSizesIndependently')

EXPECTED.update({
 'io.atmos.weather.ui.TyphoonTimelineRenderingTest':{'actualTimelineKeepsItsSourceMapAndNeverBuildsPathsOnTheUiThreadWhileScrubbing'},
 'io.atmos.weather.widgets.WidgetExtremeReflowRenderingTest':{'allFamiliesRenderWideTowersAndLargeTypeWithoutInvertedPrimarySizes'},
 'io.atmos.weather.widgets.WidgetCountdownRenderingTest':{'clockFamiliesOnlyShowACountdownForAValidAlarmWithinTheNextDay'},
 'io.atmos.weather.widgets.AdaptiveWidgetLayoutTest':{'extremeHostShapesReflowAndPrimaryFontsGrowMonotonically'},
 'io.atmos.weather.earthquake.SeismicCountyRotationTest':{'allCountiesRotateAndHistoricalGradesAndMissingValuesRemainDistinct'},
 'io.atmos.weather.ui.PhoneWeatherDockTransparencyRenderingTest':{'forecastCoversDockAndGestureAreaWithActualSystemInsets'},
 'io.atmos.weather.ui.LegacyPhoneWeatherDockTransparencyRenderingTest':{'forecastCoversDockAndGestureAreaWithActualSystemInsets'},
 'io.atmos.weather.ui.WeatherScenePauseRenderingTest':{'coveredSceneStopsFramesAndResumesItsOriginalParticleWorld'},
})

for classname in (
    'io.atmos.weather.widgets.WidgetHostReapplyRenderingTest',
    'io.atmos.weather.widgets.LegacyWidgetHostReapplyRenderingTest',
    'io.atmos.weather.widgets.Android16WidgetHostReapplyRenderingTest',
):
    EXPECTED[classname]={
        'repeatedUpdatesReplaceTheExistingRegionsInsteadOfStackingThem',
        'resizingAndChangingEveryFamilyDropsOldTextAndKeepsTheTreeBounded',
        'anAlreadyDuplicatedR4TreeIsRepairedOnItsFirstUpdate',
        'detachedOldAnimationsCannotKeepAddingLauncherWork',
        'reducedMotionAndUnknownWeatherCreateNoHiddenAnimationTimers',
    }
EXPECTED['io.atmos.weather.widgets.WidgetLauncherUpdateRenderingTest']={
    'allTwentyNineWidgetsSurviveRealHostUpdatesAndExtremeResizesWithoutGhosts'
}

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
