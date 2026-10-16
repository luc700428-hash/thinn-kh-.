# thinn-khì V15.3

Changes to the previous V15.2 project:

- `SeismicHistoricalReplay`: on-demand, bounded ExpTech historical EEW snapshot retrieval, evidence-checked against recent reported epicenters/times/magnitudes. No generated events or automated notifications during replay.
- `SeismicLiveMapPage`: random history replay button, clearly labelled acceleration, P/S illustrative expanding rings at distinct velocities, manual location status, live vs archived data separation.
- `SeismicUserLocation`: last known recent Android GPS first, optional explicit override of 22 Taiwan counties, unknown if location permission unavailable. Never infer exact township from county center.
- `SeismicSpeech`: separate opt-in/out for real incoming EEW and post-event report with conservative source-based local intensity and TTS, respects silent mode and DND. Real EEW retains existing seismic category notification sound.
- `ExpTechRealtimeModels`: separately parses source-provided origin time and explicitly county keyed intensity if available; never generates an unverified "local intensity" or intensity 8.
- `SeismicWavePhysics`: **visual teaching aid only**, not real arrival estimates.
- `EarthquakeReportWorker`: remains 15-minute best effort when enabled, currently uses third-party-republished report. NOT a verified direct CWA publication. A developer-hosted CWA authoritative feed is required for official-source-only push.

Do not represent this as a certified warning service. All status, authorization and origin provenance must be validated in an Android device with real provider response before public OTA rollout.
