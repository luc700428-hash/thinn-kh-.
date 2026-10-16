# thinn-khì V10.1 | 地點管理・逐時天氣・中央氣象署特報

**This is Android source, not an installable APK.** The entire Android project must be compiled and tested before a device release.

## Changes included

1. Renamed label to **中央氣象署特報**. The CWA RSS feed is used to list recent county-matching announcement items, with red warning cards and explicit fetch errors. An empty RSS match is **not proof** that the official authority has no live warnings.
2. 24-hour scrubber has full left-to-right selection and shows WMO hourly description (sun/moon, drizzle, small/moderate/heavy rain, snow, fog and thunderstorms), temperature, rainfall chance and wind. Vertical page scrolling is preserved.
3. GPS reverse geocoding attempts **台灣 · 縣市 · 鄉鎮市區**; diorama caption reflects available locality, while assets remain **county-level** and not individual township models.
4. Air-quality & visibility appear below sunrise and pressure dials; pressure/sunrise share dial visual treatment.
5. Rules-based advice includes clothing, umbrella, humidity, wind, air-quality, UV, visibility, temperature changes and the local part of day. **Not a generative AI service.**
6. Flat original umbrella-line logo, not a copy of Xiaomi, Apple or Samsung icons.
7. Locations: manual search submitted via Search button or keyboard IME, not on each keystroke; on-device saved list of up to 30 cities. When location permission exists, the app requests GPS on startup and prioritizes the GPS location. Previewing a saved city does not replace stored GPS for local warning notifications.

## Things NOT yet guaranteed

* **Vendor dynamic islands**: Xiaomi HyperOS Super Island/focus notices and OPPO Atomic Island require verified, sometimes explicitly approved vendor integration. The source provides ordinary grouped Android notifications but does not pretend those platforms' island APIs are enabled.
* **Live warnings**: CWA RSS announcements can be delayed or have incomplete geographic/effectivity data. Fully authoritative effective-warning synchronization, earthquake/tsunami channels and immediate server push still need authorized official structured APIs and backend services. Android WorkManager polling is best effort and cannot be guaranteed instantaneous.
* **Models**: there are only 22 county-level diorama assets. Location captions can display towns; there is not yet one hand-made 3D asset for every town.
* **Search**: Open-Meteo geocoding coverage varies; smaller villages might not appear. Search requires a network connection.
* **Build**: Kotlin-only domain model and XML have been checked; **full Android SDK Gradle compilation has not been run** here.

## Build from GitHub

1. Upload `thinn-khi_Android_Source_V10_1.zip` unmodified to repository root.
2. Replace `.github/workflows/build-apk.yml` with `thinn-khi-V10_1-build-apk.yml` content.
3. Commit and open Actions. On success download artifact `thinn-khi-V10_1-APK` and unzip to obtain debug APK.
4. On failure download `thinn-khi-V10_1-errors` and inspect `compiler-errors.txt`.
