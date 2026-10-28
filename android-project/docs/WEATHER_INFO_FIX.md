# Weather information and in-app CWA alerts

Base: d391c7d3e07a2cb8a92a3ffb64a9d15c34f577ce (workflow 309 passed both jobs).

The home observation preview rejected HTTP 200 O-A0058-003 PNG data because the official S3 response declares binary/octet-stream. A live 2026-10-28 response was 700348 bytes with PNG signature and 3600 x 3600 bounds. The decoder now checks the actual signature and bounded dimensions, decodes a 1800px preview (approximately 13 MB), preserves cancellation, and serializes requests. The card retries once, offers manual refresh, retains usable cached observations and shows the official DateTime from metadata, with separate labels for file-update time or older imagery. The native pan/zoom radar remains separate and unchanged.

Official W-C0033-002 provides full bulletin text, validity and affected areas. Native details preserve the published text and area list, distinguish expired/cancelled or unverifiable validity from active notices, and label cached/failed updates. County headline validation W-C0033-001 remains unchanged. RSS is a separate recent-announcement list, not a complete still-active warning registry. Other official maps/products open inside a CWA-only HTTPS WebView, with zoom, back navigation, theme-matched Android context, compatible algorithmic darkening, and renderer-failure recovery. No file access, JavaScript bridge, external Intent or browser dispatch is added. Product PDFs and third-party links are not a new general-purpose browser feature. The device's WebView implementation and the official site's theme support still affect embedded-page appearance.

All home alert entries, the tools entry and typhoon notice details use this in-app flow. The root Dock closes the new details before moving to the requested tab. Model attribution is retained in the footer and the detail page; the verbose multi-model line under the temperature card is removed.

New weather insights use existing current/forecast/air-quality fields. Three-hour window comparisons require three consecutive future hourly records with valid precipitation probability, wind and temperature. Missing values are not treated as zeros. Laundry requires humidity as well. Severe WMO conditions, wind and extreme temperatures penalize ranking. These are transparent relative comparisons, not safety predictions or official alerts. Mean wind and gust, WMO weather conditions and model AQI remain distinct. Dew point uses the Magnus water-saturation approximation only from -40 to 50 Celsius, not a direct dew-point observation.

Shared light/dark theme tokens supply readable primary/secondary and selected-control colors. Common forms and drum pickers no longer force a dark surface with white text. Alarm/task/report pages, dropdown surfaces, outlined buttons, weekday toggles and unit selectors follow those tokens. Dialog/popup windows retain the parent reading density and content color instead of reverting to the system font scale. Large weekday selections wrap into rows. Decorative ringtone art keeps its color palette with a darker text scrim. System bar icons follow the visible page, keeping white icons on the immersive weather background and dark icons on light tool pages. Active emergency banners retain white-on-red presentation. Existing transparent sliders, springs, radar geometry and refresh-rate requests are unchanged.

Verification includes native Robolectric rendering in light/dark modes, 100/150% reading sizes, real official radar bytes and full bulletin fixtures, expiry/cancellation/URL validation, data-missing/gap/severe-weather window tests, the existing test suite, Python checks and APK builds. Full Android instrumentation remains the same strict eight scenarios, with weekday selection checks added to the choice scenario. A local emulator/phone is not available; this patch must pass a new GitHub run after upload. No signed OTA is published by this package.

Primary references:
- https://opendata.cwa.gov.tw/dataset/observation/O-A0058-003
- https://opendata.cwa.gov.tw/dataset/all/W-C0033-002
- https://www.cwa.gov.tw/V8/C/P/Warning/W25.html
- https://developer.android.com/develop/ui/views/layout/webapps/dark-theme
- https://developer.android.com/jetpack/androidx/releases/webkit (1.14.0, compatible stable line)
- https://qed.epa.gov/hms/meteorology/humidity/algorithms/ (Magnus coefficients)
- https://opendata.cwa.gov.tw/opendatadoc/insrtuction/CWB_Data_Standard.pdf (extended Beaufort table)
