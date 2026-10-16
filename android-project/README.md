**V10.6 OTA and synchronized forecast:** see docs/V10_6.md.

**V10 update:** see V10_CHANGES.md. This is uncompiled source; build with GitHub Actions.

# thinn-khì V9 — Seamless Atmosphere Test Build

**Please read `README_RAIN_V9.md` and `docs/WEATHER_AUDIO_SOURCES.md` first.**

This release is an update to V8 focused on rain and storm animation / authentic rain recordings and snow particle dynamics. It retains the rest of the V8 functionality and UI.

The three rain/thunder `.ogg` resources are intentionally **not prebundled**. They are downloaded and converted by `tools/prepare_real_weather_audio.py` during GitHub Actions build. The build fails visibly if real recordings cannot be obtained. No fake synthetic rain is inserted as fallback.

Upload `thinn-khi_Android_Source_V9.zip` to the root of your repository, then replace `.github/workflows/build-apk.yml` with the companion `thinn-khi-V9-build-apk.yml`, commit and open Actions.

Not validated as a finished Android APK or on a Xiaomi phone. Correct audio/visual synchronization and quality are hardware-dependent and require real-device QA.
# thinn-khì V7 — Interactive Forecast & Air Quality (source preview)

## What's implemented against V6
- **24-hour drag-to-scrub forecast:** horizontally scrolling smooth temperature curve; selected-hour readout, weather icon, wind and precipitation probability follow scrolling/tapping. Up/down gestures outside horizontal drags retain normal page scrolling.
- **Weather dials:** radial UV, relative humidity, feels-like temperature, wind speed and wind direction. Temperatures and weather metrics use tabular OpenType numerals.
- **Air quality:** Open-Meteo Air Quality API current *U.S. AQI* + PM2.5, PM10, aerosol optical depth; weather API visibility. These are model data, **not** official Taiwan Ministry of Environment sensor measurements or Taiwan AQI standard. Missing air-quality requests fail independently of the weather forecast.
- **Fog/haze:** WMO fog code and visibility; separately estimated haze atmosphere when aerosol optical depth is high (do not confuse this with fog or an official warning).
- **Forecast-based smart advice:** verifiable, offline deterministic recommendations on layers/outerwear, rain umbrella in the coming six hours, UV, fog visibility and AQI. This is a rule-based engine, **not a connected LLM**; no unsupported AI claims or invented weather.
- **Multi-notice CWA UI:** separate cards for up to five location-matched weather RSS items, with direct links to original notices; notification group summary using Android InboxStyle when two or more genuinely new notices are found. Old/cancellation notices not re-pushed. This is NOT a guaranteed, instant, all-hazard warning system.
- **Continuous foreground weather audio:** 1.08-second crossfade between provided synthetic ambient loops when weather conditions change; stop or pause outside the app and respect ringer mode.
- **Remove 3D exploration screen:** home diorama remains decorative and widget-shared. It does not intercept vertical gestures. Dedicated activity unregistered and removed.
- **Background animation:** smooth cloud/fog/haze layers, rain and snow particles. Does not recompose entire UI every sky frame.

## Important limits
1. CWA weather RSS is **weather notices only**, not a complete location-filtered earthquake/tsunami alert service. The National Science and Technology Center for Disaster Reduction (NCDR) multi-hazard alert API requires approved API key/integration. No earthquake/tsunami alerts are fabricated or advertised as supported. For critical evacuation decisions, use official cell broadcasts and government alerts.
2. Background WorkManager polling interval minimum 15 min; the OS may delay it further. Not instant push. Reliable, low latency coverage requires an authorized multi-hazard server-side ingest pipeline, push service, test plan and uptime monitoring.
3. Android widgets remain snapshot-based and limited by OEM launcher controls; they reuse the last selected location/landmark from the app.
4. Weather audio is synthesized sound and sounds different on