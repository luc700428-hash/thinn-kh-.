# thinn-khì V10.8 – OTA Update Center & Unified Haptics

* Dedicated full-screen, animated glass OTA page. Respects shared glass opacity, edge light and animation-speed preferences. Shows BuildConfig version and the current release highlights. Queries latest GitHub Release and renders release notes on new updates. Download progress and SHA256/installed signing checks remain.
* One user-facing haptic strength slider from 0%–100%, default 100% for new installs. The old 58% default is migrated once; custom levels are retained. Rain/Thunder haptics stay enabled, respect silent mode and platform capabilities.
* Removed separate rain and thunder trial buttons. The 10-second demo publishes a local simulated weather event via WeatherDemoDispatcher, presents a live countdown, and orchestrates weather audio, haptics, lightning and rain scene. Can stop early; cannot emit actual emergency notifications.
* OTA requires a persistent APK signing key; as of the previous GitHub screenshot APK_KEYSTORE_BASE64 was missing. Configure GitHub repository Secrets before building signed OTA APKs.
* GitHub API latest Release is read from the existing owner/repo configuration in AppUpdater.kt. If the repo changes, update the endpoint.
* Full Android compile / live vendor QA not performed in this container. Run GitHub Actions and return error logs if compilation fails.

* Android 12+ system splash uses transparent `brand_symbol.png` instead of the solid launcher tile. First-run and home branding reuse that transparent mark, with a subtle halo on onboarding; the launcher icon remains unchanged.
* This is an in-app demo event bus, not an Android system-wide synthetic weather broadcast or a real CWA alert.
