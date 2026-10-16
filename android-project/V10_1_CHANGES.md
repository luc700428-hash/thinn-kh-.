# thinn-khì V10.1 — location-search refinement

- Main place label preserves **台灣 · 縣市 · 鄉鎮市區** when Geocoder supplies the hierarchy.
- Foreground GPS refresh is preferred on app launch when location permission exists.
- Users may search for more cities **only by tapping 搜尋 or the keyboard Search action**, save up to 30 locations on device, open any saved city temporarily, or return to GPS.
- 24-hour chart and local-hour weather wording, official warning title rename, pressure/sunrise dials, bottom AQ and visibility, enriched advice and flat icon are retained from V10.
- Display text for empty RSS result is cautious: no matching **recent announcements** in the fetched feed, not a guarantee there are no official currently active warnings.
- Alerts are regular Android grouped notifications; Xiaomi Super Island / OPPO Atomic Island **not integrated** because separate vendor permissions/integration may be required.

Limitations: No phone APK build performed in this environment. Official RSS is not a real-time current-effective-warning API; polling may be delayed (15-minute minimum via WorkManager), and device-island features require the vendor's reviewed integration. County-level 3D assets don't include distinct modeled assets for every township, although the caption uses the township from location.
