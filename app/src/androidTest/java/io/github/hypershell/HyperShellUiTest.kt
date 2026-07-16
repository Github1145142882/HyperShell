package io.github.hypershell

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.espresso.Espresso.pressBack
import org.junit.Rule
import org.junit.Test

class HyperShellUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun onboardingCompletesBeforeTerminalAndRootHasNoExtraConfirmation() {
        completeOnboardingIfNeeded()
        composeRule.onNodeWithText("普通").assertIsDisplayed()
        composeRule.onNodeWithText("Root").assertIsDisplayed()

        composeRule.onNodeWithText("文件").performClick()
        composeRule.onNodeWithText("验证 Root 文件访问？").assertDoesNotExist()
        composeRule.onNodeWithText("启动 Root 终端？").assertDoesNotExist()
    }

    @Test
    fun settingsOpensAppearanceSubpage() {
        completeOnboardingIfNeeded()
        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithText("外观").performScrollTo().performClick()
        composeRule.onNodeWithText("配色来源").assertIsDisplayed()
        composeRule.onNodeWithText("终端字体预设").assertIsDisplayed()
        pressBack()
        composeRule.onNodeWithText("首次启动引导").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settingsCanReplayOnboarding() {
        completeOnboardingIfNeeded()
        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithText("首次启动引导").performScrollTo().performClick()
        composeRule.onNodeWithText("终端与 Root 文件管理").assertIsDisplayed()
    }

    private fun completeOnboardingIfNeeded() {
        composeRule.waitForIdle()
        if (composeRule.onAllNodesWithContentDescription("开始设置").fetchSemanticsNodes().isEmpty()) return
        composeRule.onAllNodesWithContentDescription("开始设置")[0].performClick()
        composeRule.onNodeWithText("运行环境").assertIsDisplayed()
        composeRule.onNodeWithText("继续").performClick()
        composeRule.onNodeWithText("基础设置").assertIsDisplayed()
        composeRule.onNodeWithText("继续").performClick()
        composeRule.onNodeWithText("设置完毕").assertIsDisplayed()
        composeRule.onNodeWithText("开始使用").performClick()
        composeRule.waitForIdle()
    }
}
