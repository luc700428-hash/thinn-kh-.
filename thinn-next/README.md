# thinn-khì NEXT v0.2 · iOS-inspired native Android laboratory app

This is an independent Android app preview, not the production V25 or an OTA. It uses Kotlin Compose with original rendering (no Material visual components), isolated Android package `io.atmos.weather.nextioslab`, and no access to the old app's private data.

**Pages implemented:** Weather (Open-Meteo live/current/hourly/10-day plus source-aware cache), Clock/Alarms (local schedule and permissions with repeat/snooze UI), Settings (iPhone-like grouped sections and controls), earthquake/typhoon marked explicitly as pending integration.

**No Apple code, proprietary icons, fonts, or private API is embedded.** Real device screenshots and physical alarm testing are required to assess visual equivalence and reliability; passing Gradle does not establish these. See `QA_iOS_對照驗收.md`.

**Build:** `gradle --project-dir thinn-next :app:testDebugUnitTest :app:assembleDebug` from the repository root on CI with Android SDK 36, JDK 17 and Gradle 8.13. Run `python3 -m unittest discover -s thinn-next/tools -p 'test_*.py'`. For Termux, use the ZIP's `termux_build_ios_pages.sh` to safely stage the source on a new GitHub test branch.
