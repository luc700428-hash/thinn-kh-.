# V25 practical rebuild — implementation and validation

Base: `8a7cbffe9ad3896425cf6462d745bcbaa8bb490a` (the radar, dark map and 144Hz update).
This is an incremental Android source update. It keeps the existing signing and release workflow, candidate channel, alarm library, native radar and 144Hz window policy. It does not mark the full V25 milestone or device acceptance complete.

## Navigation and presentation

The four root tabs stay in their pager. A Dock request closes parent detail flags, resets nested state in the departing and destination tab, cancels any earlier tab transition, and navigates to the last requested destination. Reselecting a tab returns to its root. Full-screen details retain their outgoing composition through the transition; predictive back can seek and cancel the detail layer. Settings details keep the root Dock accessible. Dialogs and native modal windows retain their own dismissal behavior.

Earthquake monitoring settings use the same `NextReorderableToolDock` for 一般／警報／音效／關於. The main header has its long search field and a right-aligned mark with thinn-khì below. Light-mode Dock text is darkened for contrast. New screens use the shared glass surfaces, text scaling, slider, springs and reduced-motion settings.

## Desktop widgets

The Q widget has separate strip, landscape, square and tall layouts. Text heights are budgeted before allocating character space; the county/district label and short high/low line are retained where space permits. More space adds a native clock, metrics and hourly forecast. All 28 widget families are rendered at small, wide, tall and expanded sizes and with large text. The new checks inspect both bounds and text baselines, and include a long real Taiwan place label and 3× display density.

The Q character is made from 584 shrink-safe native VectorDrawable resources. Its body, eye states, highlights, rain/snow and ground splashes render at final launcher size. Four seeded eye phrases are joined at neutral poses; rain and ground share frame cadence. Native float animation continues without replacing the face. A widget refresh changes the seed. Continuous random code cannot run inside a launcher RemoteViews host; the host runs the prepared phrases and may throttle or stop animation. Reduced motion, zero system animation scale and battery saving select the static vector.

Common widget layout budgets also reserve enough space for CJK text and 12-hour AM/PM clock glyphs. The tiny strip reduces its temperature size; forecasts omit secondary rain percentages when they do not fit; analog dials and digital clock blocks reserve their text heights.

## Geography and official storm probability

The bundled regional atlas keeps 104,907 source vertices in 3,178 polygons, including 1,062 minor-island polygons, between 90–180°E and 15°S–60°N. Natural Earth 1:10m land and minor islands are public-domain data. The existing NLSC Taiwan vector atlas remains on top. Both storm maps transform native paths rather than magnifying bitmaps. Nearby countries, seas and island labels share the camera and avoid overlapping labels. This is a regional cartographic dataset, not a claim to include every rock or a navigation chart.

The `.tkatlasz` resource contains gzip bytes with an internal TKREG001 format. It deliberately avoids the `.gz` suffix because Android asset merging expands `.gz` files and removes that suffix. Its SHA-256 and upstream source checksums are in the accompanying `west_pacific_10m.sources.json`.

CWA publishes a separate 120-hour city/reference-point table. The reader safely extracts only the official relative path and UTC publication stamp from TY_NEWS-Data.js, then reads WSP-AREA_<stamp>_WHOLE-DURATION.json. It never evaluates JavaScript. Only explicitly published integer 0–100 values count. A matching island (蘭嶼、綠島、恆春) takes precedence over its county. A named county is a reference-point product, not an interpolated value at the exact GPS coordinate. A missing value stays missing; KMZ intervals remain a fallback. Source age, cached results and errors remain visible. The previous manual Weather Lab CSV/import/external panel is removed because no automatic, configured user-facing integration is available in this project.

## Current weather and outing advice

The disk cache uses named HourlyWeather fields. The previous positional read incorrectly put humidity, wind and WMO code into the snowfall fields. It now preserves snow, humidity, wind, WMO and hourly apparent temperature separately. Forecast time is kept separately from a possibly older station observation; future cache timestamps do not become fresh just by opening them. API temperature and wind units, finite bounds and source age are checked.

