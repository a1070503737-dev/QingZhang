package com.zhuangxiu.ledger

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.zhuangxiu.ledger.feedback.FeedbackClient
import com.zhuangxiu.ledger.ui.mine.FeedbackScreen
import com.zhuangxiu.ledger.ui.mine.FeedbackViewModel
import com.zhuangxiu.ledger.ui.theme.RenovationLedgerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeedbackScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun anonymousSubmissionKeepsDraftUntilTheServerConfirms() {
        val confirmation = "已收到你的反馈，谢谢！"
        val rejected = FormConnection("<script>var confirmation = '$confirmation';</script><p>请填写</p>")
        val accepted = FormConnection("<main>$confirmation</main>")
        val attempts = AtomicInteger()
        val model = FeedbackViewModel(FeedbackClient { url ->
            assertEquals(BuildConfig.FEEDBACK_SUBMIT_URL, url)
            if (attempts.getAndIncrement() == 0) rejected else accepted
        })
        compose.setContent {
            RenovationLedgerTheme {
                FeedbackScreen(onBack = {}, model = model)
            }
        }

        val message = "希望改进统计 & 筛选"
        val submit = compose.onNodeWithText("提交反馈").performScrollTo()
        submit.assertIsNotEnabled()
        compose.onNodeWithText("反馈内容").performTextInput(message)
        submit.performClick()
        val warning = compose.onNodeWithText("未能确认提交，请检查网络后重试。")
        compose.waitUntil(5_000) { runCatching { warning.assertExists(); true }.getOrDefault(false) }
        compose.onNodeWithText(message).assertIsDisplayed()

        submit.performScrollTo().performClick()
        val submitted = compose.onNodeWithText("反馈已提交")
        compose.waitUntil(5_000) { runCatching { submitted.assertExists(); true }.getOrDefault(false) }
        submitted.assertIsDisplayed()
        assertEquals(2, attempts.get())
        assertEquals("POST", accepted.requestMethod)
        assertFalse(accepted.instanceFollowRedirects)
        val body = URLDecoder.decode(accepted.posted.toString("UTF-8"), "UTF-8")
        assertEquals("entry.${BuildConfig.FEEDBACK_CONTENT_ENTRY}=$message&fvv=1&pageHistory=0", body)
    }

    private class FormConnection(val html: String) : HttpURLConnection(
        URI(BuildConfig.FEEDBACK_SUBMIT_URL).toURL(),
    ) {
        val posted = ByteArrayOutputStream()
        override fun getOutputStream() = posted
        override fun getInputStream() = html.byteInputStream()
        override fun getResponseCode() = HTTP_OK
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
    }
}
