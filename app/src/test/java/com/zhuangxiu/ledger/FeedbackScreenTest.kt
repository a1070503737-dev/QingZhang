package com.zhuangxiu.ledger

import android.app.Application
import android.content.ClipboardManager
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.zhuangxiu.ledger.ui.mine.MineScreen
import com.zhuangxiu.ledger.ui.theme.RenovationLedgerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeedbackScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun anonymousFeedbackOpensTheFormAndCanCopyTheLinkWithoutABrowser() {
        val formUrl = "https://docs.google.com/forms/d/e/test-feedback-form/viewform"
        compose.setContent {
            RenovationLedgerTheme {
                MineScreen(bottomBar = {}, onNavigate = {}, feedbackFormUrl = formUrl)
            }
        }

        val feedback = compose.onNodeWithText("意见反馈").performScrollTo().assertIsDisplayed()
        feedback.performClick()
        compose.runOnIdle {
            val intent = requireNotNull(Shadows.shadowOf(compose.activity).nextStartedActivity)
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals(formUrl, intent.dataString)
            assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
        }

        val application = Shadows.shadowOf(compose.activity.application)
        compose.runOnIdle { application.checkActivities(true) }
        try {
            feedback.performClick()
            compose.onNodeWithText(formUrl, substring = true).assertIsDisplayed()
            compose.onNodeWithText("复制链接").performClick()
            compose.runOnIdle {
                val clipboard = requireNotNull(compose.activity.getSystemService(ClipboardManager::class.java))
                assertEquals(formUrl, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
            }
        } finally {
            compose.runOnIdle { application.checkActivities(false) }
        }
    }
}
