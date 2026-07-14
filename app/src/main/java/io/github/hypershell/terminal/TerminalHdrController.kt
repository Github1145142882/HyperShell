package io.github.hypershell.terminal

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import com.termux.view.TerminalRenderer

object TerminalHdrController {
    fun isSupported(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return activity.windowManager.defaultDisplay.hdrCapabilities?.supportedHdrTypes?.isNotEmpty() == true
    }

    fun unsupportedReason(activity: Activity): String = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> "真 HDR 字形渲染需要 Android 10 或更高版本"
        activity.windowManager.defaultDisplay.hdrCapabilities?.supportedHdrTypes?.isNotEmpty() != true -> "当前屏幕未报告 HDR 输出能力"
        else -> "当前窗口无法建立 HDR 渲染表面"
    }

    fun apply(
        activity: Activity,
        enabled: Boolean,
        terminalTextEnabled: Boolean = enabled,
    ): Result<Unit> = runCatching {
        if (enabled) require(isSupported(activity)) { unsupportedReason(activity) }
        val headroom = if (enabled) availableHeadroom(activity) else 1f
        activity.window.colorMode = if (enabled) ActivityInfo.COLOR_MODE_HDR else ActivityInfo.COLOR_MODE_DEFAULT
        if (Build.VERSION.SDK_INT >= 35) {
            activity.window.desiredHdrHeadroom = if (enabled) headroom else 0f
        }
        // The floating navigation indicator needs an HDR-capable parent window on every page,
        // while extended-range terminal glyphs must remain exclusive to the terminal page.
        TerminalRenderer.configureHdrText(enabled && terminalTextEnabled, headroom)
        activity.window.decorView.invalidate()
    }

    private fun availableHeadroom(activity: Activity): Float {
        val display = activity.windowManager.defaultDisplay
        val reported = if (Build.VERSION.SDK_INT >= 36) display.highestHdrSdrRatio else 4f
        return reported.takeIf { it.isFinite() && it > 1f }?.coerceAtMost(4f) ?: 4f
    }
}
