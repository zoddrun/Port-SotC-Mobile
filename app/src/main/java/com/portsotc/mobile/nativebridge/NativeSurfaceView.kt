package com.portsotc.mobile.nativebridge

import android.content.Context
import android.graphics.PixelFormat
import android.util.AttributeSet
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * SurfaceView whose lifecycle is forwarded to the native EGL render thread.
 * The native side owns the EGL surface and must be stopped before the Android
 * surface is destroyed.
 */
class NativeSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    interface Callback {
        fun onNativeSurfaceCreated(surface: Surface)
        fun onNativeSurfaceDestroyed()
    }

    var callback: Callback? = null

    init {
        holder.addCallback(this)
        holder.setFormat(PixelFormat.RGBA_8888)
        setWillNotDraw(true)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        callback?.onNativeSurfaceCreated(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // The native renderer queries the current buffer size every frame.
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        callback?.onNativeSurfaceDestroyed()
    }
}
