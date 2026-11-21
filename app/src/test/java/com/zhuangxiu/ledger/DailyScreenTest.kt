package com.zhuangxiu.ledger

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.zhuangxiu.ledger.data.db.*
import com.zhuangxiu.ledger.domain.DailyStatistics
import com.zhuangxiu.ledger.ui.components.AmountKeypad
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
    fun homeGroupsEntriesByDayAndTotalsTheVisibleRows() {
        val month = YearMonth.now()
        val recent = month.atDay(16)
        val earlier = recent.minusDays(1)
        val book = BookEntity(1, "生活账本", BookKind.DAILY)
        val rows = listOf(
            DailyEntryEntity("older", book.id, 123, EntryKind.EXPENSE, DailyCategory.SHOPPING, earlier.toEpochDay(), 660, PayMethod.ALIPAY),
            DailyEntryEntity("lunch", book.id, 1204, EntryKind.EXPENSE, DailyCategory.FOOD, recent.toEpochDay(), 720, PayMethod.ALIPAY, note = "午餐"),
            DailyEntryEntity("travel", book.id, 896, EntryKind.EXPENSE, DailyCategory.TRANSPORT, recent.toEpochDay(), 830, PayMethod.CASH),
            DailyEntryEntity("salary", book.id, 12345, EntryKind.INCOME, DailyCategory.SALARY, recent.toEpochDay(), 840, PayMethod.CASH),
        )
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                RenovationLedgerTheme {
                    DailyHomeScreen(
                        DailyState(month, DailyStatistics.calculate(rows, book.id, month)),
                        { BookHeader(BookState(listOf(book), book), {}, {}) },
                        {}, {}, {}, {}, {},
                    )
                }
            }
        }
        val list = compose.onNode(hasScrollToIndexAction())
        val recentSummary = hasText(recent.toString()) and hasText("支出 ¥21.00") and hasText("收入 ¥123.45")
        val earlierSummary = hasText(earlier.toString()) and hasText("支出 ¥1.23") and hasText("收入 ¥0.00")
        list.performScrollToNode(earlierSummary)
        compose.onNode(earlierSummary).assertIsDisplayed()
        list.performScrollToNode(recentSummary)
        compose.onNode(recentSummary).assertIsDisplayed()
        capture("qingzhang-daily-group-totals")

        val search = hasContentDescription("搜索本月账目、分类或备注")
        list.performScrollToNode(search)
        compose.onNode(search).performTextInput("午餐")
        val filtered = hasText(recent.toString()) and hasText("支出 ¥12.04") and hasText("收入 ¥0.00")
        list.performScrollToNode(filtered)
        compose.onNode(filtered).assertIsDisplayed()
        compose.onNode(earlierSummary).assertDoesNotExist()
    }

    @Test fun homeUsesRedIncomeGreenExpenseAndBalanceColorBySign() {
        val month = YearMonth.now()
        val day = month.atDay(1).toEpochDay()
        val book = BookEntity(1, "生活账本", BookKind.DAILY)
        val income = DailyEntryEntity("income", book.id, 10_000L, EntryKind.INCOME, DailyCategory.SALARY, day, 660, PayMethod.ALIPAY)
        val expense = DailyEntryEntity("expense", book.id, 2_500L, EntryKind.EXPENSE, DailyCategory.SHOPPING, day, 661, PayMethod.ALIPAY)
        var rows by mutableStateOf(listOf(income, expense))
        compose.setContent {
            RenovationLedgerTheme(dynamicColor = false) {
                DailyHomeScreen(
                    DailyState(month, DailyStatistics.calculate(rows, book.id, month)),
                    { BookHeader(BookState(listOf(book), book), {}, {}) },
                    { LedgerBottomBar(Routes.HOME, {}) }, {}, {}, {}, {},
                )
            }
        }

        fun assertTextColor(node: SemanticsNodeInteraction, color: Int) {
            val image = node.assertIsDisplayed().captureToImage().asAndroidBitmap()
            val pixels = IntArray(image.width * image.height)
            image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
            assertTrue("Expected text to contain ${color.toUInt().toString(16)}", color in pixels)
        }

        val red = 0xFFB42324.toInt()
        val green = 0xFF12653E.toInt()
        assertTextColor(compose.onNodeWithContentDescription("本月收入 ¥100.00"), red)
        assertTextColor(compose.onNodeWithContentDescription("本月支出 ¥25.00"), green)
        assertTextColor(compose.onNodeWithContentDescription("本月结余 ¥75.00"), red)
        compose.runOnIdle { rows = listOf(expense) }
        assertTextColor(compose.onNodeWithContentDescription("本月结余 -¥25.00"), green)
        compose.runOnIdle { rows = emptyList() }
        assertTextColor(compose.onNodeWithContentDescription("本月结余 ¥0.00"), 0xFF111C2D.toInt())
    }

    @Test fun darkEmptyHomeHasBlackBackgroundAndRoomAboveRecordButton() {
        val month = YearMonth.now()
        val book = BookEntity(1, "生活账本", BookKind.DAILY)
        compose.setContent {
            RenovationLedgerTheme(darkTheme = true) {
                DailyHomeScreen(
                    DailyState(month, DailyStatistics.calculate(emptyList(), book.id, month)),
                    { BookHeader(BookState(listOf(book), book), {}, {}) },
                    { LedgerBottomBar(Routes.HOME, {}) },
                    {}, {}, {}, {},
                )
            }
        }
        val empty = compose.onNodeWithText("本月还没有账目").assertIsDisplayed()
        val add = compose.onNodeWithText("记一笔", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(empty.fetchSemanticsNode().boundsInRoot.bottom < add.fetchSemanticsNode().boundsInRoot.top)
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        assertEquals(android.graphics.Color.BLACK, image.getPixel(0, image.height / 2))
        capture("qingzhang-home-pure-black-empty")
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
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToIndex(4)
        val empty = compose.onNodeWithText("本月还没有账目").assertIsDisplayed()
        assertTrue(empty.fetchSemanticsNode().boundsInRoot.bottom < add.fetchSemanticsNode().boundsInRoot.top)
        add.performClick()
        assertTrue(added)
        capture("ux-home-light-320")
    }

    @Test
    fun monthSwipeChangesOneMonthOnReleaseAndCalendarStillSelectsDates() {
        val initial = YearMonth.of(2025, 1)
        val book = BookEntity(1, "生活账本", BookKind.DAILY)
        var month by mutableStateOf(initial)
        var statistics by mutableStateOf(false)
        val deltas = mutableListOf<Long>()
        compose.setContent {
            RenovationLedgerTheme {
                val state = DailyState(month, DailyStatistics.calculate(emptyList(), book.id, month))
                val header: @Composable () -> Unit = { BookHeader(BookState(listOf(book), book), {}, {}) }
                val changeMonth: (Long) -> Unit = { delta ->
                    deltas.add(delta)
                    month = month.plusMonths(delta)
                }
                if (statistics) {
                    DailyStatsScreen(state, header, {}, changeMonth, {})
                } else {
                    DailyHomeScreen(state, header, {}, changeMonth, {}, {}, {})
                }
            }
        }

        val monthPicker = compose.onNodeWithContentDescription("选择月份")
        monthPicker.performTouchInput {
            down(center)
            moveTo(center - Offset(120.dp.toPx(), 0f))
        }
        compose.onNodeWithText("2 月").assertIsDisplayed()
        capture("qingzhang-month-swipe-preview")
        compose.runOnIdle { assertEquals(emptyList<Long>(), deltas) }
        monthPicker.performTouchInput { up() }
        compose.onNodeWithText("2025年2月").assertIsDisplayed()
        compose.onNodeWithText("2 月").assertDoesNotExist()
        monthPicker.performTouchInput { swipe(center, center + Offset(35.dp.toPx(), 0f)) }
        compose.onNodeWithText("2025年2月").assertIsDisplayed()
        compose.onNodeWithText("1 月").assertDoesNotExist()
        monthPicker.performTouchInput {
            swipe(center - Offset(32.dp.toPx(), 0f), center + Offset(110.dp.toPx(), 0f))
        }
        compose.onNodeWithText("2025年1月").assertIsDisplayed()
        compose.runOnIdle { statistics = true }
        monthPicker.performTouchInput { swipe(center, center - Offset(116.dp.toPx(), 0f)) }
        compose.onNodeWithText("2025年2月").assertIsDisplayed()
        val nextDay = compose.onNodeWithContentDescription("2月12日 0.00")
        nextDay.performClick()
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("(2025.02.12)"))
        compose.onNodeWithText("(2025.02.12)").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(1L, -1L, 1L), deltas) }
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
        val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(home.fetchSemanticsNode().boundsInRoot.left > screen.left)
        assertTrue(books.fetchSemanticsNode().boundsInRoot.right < screen.right)
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

    @Test
    fun floatingDockLetsContentShowThroughItsSurface() {
        compose.setContent {
            RenovationLedgerTheme(dynamicColor = false) {
                Box(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxHeight().background(Color.Red))
                        Box(Modifier.weight(1f).fillMaxHeight().background(Color.Blue))
                    }
                    LedgerBottomBar(Routes.HOME, {}, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val stats = compose.onNodeWithText("统计").fetchSemanticsNode().boundsInRoot
        val y = stats.center.y.toInt()
        val left = image.getPixel(image.width * 5 / 12, y)
        val right = image.getPixel(image.width * 7 / 12, y)
        assertTrue(android.graphics.Color.red(left) > android.graphics.Color.red(right))
        assertTrue(android.graphics.Color.blue(right) > android.graphics.Color.blue(left))
        assertTrue(android.graphics.Color.green(left) > 128 && android.graphics.Color.green(right) > 128)
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
        capture("qingzhang-quick-small-large-text")
        val key = compose.onNodeWithContentDescription("数字键1", useUnmergedTree = true)
        val keyBounds = key.fetchSemanticsNode().boundsInRoot
        val date = compose.onNodeWithContentDescription("记账日期").performScrollTo().assertIsDisplayed()
        assertTrue(date.fetchSemanticsNode().boundsInRoot.bottom <= keyBounds.top)
        assertEquals(keyBounds, key.fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithContentDescription("保存日常账目").assertIsDisplayed().performClick()
        assertTrue(saved)
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

    @Test fun keypadPressChangesOnlyTheTouchedKeyAndInputsOnce() {
        val entered = mutableListOf<String>()
        compose.setContent {
            RenovationLedgerTheme(darkTheme = true) {
                Box(Modifier.padding(24.dp)) {
                    AmountKeypad { entered.add(it) }
                }
            }
        }
        val key = compose.onNodeWithContentDescription("数字键1")
        val bounds = key.fetchSemanticsNode().boundsInRoot
        val interiorX = (bounds.left + bounds.width / 4f).toInt()
        val gapX = bounds.right.toInt() + 3
        val centerY = bounds.center.y.toInt()
        val before = compose.onRoot().captureToImage().asAndroidBitmap()

        key.performTouchInput { down(Offset(width - 4f, center.y)) }
        val pressed = compose.onRoot().captureToImage().asAndroidBitmap()
        assertEquals(bounds, key.fetchSemanticsNode().boundsInRoot)
        assertTrue(before.getPixel(interiorX, centerY) != pressed.getPixel(interiorX, centerY))
        assertEquals(before.getPixel(gapX, centerY), pressed.getPixel(gapX, centerY))
        assertTrue(entered.isEmpty())
        key.performTouchInput { up() }
        compose.runOnIdle { assertEquals(listOf("1"), entered) }
    }
}
