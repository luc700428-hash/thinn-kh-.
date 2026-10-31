package io.atmos.weather.next

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class StaticAmbiencePlaybackTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private val path="next_weather/ambience/sunny.wav"
    @Test fun staticTrackAcceptsThePlatformWaitingForDataStateAndActuallyStarts() {
        val blank=AudioTrack(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build(),
            AudioFormat.Builder().setSampleRate(44100).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
            88200,AudioTrack.MODE_STATIC,AudioManager.AUDIO_SESSION_ID_GENERATE)
        try{assertEquals(AudioTrack.STATE_NO_STATIC_DATA,blank.state)}finally{blank.release()}
        val loop=NextLoopTrack.open(context,path,AudioAttributes.USAGE_MEDIA)
        assertNotNull("A valid PCM asset must not be rejected before its first write",loop)
        try {
            loop!!.start(.5f);assertTrue(loop.isPlaying)
            loop.pause();assertFalse(loop.isPlaying)
            loop.resume();assertTrue(loop.isPlaying)
        }finally{loop?.close()}
    }
    @Test fun everyWeatherAssetAndTheAlarmUsageCanOpenARealStaticTrack() {
        val assets=context.assets.list("next_weather/ambience")!!.filter{it.endsWith(".wav")}
        assertTrue(assets.size>=17)
        for(asset in assets) {
            val loop=NextLoopTrack.open(context,"next_weather/ambience/$asset",AudioAttributes.USAGE_MEDIA)
            assertNotNull(asset,loop);loop!!.start(.4f);assertTrue(asset,loop.isPlaying);loop.close()
        }
        val alarm=NextLoopTrack.open(context,path,AudioAttributes.USAGE_ALARM)
        assertNotNull(alarm);alarm!!.start(.5f);assertTrue(alarm.isPlaying);alarm.close()
    }
    @Test fun foregroundMuteResumeAndExplicitRetryProduceTheCorrectPlaybackState()=runTest {
        (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).setStreamVolume(AudioManager.STREAM_MUSIC,5,0)
        val controller=NextAmbienceController(context)
        try {
            controller.foreground(true);controller.configure(path,true,.5f)
            assertEquals("playing",controller.state.value.code)
            controller.configure(path,true,0f);assertEquals("muted",controller.state.value.code)
            controller.configure(path,true,.6f);assertEquals("playing",controller.state.value.code)
            controller.foreground(false);assertEquals("paused",controller.state.value.code)
            controller.foreground(true);assertEquals("playing",controller.state.value.code)
            controller.configure(path,true,.6f,forceRestart=true);assertEquals("playing",controller.state.value.code)
        }finally{controller.close()}
    }
    @Test fun failedAssetDoesNotPreventTheNextPreviewFromRecovering()=runTest {
        (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).setStreamVolume(AudioManager.STREAM_MUSIC,5,0)
        val controller=NextAmbienceController(context)
        try {
            controller.foreground(true);controller.configure("next_weather/ambience/missing.wav",true,.5f)
            assertEquals("unavailable",controller.state.value.code)
            controller.configure(path,true,.5f,forceRestart=true)
            assertEquals("playing",controller.state.value.code)
        }finally{controller.close()}
    }
}
