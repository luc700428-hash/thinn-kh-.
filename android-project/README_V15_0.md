# thinn-khì V15.0｜地震資訊中心與版本紀錄修正

Android/Kotlin Compose source upgrade from V11.3; base app ID io.atmos.weather unchanged.

**Real features**: polished earthquake report center, model for CWA significant/small-area reports, user-entered CWA key encrypted with Android Keystore, secure HTTPS official data requests, chronological official reports, post-event notification selection and threshold, animated seismic rings, safety info, version-correct release notes embedded during build, existing 8 notification tones retained.

**Not a certified earthquake early warning**. CWA earthquake reports are issued after shaking. WorkManager 15 minute scheduling can be delayed and is unsuitable for safety-critical EEW. A genuine authenticated low-latency official EEW provider, user approval, reliable backend delivery, independent timing/security field tests and fallback procedures are required before enabling that feature.

TREM-Lite reference: https://github.com/ExpTechTW/TREM-Lite. AGPL-3.0 source was NOT copied into this application. A direct source-code integration would require license compliance and separate review. CWA data keys are user-provided and NOT built into APK.

## Usage
1. Open the home header earthquake-waves button or Settings -> 地震資訊中心.
2. Get a free CWA Open Data authorization code from the official site. Paste into the in-app encrypted form.
3. Select whether you want post-earthquake reports and a magnitude threshold. Native report notifications use chosen sound; these are NOT seconds-before-shaking warnings.
4. The page explains data failures and source status explicitly.

## Version notes correctness
The GitHub workflow writes release_notes to app/src/main/assets/current_release_notes.md before :app:assembleRelease, and publishes exactly the same notes. Every installed version displays its own bundled release notes in the update center rather than old hardcoded text.

GitHub Actions: Upload thinn-khi_Android_Source_V15_0.zip to repository root; replace .github/workflows/build-apk.yml with thinn-khi-V15_0-build-apk.yml. First run publish_release=false, then publish_release=true only after a successful signed APK artifact. Existing fixed signing key MUST be used for OTA.
