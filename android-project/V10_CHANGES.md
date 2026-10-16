# thinn-khì V10 — Fixed hourly scrubber and CWA notice messaging

- Forecast line starts at left edge and reaches right edge; horizontal scrubbing selects the full 24 hours while vertical scrolling remains usable.
- Selected hour displays accurate WMO-coded weather wording, temperature, rain chance and wind speed.
- Renamed label to 中央氣象署特報. Cards from official RSS remain red; no-alert state only shown after a successful RSS fetch; network failure is distinct.
- Added user-triggered check for official local RSS warning notices; background checking remains 15-minute WorkManager polling and cannot guarantee immediate pushes.
- Micro-city label displays the compact selected location; data selection continues to use the full location.
- Matched sunrise and pressure cards to the weather dial design; air quality moved to last section.
- Richer forecast-based lifestyle suggestions (up to seven), grounded in weather data.
- New original flat umbrella/wind brand mark inspired by minimal weather icon design, not a copy of a third-party logo.

**Limitations:** This source was not compiled on Android SDK or tested on device here. CWA RSS is announcements, NOT authoritative live active alert validity; complete official area/time-aware alert support requires CWA warning data API with authorized key (e.g. W-C0033 series). WorkManager cannot do instant delivery.
