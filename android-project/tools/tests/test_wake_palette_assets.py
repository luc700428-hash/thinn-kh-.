"""Validate the new audible payload, fallback files and loop continuity without DSP dependencies."""
from pathlib import Path
import array,hashlib,json,math,struct,unittest,wave

ROOT=Path(__file__).resolve().parents[2]
ASSETS=ROOT/'app/src/main/assets'

class WakePaletteAssets(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest=json.loads((ASSETS/'next_weather/wake_palette_manifest.json').read_text())

    def test_eight_added_palettes_resolve_four_complete_scores(self):
        m=self.manifest
        self.assertEqual(len(m['added_themes']),8)
        self.assertEqual(m['total_selectable_themes'],20)
        expected={(t,v) for t in m['added_themes'] for v in ('neutral','clear','rain','snow')}
        self.assertEqual(expected,{(a['theme'],a['variant']) for a in m['assets']})
        self.assertEqual(len(m['assets']),32)
        self.assertFalse(m['human_listening_or_wake_efficacy_verified'])

    def test_audio_is_bounded_non_silent_and_has_continuous_loop_joins(self):
        for a in self.manifest['assets']:
            path=ASSETS/a['path']
            self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(),a['sha256'])
            self.assertLess(path.stat().st_size,6_000_000)
            with wave.open(str(path)) as w:
                self.assertEqual((w.getnchannels(),w.getsampwidth(),w.getframerate()),(1,2,32000))
                self.assertGreater(w.getnframes(),32000*8)
                self.assertLess(w.getnframes(),32000*30)
                first=struct.unpack('<hh',w.readframes(2))
                w.setpos(w.getnframes()-2);last=struct.unpack('<hh',w.readframes(2))
                self.assertEqual(first[0],last[1])
                self.assertLessEqual(abs((first[1]-first[0])-(last[1]-last[0])),1)
                w.rewind();pcm=array.array('h',w.readframes(w.getnframes()))
                peak=max(abs(x) for x in pcm)/32768
                rms=math.sqrt(sum(x*x for x in pcm)/len(pcm))/32768
                self.assertLessEqual(peak,.701)
                self.assertGreater(rms,.04)
                self.assertLess(rms,.13)

    def test_every_score_has_a_real_distinct_ogg_fallback(self):
        seen=set()
        for a in self.manifest['assets']:
            data=(ROOT/a['fallback']).read_bytes()
            digest=hashlib.sha256(data).hexdigest()
            self.assertEqual(digest,a['fallback_sha256'])
            self.assertTrue(data.startswith(b'OggS'))
            self.assertIn(b'vorbis',data[:128])
            self.assertGreater(len(data),20_000)
            self.assertNotIn(digest,seen);seen.add(digest)

    def test_new_families_have_distinct_timings_and_scores(self):
        m=self.manifest
        self.assertEqual(8,len({p['bpm'] for p in m['profiles'].values()}))
        self.assertEqual({3,4,6},{p['meter'] for p in m['profiles'].values()})
        self.assertEqual(8,len({a['sha256'] for a in m['assets'] if a['variant']=='neutral'}))
        for t in m['added_themes']:
            self.assertEqual(4,len({a['sha256'] for a in m['assets'] if a['theme']==t}))

if __name__=='__main__':unittest.main()
