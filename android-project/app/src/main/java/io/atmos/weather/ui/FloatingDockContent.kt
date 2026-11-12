package io.atmos.weather.ui
import androidx.compose.foundation.layout.navigationBars

/** Size from content, independent of tablet/window width. */
internal object FloatingDockContent {
    fun length(items:Int,fontScale:Float,vertical:Boolean=false):Float {
        val scale=fontScale.takeIf{it.isFinite()}?.coerceIn(1f,3f)?:1f
        return items.coerceAtLeast(1)*maxOf(82f,50f+32f*scale)+(if(vertical)10f else 12f)
    }
}

@androidx.compose.runtime.Composable internal fun floatingDockContentClearance():androidx.compose.ui.unit.Dp {
 if(LocalWorkspaceLayout.current.tablet)return LocalWorkspaceBottomClearance.current
 val density=androidx.compose.ui.platform.LocalDensity.current
 val system=androidx.compose.foundation.layout.WindowInsets.navigationBars.getBottom(density)/density.density
 return androidx.compose.ui.unit.Dp(maxOf(68f,54f+24f*density.fontScale)+28f+system)
}
