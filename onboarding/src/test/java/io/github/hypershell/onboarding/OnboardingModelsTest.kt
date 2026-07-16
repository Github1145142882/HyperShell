/*
 * Copyright (C) 2026 HyperShell Contributors
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package io.github.hypershell.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingModelsTest {
    @Test
    fun stepsAdvanceAndClampAtBoundaries() {
        assertEquals(OnboardingStep.Environment, OnboardingStep.Welcome.next())
        assertEquals(OnboardingStep.BasicSettings, OnboardingStep.Environment.next())
        assertEquals(OnboardingStep.Complete, OnboardingStep.BasicSettings.next())
        assertEquals(OnboardingStep.Complete, OnboardingStep.Complete.next())
    }

    @Test
    fun stepsGoBackAndClampAtWelcome() {
        assertEquals(OnboardingStep.Welcome, OnboardingStep.Welcome.previous())
        assertEquals(OnboardingStep.Welcome, OnboardingStep.Environment.previous())
        assertEquals(OnboardingStep.Environment, OnboardingStep.BasicSettings.previous())
        assertEquals(OnboardingStep.BasicSettings, OnboardingStep.Complete.previous())
    }
}
