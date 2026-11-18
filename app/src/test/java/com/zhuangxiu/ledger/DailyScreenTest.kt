package com.zhuangxiu.ledger

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.zhuangxiu.ledger.data.db.*
import com.zhuangxiu.ledger.domain.DailyStatistics
import com.zhuangxiu.ledger.ui.daily.*
import com.zhuangxiu.ledger.ui.navigation.*
import com.zhuangxiu.ledger.ui.theme.RenovationLedgerTheme
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
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
    fun monthlyActionsAndDockFitSmallScreenWithLargeText() {
        val current = YearMonth.now()
        val book = BookEntity(1, "用于验证小屏长名称截断的生活账本测试", BookKind.DAILY)
        var added = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                RenovationLedgerTheme(darkTheme = false) {
                    var month by remember { mutableStateOf(current) }
                    DailyHomeScreen(
                        DailyState(month, DailyStatistics.calculate(emptyList(), book.id, month)),
                        { BookHeader(BookState(listOf(book), book), {}, {}) },
                        { LedgerBottomBar(Routes.HOME, {}) },
                        { month = month.plusMonths(it) }, { month = current }, { added = true }, {},
                    )
                }
            }
        }
        val previous = compose.onNodeWithContentDescription("上个月")
        val next = compose.onNodeWithContentDescription("下个月")
        previous.assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        next.assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        assertTrue(previous.fetchSemanticsNode().boundsInRoot.right <= next.fetchSemanticsNode().boundsInRoot.left)
        compose.onNodeWithContentDescription("设置").assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        compose.onNodeWithText("本月").assertDoesNotExist()
        previous.performTouchInput { click(Offset(2f, center.y)) }
        compose.onNodeWithText("本月").assertIsDisplayed().performClick()
        compose.onNodeWithText("本月").assertDoesNotExist()
        val add = compose.onNodeWithText("记一笔", useUnmergedTree = true)
        val home = compose.onNodeWithText("首页")
        assertTrue(add.fetchSemanticsNode().boundsInRoot.bottom < home.fetchSemanticsNode().boundsInRoot.top)
        add.performClick()
        assertTrue(added)
        capture("ux-home-light-320")
    }

    @Test @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun dockCommitsOnlyLastTabOnRelease() {
        val visited = mutableListOf<String>()
        compose.setContent {
            RenovationLedgerTheme {
                LedgerBottomBar(Routes.HOME, { visited.add(it) })
            }
        }
        val home = compose.onNodeWithText("首页")
        val stats = compose.onNodeWithText("统计")
        val books = compose.onNodeWithText("账本")
        val statsOffset = stats.fetchSemanticsNode().boundsInRoot.center - home.fetchSemanticsNode().boundsInRoot.center
        val booksOffset = books.fetchSemanticsNode().boundsInRoot.center - home.fetchSemanticsNode().boundsInRoot.center
        home.performTouchInput {
            down(center)
            moveTo(center + statsOffset)
            moveTo(center + booksOffset)
        }
        compose.runOnIdle { assertTrue(visited.isEmpty()) }
        home.performTouchInput { up() }
        compose.runOnIdle { assertEquals(listOf(Routes.BOOKS), visited) }
        stats.performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(listOf(Routes.BOOKS, Routes.STATS), visited) }
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
    @Test fun amountUsesOnlyCustomNumberPadAndNotesCanStillBeTyped() {
        compose.setContent { RenovationLedgerTheme {
            var state by remember { mutableStateOf(DailyEditState(loading = false)) }
            DailyEditContent(state, false, {}, { state = it(state) }, {}, {}, {})
        } }
        val amount = compose.onNodeWithContentDescription("日常记账金额")
        amount.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetText)).performClick()
        compose.onNodeWithContentDescription("数字键1", useUnmergedTree = true).assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("数字键.", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("数字键2", useUnmergedTree = true).performClick()
        amount.assertTextContains("1.2")
        compose.onNodeWithContentDescription("保存日常账目").assertIsEnabled()
        compose.onNodeWithText("备注（可选）").performScrollTo().performClick().performTextInput("午餐")
        compose.onNodeWithContentDescription("数字键1", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("完成备注")
            .assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp).performClick()
        compose.onNodeWithContentDescription("数字键1", useUnmergedTree = true).assertIsDisplayed()
        amount.assertTextContains("1.2")

        val four = compose.onNodeWithContentDescription("数字键4", useUnmergedTree = true)
        val six = compose.onNodeWithContentDescription("数字键6", useUnmergedTree = true)
        val distance = six.fetchSemanticsNode().boundsInRoot.center - four.fetchSemanticsNode().boundsInRoot.center
        four.performTouchInput { swipe(center, center + distance) }
        amount.assertTextContains("1.26")
    }
}
