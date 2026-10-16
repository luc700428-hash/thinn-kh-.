# thinn-khì V9 Kotlin / thunder-audio fixes

Fixes based on actual GitHub Actions build #21 error artifact:

- WeatherHomeScreen.kt: removed duplicate `maxLines` named argument in the location label Text.
- tools/prepare_real_weather_audio.py: normalized FFmpeg afade duration `d=0.12` and surfaced process failures.

This project has not been compiled in a complete Android SDK environment here. Use the supplied GitHub Actions workflow and check that it completes successfully.
