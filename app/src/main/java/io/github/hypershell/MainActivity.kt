package io.github.hypershell

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

class MainActivity : ComponentActivity() {
    private val viewModel: HyperShellViewModel by viewModels()
    private val processObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) = viewModel.onAppForegrounded()
        override fun onStop(owner: LifecycleOwner) = viewModel.onAppBackgrounded()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)
        enableEdgeToEdge()
        setContent {
            HyperShellApp(viewModel = viewModel)
        }
        openShortcut(intent)
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
    }
}
