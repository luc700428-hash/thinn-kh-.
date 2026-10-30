# CWA warning details regression capture

Retrieved directly from CWA on 2026-10-30 at approximately 05:04 Taiwan time.
The RSS contains both a wind notice published at 04:29 and earthquake report 074
published at 04:40. The wind product is already published but its affected period
is 17:00–23:00 that day. This is the combination shown in the user's failed page.

- RSS: https://www.cwa.gov.tw/rss/Data/cwa_warning.xml
- County scope: CWA file API, W-C0033-001, JSON
- Complete bulletin: CWA file API, W-C0033-002, JSON
- Official wind page: https://www.cwa.gov.tw/V8/C/P/Warning/W25.html
- CWA format: https://opendata.cwa.gov.tw/opendatadoc/Warning/W-C0033-006.pdf

Fixtures preserve the official body and timestamps. They are historical test
inputs, never bundled as live app alerts. Tests use a fixed 05:01 Taiwan clock,
then verify exact start/end boundaries, cancellation and failure handling.
