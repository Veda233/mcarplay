package com.mcarplay.app.video

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.view.Surface

/**
 * Diagnostic-only local source: render moving bars directly into the MediaCodec input Surface.
 * This must not depend on CarWith, CarLife, virtual displays, or Shizuku.
 */
object CarPlayTestPattern {
    fun start(
        surface: Surface,
        width: Int,
        height: Int,
        keepRunning: () -> Boolean,
        log: (String) -> Unit,
    ) {
        Thread({
            var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
            var context: EGLContext = EGL14.EGL_NO_CONTEXT
            var window: EGLSurface = EGL14.EGL_NO_SURFACE
            try {
                display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
                check(display != EGL14.EGL_NO_DISPLAY) { "No EGL display" }
                check(EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 0)) { "EGL init failed" }
                val attribs = intArrayOf(
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                    EGL14.EGL_NONE,
                )
                val configs = arrayOfNulls<EGLConfig>(1)
                val count = IntArray(1)
                check(EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0) && count[0] > 0) {
                    "No EGL config"
                }
                val config = requireNotNull(configs[0])
                context = EGL14.eglCreateContext(
                    display, config, EGL14.EGL_NO_CONTEXT,
                    intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
                )
                check(context != EGL14.EGL_NO_CONTEXT) { "EGL context failed" }
                window = EGL14.eglCreateWindowSurface(
                    display, config, surface, intArrayOf(EGL14.EGL_NONE), 0,
                )
                check(window != EGL14.EGL_NO_SURFACE) { "EGL window surface failed" }
                check(EGL14.eglMakeCurrent(display, window, window, context)) { "EGL makeCurrent failed" }
                log("TEST: 本地动态测试条纹已开始 ${width}x${height}")
                var frame = 0L
                while (keepRunning()) {
                    GLES20.glViewport(0, 0, width, height)
                    GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
                    GLES20.glClearColor(0.05f, 0.10f, 0.19f, 1f)
                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                    GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
                    val colors = arrayOf(
                        floatArrayOf(0.95f, 0.18f, 0.20f),
                        floatArrayOf(0.12f, 0.75f, 0.35f),
                        floatArrayOf(0.20f, 0.48f, 0.98f),
                        floatArrayOf(0.96f, 0.84f, 0.18f),
                        floatArrayOf(0.95f, 0.95f, 0.95f),
                    )
                    val barWidth = width / colors.size
                    colors.forEachIndexed { index, color ->
                        GLES20.glScissor(index * barWidth, height / 4, if (index == colors.lastIndex) width - index * barWidth else barWidth, height / 2)
                        GLES20.glClearColor(color[0], color[1], color[2], 1f)
                        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                    }
                    // Moving white marker proves that live video, rather than a cached image, is displayed.
                    GLES20.glScissor(((frame * 9) % width).toInt(), 0, (width / 20).coerceAtLeast(2).coerceAtMost(width), height / 6)
                    GLES20.glClearColor(1f, 1f, 1f, 1f)
                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                    GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
                    EGLExt.eglPresentationTimeANDROID(display, window, System.nanoTime())
                    check(EGL14.eglSwapBuffers(display, window)) { "EGL swapBuffers failed" }
                    frame++
                    Thread.sleep(50L)
                }
            } catch (error: Exception) {
                log("TEST: 测试画面渲染异常 - ${error.message}")
            } finally {
                if (display != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                    if (window != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, window)
                    if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
                    EGL14.eglTerminate(display)
                }
                log("TEST: 测试画面渲染线程退出")
            }
        }, "carplay-test-pattern").apply { isDaemon = true; start() }
    }
}
