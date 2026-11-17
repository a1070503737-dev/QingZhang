package com.zhuangxiu.ledger

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.zhuangxiu.ledger.data.db.*
import com.zhuangxiu.ledger.domain.DailyStatistics
import com.zhuangxiu.ledger.ui.daily.*
import com.zhuangxiu.ledger.ui.navigation.*
import com.zhuangxiu.ledger.ui.theme.RenovationLedgerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DailyScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun capture(name: String) {
        val file = File("build/reports/ui/$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun darkHomeUsesActualSummary() {
        val month = YearMonth.of(2026, 11)
        val rows = listOf(DailyEntryEntity("1", 1, 19900, EntryKind.EXPENSE, DailyCategory.SHOPPING, month.atDay(1).toEpochDay(), 660, PayMethod.ALIPAY))
        compose.setContent { RenovationLedgerTheme(darkTheme = true) {
            DailyHomeScreen(DailyState(month, DailyStatistics.calculate(rows, 1, month)),
                { BookHeader(BookState(listOf(BookEntity(1, "生活账本", BookKind.DAILY)), BookEntity(1, "生活账本", BookKind.DAILY)), {}, {}) },
                { LedgerBottomBar(Routes.HOME, {}) }, {}, {}, {}, {})
        } }
        compose.onNodeWithContentDescription("本月支出 ¥199.00").assertIsDisplayed()
        capture("qingzhang-home-dark")
    }
    @Test @Config(qualifiers = "w320dp-h740dp-xhdpi")
    fun smallScreenLargeFontCanSaveAndSelectCategory() {
        var saved = false
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
            RenovationLedgerTheme {
                var state by remember { mutableStateOf(DailyEditState(amount = "12.50", loading = false)) }
                DailyEditContent(state, false, {}, { state = it(state) }, { state = state.copy(kind = it) }, { saved = true }, {})
            }
        } }
        compose.onNodeWithText("交通").performScrollTo().performClick()
        compose.onNodeWithContentDescription("保存日常账目").assertIsDisplayed().performClick()
        assertTrue(saved)
        capture("qingzhang-quick-small-large-text")
    }
    @Test fun customNumberPadChangesExactInput() {
        compose.setContent { RenovationLedgerTheme {
            var state by remember { mutableStateOf(DailyEditState(loading = false)) }
            DailyEditContent(state, false, {}, { state = it(state) }, {}, {}, {})
        } }
        compose.onNodeWithContentDescription("数字键1", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("数字键.", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("数字键2", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("日常记账金额").assertTextContains("1.2")
        compose.onNodeWithContentDescription("保存日常账目").assertIsEnabled()
    }
}
