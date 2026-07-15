package io.github.hypershell

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

class MainActivity : ComponentActivity() {
    private val viewModel: HyperShellViewModel by viewModels()
    private var hdrSurfaceWarmupScheduled = false
    private val processObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            viewModel.onAppForegrounded()
            val stoppedAt = lastProcessStopUptime
            if (stoppedAt != 0L && SystemClock.uptimeMillis() - stoppedAt >= LONG_BACKGROUND_MS) {
                lastProcessStopUptime = 0L
                hdrSurfaceWarmedForProcess = false
                hdrSurfaceWarmupScheduled = false
            }
        }

        override fun onStop(owner: LifecycleOwner) {
            lastProcessStopUptime = SystemClock.uptimeMillis()
            viewModel.onAppBackgrounded()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)
        enableEdgeToEdge()
        setContent {
            HyperShellApp(viewModel = viewModel)
        }
        if (savedInstanceState == null) openShortcut(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || hdrSurfaceWarmupScheduled || hdrSurfaceWarmedForProcess) return
        hdrSurfaceWarmupScheduled = true
        hdrSurfaceWarmedForProcess = true
        // HyperOS does not expose the HDR headroom of this first Activity surface until it has
        // actually left composition once. recreate() overlaps the old and new surfaces and is
        // therefore insufficient. A short opaque SDR activity in the same task performs the
        // same compositor hand-off as backgrounding, without exposing the launcher or stopping
        // the process lifecycle (and therefore without terminating the terminal session).
        window.decorView.postDelayed(
            {
                if (isFinishing || isDestroyed || !hasWindowFocus()) {
                    hdrSurfaceWarmupScheduled = false
                    hdrSurfaceWarmedForProcess = false
                    return@postDelayed
                }
                val decor = window.decorView
                if (decor.width <= 0 || decor.height <= 0) {
                    resetHdrWarmup()
                    return@postDelayed
                }
                val preview = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
                // decor.draw() does not include SurfaceView/hardware-composed content and can
                // yield a black frame. PixelCopy samples the final compositor output instead,
                // so the short HDR surface hand-off is visually the same frame as MainActivity.
                PixelCopy.request(
                    window,
                    preview,
                    { result ->
                        if (result == PixelCopy.SUCCESS && !isFinishing && !isDestroyed && hasWindowFocus()) {
                            startHdrWarmup(preview)
                        } else {
                            preview.recycle()
                            resetHdrWarmup()
                        }
                    },
                    Handler(Looper.getMainLooper()),
                )
            },
            HDR_SURFACE_SETTLE_MS,
        )
    }

    private fun startHdrWarmup(preview: Bitmap) {
        HdrWarmupActivity.setPreview(preview)
        runCatching {
            startActivity(
                Intent(this, HdrWarmupActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION),
            )
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }.onFailure {
            HdrWarmupActivity.clearPreview()
            resetHdrWarmup()
        }
    }

    private fun resetHdrWarmup() {
        hdrSurfaceWarmupScheduled = false
        hdrSurfaceWarmedForProcess = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openShortcut(intent)
    }

    private fun openShortcut(intent: Intent?) {
        intent?.getStringExtra(EXTRA_SHORTCUT_PATH)?.let(viewModel::openBookmark)
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processObserver)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SHORTCUT_PATH = "io.github.hypershell.extra.SHORTCUT_PATH"
        private const val HDR_SURFACE_SETTLE_MS = 700L
        private const val LONG_BACKGROUND_MS = 2_000L
        private var hdrSurfaceWarmedForProcess = false
        private var lastProcessStopUptime = 0L
    }
}
