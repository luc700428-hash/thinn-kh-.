package com.kyant.backdrop

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import org.intellij.lang.annotations.Language

sealed interface RuntimeShaderCache {

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun obtainRuntimeShader(
        key: String,
        @Language("AGSL") string: String
    ): RuntimeShader
}

internal class RuntimeShaderCacheImpl : RuntimeShaderCache {

    // thinn-khì: compiling AGSL is the slow part, and every glass piece used to compile its own
    // copy whenever a page appeared (heavy jank when switching pages quickly). Compiled shaders
    // are shared for the whole process; each RenderEffect and Paint copies the uniforms it is
    // created with, so sharing one instance is safe.
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun obtainRuntimeShader(key: String, string: String): RuntimeShader =
        SharedRuntimeShaders.obtain(key, string)

    fun clear() {}
}

/** Process-wide compiled shaders. Safe to fill from a background thread ([prewarm]). */
object SharedRuntimeShaders {
    private val shaders = java.util.concurrent.ConcurrentHashMap<String, RuntimeShader>()

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun obtain(key: String, string: String): RuntimeShader =
        shaders[key] ?: RuntimeShader(string).let { shaders.putIfAbsent(key, it) ?: it }

    /** Compiles the glass shaders ahead of the first page, off the main thread. */
    fun prewarm() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        runCatching {
            obtain("Refraction", RoundedRectRefractionShaderString)
            obtain("RefractionWithDispersion", RoundedRectRefractionWithDispersionShaderString)
            obtain("Default", DefaultHighlightShaderString)
        }
    }
}
