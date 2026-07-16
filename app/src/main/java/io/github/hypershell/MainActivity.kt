package io.github.hypershell

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import io.github.hypershell.onboarding.OnboardingEntry
import io.github.hypershell.onboarding.OriginalProvisionSettings
import io.github.hypershell.onboarding.HyperShellProvisionActivity
import com.sevtinge.hyperceiler.common.utils.PrefsBridge

class MainActivity : ComponentActivity() {
    private val viewModel: HyperShellViewModel by viewModels()
    private var hdrSurfaceWarmupScheduled = false
    private var provisionInFlight = false
    private var provisionCompletionPending = false
    private var welcomeLaunchRequested = false
    private var welcomeInFlight = false
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
        PrefsBridge.initForApp(applicationContext)
        val returningFromProvision = intent.getBooleanExtra(EXTRA_DEBUG_OOBE, false)
        val returningFromWelcome = intent.getBooleanExtra(EXTRA_WELCOME_COMPLETE, false)
        val firstMainInProcess = !mainCreatedForProcess
        mainCreatedForProcess = true
        welcomeLaunchRequested = firstMainInProcess && !returningFromProvision && !returningFromWelcome
        consumeWelcomeCompletion(intent)
        consumeProvisionCompletion(intent)
        ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)
        enableEdgeToEdge()
        setContent {
            HyperShellApp(viewModel = viewModel)
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState
                    .map { state ->
                        state.settingsLoaded &&
                            state.onboardingEntry == null &&
                            state.settings.bottomBarHdrFeedback
                    }
                    .distinctUntilChanged()
                    .collect { enabled ->
                        if (enabled) {
                            scheduleHdrWarmupIfReady()
                        } else {
                            hdrSurfaceWarmupScheduled = false
                            hdrSurfaceWarmedForProcess = false
                            HdrWarmupActivity.clearPreview()
                        }
                    }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState
                    .map { state -> state.settingsLoaded && state.onboardingEntry != null }
                    .distinctUntilChanged()
                    .collect { pending ->
                        if (pending && Build.VERSION.SDK_INT >= 35 && !provisionCompletionPending) {
                            launchOriginalProvision()
                        }
                    }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState
                    .map { state ->
                        Triple(
                            state.settingsLoaded,
                            state.onboardingEntry == null,
                            state.settings.showWelcomeOnLaunch,
                        )
                    }
                    .distinctUntilChanged()
                    .collect { (loaded, onboardingComplete, enabled) ->
                        if (loaded && onboardingComplete && welcomeLaunchRequested) {
                            if (enabled) launchWelcomeOnly() else welcomeLaunchRequested = false
                        }
                    }
            }
        }
        if (savedInstanceState == null) openShortcut(intent)
    }

    private fun launchWelcomeOnly() {
        if (welcomeInFlight || provisionInFlight) return
        welcomeInFlight = true
        welcomeLaunchRequested = false
        runCatching {
            startActivity(
                Intent(this, HyperShellProvisionActivity::class.java)
                    .putExtra(HyperShellProvisionActivity.EXTRA_WELCOME_ONLY, true),
            )
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
            // The original HyperCeiler scale-up ActivityOptions require a newly created target
            // window. Leaving this singleTask instance under the welcome page makes Android
            // deliver onNewIntent() instead and degrades the transition to a horizontal slide.
            finish()
        }.onFailure {
            welcomeInFlight = false
        }
    }

    private fun launchOriginalProvision() {
        if (provisionInFlight) return
        provisionInFlight = true
        val entry = viewModel.uiState.value.onboardingEntry ?: return
        OriginalProvisionSettings.seed(viewModel.uiState.value.settings)
        activeProvisionEntry = entry
        val intent = Intent().apply {
            setClassName(packageName, ORIGINAL_PROVISION_ACTIVITY)
            putExtra(EXTRA_DEBUG_OOBE, true)
            if (entry == OnboardingEntry.FirstRun) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        }
        runCatching {
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
            if (entry == OnboardingEntry.FirstRun) finish()
        }
            .onFailure {
                provisionInFlight = false
                finish()
            }
    }

    private fun consumeProvisionCompletion(source: Intent?) {
        if (source?.getBooleanExtra(EXTRA_DEBUG_OOBE, false) != true) return
        provisionCompletionPending = true
        val entry = activeProvisionEntry ?: OnboardingEntry.FirstRun
        activeProvisionEntry = null
        source.removeExtra(EXTRA_DEBUG_OOBE)
        viewModel.completeOriginalOnboarding(entry, OriginalProvisionSettings.read())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) scheduleHdrWarmupIfReady()
    }

    private fun scheduleHdrWarmupIfReady() {
        val state = viewModel.uiState.value
        if (!state.settingsLoaded || state.onboardingEntry != null) return
        if (!state.settings.bottomBarHdrFeedback) return
        if (welcomeLaunchRequested || welcomeInFlight) return
        if (!hasWindowFocus() || hdrSurfaceWarmupScheduled || hdrSurfaceWarmedForProcess) return
        hdrSurfaceWarmupScheduled = true
        hdrSurfaceWarmedForProcess = true
        // HyperOS does not expose the HDR headroom of this first Activity surface until it has
        // actually left composition once. recreate() overlaps the old and new surfaces and is
        // therefore insufficient. A short opaque SDR activity in the same task performs the
        // same compositor hand-off as backgrounding, without exposing the launcher or stopping
        // the process lifecycle (and therefore without terminating the terminal session).
        window.decorView.postDelayed(
            {
                if (
                    isFinishing ||
                    isDestroyed ||
                    !hasWindowFocus() ||
                    !viewModel.uiState.value.settings.bottomBarHdrFeedback
                ) {
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
        if (intent.getBooleanExtra(EXTRA_WELCOME_COMPLETE, false)) {
            consumeWelcomeCompletion(intent)
        }
        consumeProvisionCompletion(intent)
        openShortcut(intent)
    }

    private fun consumeWelcomeCompletion(source: Intent?) {
        if (source?.getBooleanExtra(EXTRA_WELCOME_COMPLETE, false) != true) return
        source.removeExtra(EXTRA_WELCOME_COMPLETE)
        welcomeInFlight = false
        welcomeLaunchRequested = false
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
        const val EXTRA_WELCOME_COMPLETE = "io.github.hypershell.extra.WELCOME_COMPLETE"
        private const val ORIGINAL_PROVISION_ACTIVITY =
            "io.github.hypershell.onboarding.HyperShellProvisionActivity"
        private const val EXTRA_DEBUG_OOBE = "extra_debug_oobe"
        private const val HDR_SURFACE_SETTLE_MS = 700L
        private const val LONG_BACKGROUND_MS = 2_000L
        private var hdrSurfaceWarmedForProcess = false
        private var mainCreatedForProcess = false
        private var lastProcessStopUptime = 0L
        private var activeProvisionEntry: OnboardingEntry? = null
    }
}
