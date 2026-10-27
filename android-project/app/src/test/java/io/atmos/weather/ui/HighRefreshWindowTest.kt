package io.atmos.weather.ui

import android.app.Activity
import android.app.Application
import android.os.Build
import android.widget.FrameLayout
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[31,35],application=Application::class)
class HighRefreshWindowTest {
    @Test fun appliesAvailableWindowModeAnd144HintToExistingChildren() {
        val controller=Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity=controller.get();val root=FrameLayout(activity);val nested=FrameLayout(activity);val child=TextView(activity)
            root.addView(nested);nested.addView(child);activity.setContentView(root)
            val original=activity.window.decorView.display!!.mode
            HighRefreshWindow.apply(activity.window,root)
            assertTrue(activity.window.attributes.preferredRefreshRate>0f)
            assertEquals(original.physicalWidth,activity.window.decorView.display!!.mode.physicalWidth)
            assertEquals(original.physicalHeight,activity.window.decorView.display!!.mode.physicalHeight)
            if(Build.VERSION.SDK_INT>=35) {
                assertEquals(144f,root.requestedFrameRate,0f);assertEquals(144f,nested.requestedFrameRate,0f);assertEquals(144f,child.requestedFrameRate,0f)
            }
            val rate=activity.window.attributes.preferredRefreshRate;val id=activity.window.attributes.preferredDisplayModeId
            HighRefreshWindow.apply(activity.window,root)
            assertEquals(rate,activity.window.attributes.preferredRefreshRate,0f);assertEquals(id,activity.window.attributes.preferredDisplayModeId)
        } finally{controller.pause().stop().destroy()}
    }
}
