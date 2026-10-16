# thinn-khì V9 — Atmosphere Rain / Storm / Snow Rebuild

## What's rebuilt

- Naturalistic rainfall particle distribution: non-uniform velocities, thickness, length, depth and positions. Every drop has its own lifetime (not a synchronized global timer).
- Small, transparent splash rings and secondary rebound droplets on sampled glass/card planes, with low particle counts to protect scroll performance.
- Natural thunderstorm timing: variable 9–24 second flash intervals, short double pulse, delayed storm sound using the same event ID, and optional single haptic cue. **Avoid strobe sensitivity**; the flash is moderate intensity.
- Snowflakes have different sizes/drift speeds; a subtle foreground shelf gradually accumulates snow, plus the existing snow-capped diorama renderer.
- Real-world recording assets are downloaded **during GitHub Actions build**. If remote recording download fails, the build fails rather than silently shipping noise.
- New WAV/OGG processing: 2-second circular overlap-crossfade and environment volume blending on changes.

## Build

1. Upload `thinn-khi_Android_Source_V9.zip` to your GitHub repository root.
2. Replace `.github/workflows/build-apk.yml` with the contents of `thinn-khi-V9-build-apk.yml` (distributed separately).
3. Push / Commit, and inspect GitHub Actions. The job first downloads the field recordings, then compiles, then exports `thinn-khi-V9-APK`.

Rain sounds are not pre-bundled in this source archive because this environment could not fetch sound bytes. The CI workflow must fetch them from their public-domain sources. See `docs/WEATHER_AUDIO_SOURCES.md` for licensing.

**Not yet validated on a real Android device.** Sensory quality and audio seam behavior should be assessed on target Xiaomi/HyperOS phone, including mono/stereo output and loop boundaries.
