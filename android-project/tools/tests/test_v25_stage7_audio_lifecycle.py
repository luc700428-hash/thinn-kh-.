"""Source-level regression invariants. Kotlin test + CI + actual device audio QA required."""
from pathlib import Path
import unittest
A=Path(__file__).resolve().parents[2]/'app/src/main/java/io/atmos/weather'

class Stage7AudioSafety(unittest.TestCase):
    def test_ambient_foreground_restores_gain_and_silences_both_transition_tracks(self):
        s=(A/'next/NextPcmAudio.kt').read_text()
        for line in ('private var transitioning: NextLoopTrack?',
                     'if(transitioning==null) track?.volume(level)',
                     'transitioning?.pause()',
                     'val to=if(audible()) level else 0f',
                     'if(audible()) {incoming.volume(level);incoming.resume()}',
                     'disposed=true',
                     'private var released = false'):
            self.assertIn(line,s)
        self.assertNotIn('val playing=active && audio.ringerMode == AudioManager.RINGER_MODE_NORMAL',s)
    def test_earthquake_hold_does_not_poison_deduplication(self):
        s=(A/'voice/NaturalVoiceHub.kt').read_text()
        self.assertLess(s.index('if(now<earthquakeHoldUntil) return false'),s.index('seen[req.dedupeKey]=now'))
        self.assertIn('if(req.priority==Priority.EARTHQUAKE) return false',s)
        self.assertIn('fun interruptForEarthquake()',s)
    def test_unknown_weather_does_not_play_sunny_tone(self):
        s=(A/'audio/WeatherAudio.kt').read_text()
        self.assertIn('else -> null // Unknown/overcast/fog',s)
        self.assertIn('mixer.configure(resource ?: 0,enabled && resource!=null,volume)',s)
    def test_audio_policy_is_wired_into_runtime(self):
        p=(A/'next/NextAudioLifecyclePolicy.kt').read_text()
        runtime=(A/'next/NextPcmAudio.kt').read_text()
        self.assertIn('foreground && enabled && normalRinger',p)
        self.assertIn('NextAudioLifecyclePolicy.canPlay(',runtime)

if __name__=='__main__':unittest.main()
