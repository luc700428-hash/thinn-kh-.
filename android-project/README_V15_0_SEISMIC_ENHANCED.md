# V15.0 臺灣地震監測強化版

- Foreground CWA official *post-earthquake* report polling every ~60s, user-controlled.
- Android background post-report notifications via WorkManager (existing ~15min minimum, best effort).
- Smartphone accelerometer illustrated waveform: NOT a seismic instrument.
- 12s three-phase "趴下、掩護、穩住" voluntary safety drill with animated rings, original sounds, optional Android TTS, haptics. Not a real earthquake warning.
- Original resources: res/raw/thinn_seismic_drill_alert.ogg, thinn_seismic_step.ogg, thinn_seismic_allclear.ogg
- Per-area intensity parsing where official data provide areas.
- Preserves eight selectable standard notification chimes and CWA key encryption.
- Current APK release notes are bundled from assets/current_release_notes.md. Action optional full release_notes overrides it only when nonblank.

**Limitations:** No certified EEW source, TREM-Net station data, waveform stream, predicted arrival time, guaranteed early alert, or 24/7 realtime background feed. The CWA open data endpoints E-A0015-001 / E-A0016-001 are *after-the-fact reports* only.
No TREM-Lite AGPL code was copied. Read its licensing and upstream API terms before direct integration.
