package io.github.hypershell

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.espresso.Espresso.pressBack
import org.junit.Rule
import org.junit.Test

class HyperShellUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun filesAreDefaultAndRootHasNoExtraConfirmation() {
        composeRule.onNodeWithText("搜索").assertIsDisplayed()
        composeRule.onNodeWithText("验证 Root 文件访问？").assertDoesNotExist()

        composeRule.onNodeWithText("终端").performClick()
        composeRule.onNodeWithText("普通").assertIsDisplayed()
        composeRule.onNodeWithText("Root").assertIsDisplayed()
        composeRule.onNodeWithText("启动 Root 终端？").assertDoesNotExist()
    }

    @Test
    fun settingsOpensAppearanceSubpage() {
        composeRule.onNodeWithText("设置").performClick()
        composeRule.onAllNodes(hasScrollAction())[0].performScrollToIndex(2)
        composeRule.onNodeWithText("外观").performClick()
        composeRule.onNodeWithText("配色来源").assertIsDisplayed()
        composeRule.onNodeWithText("终端字体大小").assertIsDisplayed()
        pressBack()
        composeRule.onNodeWithText("PTY 滚屏").assertIsDisplayed()
    }
}