For Taiwan locations, fresh public CWA surface observations may supply current temperature, humidity, wind, pressure and condition when a WGS84 station is within 15km, differs from the forecast grid elevation by at most 200m and is no older than 30 minutes. Invalid -99 sentinel values are rejected. If suitable observations are unavailable, the model remains available. Forecasts continue to use Open-Meteo. The header gives station, distance and observation time and opens an explanation of the source and method. CWA apparent temperature uses wind in m/s, vapour pressure and humidity; Open-Meteo apparent temperature has its own radiation/humidity/wind method. These are estimates and do not guarantee how every person feels.

Outing suggestions use actual temperature as a cold safeguard even if apparent temperature or personal preference is warmer. They consider the next six hours, a ≥6°C drop, wind and wet cold, while keeping the measured numbers unchanged. Clothing thresholds are personal planning suggestions, separate from official weather warnings.

## Practical tools

Marine data is fetched in-app from Open-Meteo Marine for selected Taiwan offshore grids. It includes wave and swell height, period and **FROM** direction, ocean current speed and **TO** direction, and sea-surface temperature. Units are validated/converted, zero remains zero and missing remains missing. Users can choose a coast/island and drag future time. Changing coast clears the previous region's displayed values immediately. Cached fallback is limited and visibly dated. A north-up vector dial is a geographic bearing diagram, not a magnetic device compass.

Sun/moon positions and times are calculated offline with Apache-2.0 commons-suncalc 3.11. Users can choose a date and local civil time, see phase/illumination, azimuth, altitude and rise/set events. Daily windows follow the actual 23/24/25-hour local day. Loading and calculation failure are distinct from a day with no event. Hills, clouds and atmospheric variation affect actual visibility.

## Monitoring and sound

Each station uses its source timestamp, including the live RTS `ts` field; receipt time is only a fallback when a real measurement lacks a source timestamp. At exactly 20 seconds it is still fresh, after that its observation expires. Missing rows are retained with their original timestamp. Explicit maintenance/offline frames can change availability without renewing readings. Old/future measurements cannot trigger the opt-in station detection alert or poison its latest-time marker. Brief reconnection does not immediately discard a still-fresh reading.

Ambience respects Android media volume and audio focus, pauses in background, retries when returning and exposes playing/preparing/off/muted/no-weather/focus/error states. The volume spans 0–100%; dragging it above zero enables sound. An eight-second preview is available. Newly absent settings default to enabled; an explicitly stored off value is preserved. All existing original weather loops and pop alarm arrangements remain present. No physical speaker or wake-up effectiveness is claimed as tested in this environment.

## Primary references

- [CWA city probability index](https://www.cwa.gov.tw/Data/js/typhoon/TY_NEWS-Data.js) and [official KMZ data](https://data.gov.tw/dataset/10059).
- [CWA surface observations O-A0001-001](https://opendata.cwa.gov.tw/dist/opendata-swagger.html) and CWA public file API for that dataset.
- [Open-Meteo forecast documentation](https://open-meteo.com/en/docs) and [marine documentation](https://open-meteo.com/en/docs/marine-weather-api).
- [Google Weather Lab guide](https://developers.google.com/weathernext/guides/weatherlab); the project does not replace it with fabricated AI paths.
- [Natural Earth land](https://www.naturalearthdata.com/downloads/10m-physical-vectors/10m-land/), [minor islands](https://www.naturalearthdata.com/downloads/10m-physical-vectors/10m-minor-islands/), [public-domain terms](https://www.naturalearthdata.com/about/terms-of-use/).
- [commons-suncalc](https://github.com/shred/commons-suncalc) and [Android RemoteViews dimensions](https://developer.android.com/reference/android/widget/RemoteViews).

## Acceptance limits

Automated build/test results, hashes and native Android render previews are recorded in the delivered package validation evidence. The instrumentation test APK is compiled, but a live Android phone/emulator is not connected here. Xiaomi launcher animation behavior, live audio, notification policy, predictive-back feel, thermal behavior, actual 144Hz/FPS and existing-signature installation still require the candidate APK on the phone. Ordinary source and candidate builds do not bypass the repository's official full-milestone acceptance gate.
