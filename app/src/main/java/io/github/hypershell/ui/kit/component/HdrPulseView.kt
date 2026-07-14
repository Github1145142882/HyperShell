package io.github.hypershell.ui.kit.component

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Shader
import android.hardware.DataSpace
import android.os.Build
import android.util.Log
import android.view.SurfaceControl
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * Local HDR press light drawn directly into the selected Miuix indicator SurfaceView.
 *
 * Using the SurfaceView's own RGBA_F16 buffer is intentional. Some HyperOS compositors clamp an
 * extended-range child SurfaceControl to SDR even when its HardwareBuffer and data space are HDR.
 */
class HdrPulseView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    private val bloomPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linearExtendedSrgb = ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB)
    private var surfaceReady = false
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var framePosted = false

    var intensity: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            configureHdrLayer(field > MIN_ACTIVE_INTENSITY)
            requestFrame()
        }

    var pulseColor: Int = Color.WHITE
        set(value) {
            field = value
            if (intensity > MIN_ACTIVE_INTENSITY) requestFrame()
        }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.RGBA_F16)
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        surfaceWidth = width
        surfaceHeight = height
        configureHdrLayer(intensity > MIN_ACTIVE_INTENSITY)
        requestFrame()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        configureHdrLayer(intensity > MIN_ACTIVE_INTENSITY)
        requestFrame()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        surfaceWidth = 0
        surfaceHeight = 0
        framePosted = false
    }

    override fun onDetachedFromWindow() {
        configureHdrLayer(false)
        surfaceReady = false
        super.onDetachedFromWindow()
    }

    private fun configureHdrLayer(active: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            setDesiredHdrHeadroom(if (active) HDR_HEADROOM else 1f)
        }
        if (!surfaceReady || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val control = runCatching { surfaceControl }.getOrNull() ?: return
        if (!control.isValid) return
        runCatching {
            SurfaceControl.Transaction().use { transaction ->
                transaction
                    .setDataSpace(control, DataSpace.DATASPACE_SCRGB_LINEAR)
                    .setOpaque(control, false)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    transaction.setExtendedRangeBrightness(
                        control,
                        if (active) HDR_HEADROOM else 1f,
                        if (active) HDR_HEADROOM else 1f,
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    transaction.setDesiredHdrHeadroom(control, if (active) HDR_HEADROOM else 1f)
                }
                transaction.apply()
            }
        }.onFailure { Log.w(TAG, "Unable to configure direct HDR indicator surface", it) }
    }

    private fun requestFrame() {
        if (!surfaceReady || framePosted) return
        framePosted = true
        postOnAnimation {
            framePosted = false
            drawFrame()
        }
    }

    private fun drawFrame() {
        if (!surfaceReady || surfaceWidth <= 0 || surfaceHeight <= 0 || !holder.surface.isValid) return
        val canvas = runCatching { holder.lockHardwareCanvas() }
            .recoverCatching { holder.lockCanvas() }
            .getOrElse {
                Log.w(TAG, "Unable to lock direct HDR indicator canvas", it)
                return
            }
        try {
            if (intensity > MIN_ACTIVE_INTENSITY) drawPulse(canvas)
            else canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        } finally {
            runCatching { holder.unlockCanvasAndPost(canvas) }
                .onFailure { Log.w(TAG, "Unable to post direct HDR indicator frame", it) }
        }
    }

    private fun drawPulse(canvas: Canvas) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val width = surfaceWidth.toFloat()
        val height = surfaceHeight.toFloat()
        val linear = Color.valueOf(pulseColor).convert(linearExtendedSrgb)
        val transparent = Color.pack(0f, 0f, 0f, 0f, linearExtendedSrgb)
        // Keep the outside quiet and drive the centre close to the requested HDR peak. The
        // previous low-alpha carrier only reached about 1.3x SDR after premultiplication, which
        // looked like a grey tint even when the compositor accepted the HDR buffer.
        val carrier = hdrColor(linear, 0.46f * intensity)
        val hotCore = hdrColor(linear, 0.96f * intensity)
        val innerGlow = hdrColor(linear, 0.72f * intensity)
        val falloff = hdrColor(linear, 0.22f * intensity)
        val cornerRadius = height * 0.5f

        // The carrier keeps a meaningful area above SDR white; the radial layer supplies the
        // requested centre-bright, outside-dark appearance without a grey/black dimming overlay.
        bloomPaint.shader = null
        bloomPaint.setColor(carrier)
        canvas.drawRoundRect(0f, 0f, width, height, cornerRadius, cornerRadius, bloomPaint)

        val spreadRadius = maxOf(width, height) * 0.64f
        bloomPaint.shader = RadialGradient(
            width * 0.5f,
            height * 0.5f,
            spreadRadius,
            longArrayOf(hotCore, hotCore, innerGlow, falloff, transparent),
            floatArrayOf(0f, 0.18f, 0.42f, 0.68f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(0f, 0f, width, height, cornerRadius, cornerRadius, bloomPaint)
    }

    private fun hdrColor(color: Color, alpha: Float): Long = Color.pack(
        color.red() * HDR_HEADROOM,
        color.green() * HDR_HEADROOM,
        color.blue() * HDR_HEADROOM,
        alpha.coerceIn(0f, 1f),
        linearExtendedSrgb,
    )

    private companion object {
        const val TAG = "HyperShellHdrPulse"
        const val HDR_HEADROOM = 4f
        const val MIN_ACTIVE_INTENSITY = 0.005f
    }
}
