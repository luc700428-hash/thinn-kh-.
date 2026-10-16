# thinn-khì V11.0 — Android source release candidate

## Important: this is source, not a prebuilt APK

Upload **`thinn-khi_Android_Source_V11_0.zip`** to the GitHub repository root, then replace `.github/workflows/build-apk.yml` with the supplied `thinn-khi-V11_0-build-apk.yml` file. The workflow uses the four existing signing Secrets and produces the signed APK when it compiles successfully.

Run a **test build first**: Actions > `Build thinn-khi V11.0 APK and OTA` > `Run workflow`, with `publish_release=false`. After `SIGNED-APK` succeeds, run again with `publish_release=true` to publish a new release. Do not publish on build failure. The App Update center will only offer versions newer than the installed build.

## Implemented in source

- Android weather classification expanded: fog, model-estimated haze, raised dust and *possible* severe dust, snow/sleet/freezing rain/hail, thunderstorm and existing clear/cloud/rain.
- Additional Open-Meteo weather query variables: snowfall probability/amount, ice probability, gusts and snow depth; extra dust from the air-quality model. The weather forecast falls back to established parameters on HTTP 400.
- Snow-aware, impact-oriented lifestyle suggestions. Display a snowfall percentage only when the forecast actually provides the independent field. Sand/dust classifications are model heuristics, **not government dust-storm alerts**.
- Animated, lightweight rain beads, snow buildup and sand buildup at the top of the *simulated* glass surface, with animated gradients and time-based particle continuity. Existing audio tracks cross-fade; sound effects are reused. This is **2D stylized simulation**, not a 3D fluid physics engine.
- System-style update center labels; Android WorkManager checks releases approximately every 12 hours and posts a version notice once per release where notifications are allowed. Xiaomi focus extensions are attempted only on permitted devices. All other Android devices receive native notifications.
- Reworked blue weather icon with cloud and sun, supplied in Android icon resolutions.
- New Taipei's featured regional display uses Danjiang Bridge-inspired 2.5D geometry. Existing county skyline families, regional identity-based variations, and widgets are retained.

## Not complete, do not describe as delivered

- This is **not** 368 custom-built physical 3D landmarks, nor a 22-county verified GLB collection. Existing parametric regional art is used where custom models are missing.
- No new original studio-quality recordings for sand/ice: existing environmental sounds reused.
- No guaranteed 60/120 FPS, hardware 3D fluid solver or 3D sand deposition. A device performance pass is still required.
- Xiaomi/other manufacturers own the decision to show Focus/Super Island notifications; app cannot force focus notifications on all smartphones.
- No full Android SDK Gradle compile or Xiaomi physical device QA was performed in this environment; use GitHub Actions and return `*-errors` logs if it fails.

## OTA signing

**Do not change your permanent signing keystore.** Keep the four existing `APK_KEYSTORE_BASE64`, `APK_KEYSTORE_PASSWORD`, `APK_KEY_PASSWORD`, and `APK_KEY_ALIAS` GitHub Actions secrets. This version increases `versionCode` to 11,000,000 plus GitHub run number; the version name will be `11.0.<run>`. Android still requires the same certificate for an in-place update.
