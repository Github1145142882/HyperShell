/*
 * This file is part of HyperShell.
 *
 * The onboarding flow is adapted from HyperCeiler's provision module:
 * https://github.com/ReChronoRain/HyperCeiler/tree/7266aaa0d698ad10795381c5bf23651c2e1719d0/library/provision
 *
 * Copyright (C) 2023-2026 HyperCeiler Contributions
 * Copyright (C) 2026 HyperShell Contributors
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package io.github.hypershell.onboarding

const val CURRENT_ONBOARDING_VERSION: Int = 1

enum class OnboardingStep {
    Welcome,
    Environment,
    BasicSettings,
    Complete;

    fun next(): OnboardingStep = entries.getOrElse(ordinal + 1) { Complete }

    fun previous(): OnboardingStep = entries.getOrElse(ordinal - 1) { Welcome }
}

enum class OnboardingEntry { FirstRun, Replay }

enum class OnboardingThemeSource { Standard, Monet }

enum class OnboardingBrightness { System, Light, Dark }

enum class OnboardingFileLayout { Single, Dual }

enum class OnboardingBottomBarStyle { LiquidGlass, FloatingSolid, StandardNavigation }

enum class OnboardingDefaultEnvironment { Termux, Debian }

enum class RootVerificationState { Idle, Checking, Available, Unavailable }

data class OnboardingPreferences(
    val themeSource: OnboardingThemeSource,
    val brightness: OnboardingBrightness,
    val fileLayout: OnboardingFileLayout,
    val showHiddenFiles: Boolean,
    val scriptUseRoot: Boolean,
    val keepTerminalInBackground: Boolean,
    val showWelcomeOnLaunch: Boolean,
    val bottomBarStyle: OnboardingBottomBarStyle,
    val bottomBarHdrFeedback: Boolean,
    val terminalHdrHighlight: Boolean,
    val defaultEnvironment: OnboardingDefaultEnvironment,
)
