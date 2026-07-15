package io.github.hypershell

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import androidx.core.view.WindowCompat

/**
 * Gives HyperOS one real SDR composition interval before returning to the HDR main surface.
 *
 * This activity deliberately stays in the same task and for less than ProcessLifecycleOwner's
 * stop timeout, so terminal background policy is not triggered.
 */
class HdrWarmupActivity : Activity() {
    private var displayedPreview: Bitmap? = null
    private var previewView: ImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
        window.attributes = window.attributes.apply { windowAnimations = 0 }
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val background = resolveWindowBackground()
        val preview = takePreview()
        if (preview != null) {
            // Install the captured frame as the window background before the first content draw.
            // This closes the one-frame gap where HyperOS otherwise shows its black default
            // surface while switching from the HDR Activity to this SDR hand-off Activity.
            window.setBackgroundDrawable(BitmapDrawable(resources, preview))
        }
        @Suppress("DEPRECATION")
        run {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }
        val content = if (preview != null) {
            ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_XY
                setBackgroundColor(background)
                setImageBitmap(preview)
            }.also {
                displayedPreview = preview
                previewView = it
            }
        } else {
            View(this).apply { setBackgroundColor(background) }
        }
        setContentView(content)

        window.decorView.postDelayed(
            {
                if (!isFinishing && !isDestroyed) finish()
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            },
            HANDOFF_DURATION_MS,
        )
    }

    private fun resolveWindowBackground(): Int {
        val value = TypedValue()
        if (!theme.resolveAttribute(android.R.attr.colorBackground, value, true)) return Color.BLACK
        return if (value.resourceId != 0) {
            @Suppress("DEPRECATION")
            resources.getColor(value.resourceId, theme)
        } else {
            value.data
        }
    }

    override fun onDestroy() {
        previewView?.setImageDrawable(null)
        displayedPreview?.recycle()
        previewView = null
        displayedPreview = null
        super.onDestroy()
    }

    companion object {
        private const val HANDOFF_DURATION_MS = 260L
        private var previewBitmap: Bitmap? = null

        fun setPreview(bitmap: Bitmap?) {
            clearPreview()
            previewBitmap = bitmap
        }

        fun clearPreview() {
            previewBitmap?.recycle()
            previewBitmap = null
        }

        private fun takePreview(): Bitmap? = previewBitmap.also { previewBitmap = null }
    }
}
