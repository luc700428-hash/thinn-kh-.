# thinn-khì rain sound asset credits and processing

The Android build downloads **actual field recordings** instead of shipping synthesized white noise. The source recordings are public domain, with original file pages available below. The playback does not require a network connection after the APK has been built.

- `light_rain.ogg` – *Rain against the window*, recorded by **cori**, public domain ([Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Rain_against_the_window.ogg)). The sound of real rain against a window and wind, mixed at a restrained level.
- `heavy_rain.ogg` – *Rain (1)*, recorded by **ezwa**, public domain ([Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Rain_(1).ogg)). Natural rain field recording.
- `thunder_strike.ogg` – *Rain and thunder*, recorded by **Caesar**, public domain ([Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Rain_and_thunder.ogg)). Real rain and thunder, triggered when a random lightning event occurs.

Audio processing with FFmpeg: high-pass filtering for rumble removal; modest level shaping; overlap/crossfade at ambient loop boundaries; Ogg Vorbis encoding. Thunder is NOT embedded in the looping base rain bed. Instead, the visual lightning event queues the real thunder recording with a variable short delay. Weather transitions use a separate ~1 second crossfade.

## Distribution and licensing

Authors dedicated the above works to the public domain according to their file description pages. Keep these credit/source URLs in distribution documentation. The downloader intentionally fails rather than inserting synthetic rain if the recordings cannot be fetched. Do not represent the APK as complete until the build succeeds with these assets.

## Known limits

A real recording alone cannot guarantee every speaker reproduces each raindrop authentically. MediaPlayer loop boundaries may have implementation-specific timing artifacts even with crossfaded material; test on actual target devices and use AudioTrack/ExoPlayer for a stricter audio guarantee if necessary. The impact/snow/flash animations use lightweight simulated physics, not a rigid-body engine.
