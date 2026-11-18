package com.zhuangxiu.ledger

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 真正启动 Activity、Hilt、Room，以界面操作完成 CRUD、重建、账本切换。 */
@RunWith(RobolectricTestRunner::class)
@Config(application = LedgerApp::class, sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DailyFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun awaitText(text: String) { compose.waitUntil(20000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun awaitDescription(text: String) { compose.waitUntil(20000) { compose.onAllNodesWithContentDescription(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun enterAmount(amount: String) {
        amount.forEach { compose.onNodeWithContentDescription("数字键$it", useUnmergedTree = true).performClick() }
    }
    private fun capture(name: String) {
        val file = File("build/reports/ui/$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun dailyCrudStatisticsRestartAndBookSwitch() {
        awaitText("生活账本")
        awaitDescription("本月结余 ¥0.00")
        capture("qingzhang-empty")
        compose.onNodeWithText("记一笔", useUnmergedTree = true).performClick()
        awaitDescription("日常记账金额")
        compose.onNodeWithText("餐饮").assertExists()
        compose.onNodeWithText("装修阶段").assertDoesNotExist()
        compose.onNodeWithText("付款状态").assertDoesNotExist()
        compose.onNodeWithContentDescription("保存日常账目").assertIsNotEnabled()
        enterAmount("25.50")
        capture("qingzhang-quick-entry")
        compose.onNodeWithContentDescription("保存日常账目").performClick()
        awaitDescription("本月支出 ¥25.50")
        compose.onNodeWithText("记一笔", useUnmergedTree = true).performClick()
        awaitDescription("日常记账金额")
        compose.onNodeWithText("收入", useUnmergedTree = true).performClick()
        enterAmount("100")
        compose.onNodeWithText("支出", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("日常记账金额").assertTextContains("100")
        compose.onNodeWithText("收入", useUnmergedTree = true).performClick()
        compose.onNodeWithText("账本 · 生活账本").assertExists()
        compose.onNodeWithContentDescription("保存日常账目").performClick()
        awaitDescription("本月结余 ¥74.50")
        capture("qingzhang-home")
        val homeTab = compose.onNodeWithText("首页")
        val statsTab = compose.onNodeWithText("统计")
        val distance = statsTab.fetchSemanticsNode().boundsInRoot.center - homeTab.fetchSemanticsNode().boundsInRoot.center
        homeTab.performTouchInput { swipe(center, center + distance) }
        awaitText("收支日历")
        statsTab.assertIsSelected()
        val statsList = compose.onNode(hasScrollToIndexAction())
        statsList.performScrollToNode(hasText("当日收支明细"))
        compose.onNodeWithText("收入 +¥100.00").assertExists()
        compose.onNodeWithText("支出 -¥25.50").assertExists()
        compose.onNodeWithText("净额 +¥74.50").assertExists()
        capture("qingzhang-stats")
        statsList.performScrollToNode(hasText("支出分类"))
        val calendarEntry = hasText("餐饮") and hasClickAction()
        statsList.performScrollToNode(calendarEntry)
        compose.onNode(calendarEntry).performClick()
        awaitText("编辑账目")
        awaitDescription("日常记账金额")
        repeat(4) { compose.onNodeWithContentDescription("退格", useUnmergedTree = true).performClick() }
        enterAmount("20")
        compose.onNodeWithContentDescription("保存日常账目").performClick()
        awaitText("统计")
        statsTab.assertIsSelected()
        homeTab.performClick()
        awaitDescription("本月结余 ¥80.00")
        compose.activityRule.scenario.recreate()
        awaitDescription("本月结余 ¥80.00")
        compose.onNodeWithText("餐饮").performScrollTo().performClick()
        awaitText("编辑账目")
        compose.onNodeWithContentDescription("删除账目").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithContentDescription("删除账目").performClick()
        compose.onNodeWithText("删除", useUnmergedTree = true).performClick()
        awaitDescription("本月结余 ¥100.00")
        compose.onNodeWithContentDescription("切换账本").performClick()
        compose.onNodeWithText("全部账本").performClick()
        awaitDescription("本月结余 ¥100.00")
        compose.activityRule.scenario.recreate()
        awaitText("全部账本")
        compose.onNodeWithText("记一笔", useUnmergedTree = true).performClick()
        awaitText("记到哪个账本？")
        compose.onNodeWithText("生活账本").performClick()
        awaitDescription("日常记账金额")
        compose.onNodeWithText("餐饮").assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
        awaitText("全部账本")
        compose.onNodeWithContentDescription("切换账本").performClick()
        compose.onNodeWithText("生活账本").performClick()
        awaitDescription("本月结余 ¥100.00")
        compose.onNodeWithText("账本").performClick()
        compose.onNodeWithContentDescription("编辑生活账本").assertWidthIsEqualTo(48.dp).performClick()
        compose.onNodeWithText("归档账本").performScrollTo().performClick().assertIsOn()
        compose.onNodeWithText("保存").performScrollTo().performClick()
        awaitText("恢复使用")
        compose.onNodeWithText("普通账本 · 已归档 · 查看历史").performClick()
        awaitText("已归档 · 历史明细。恢复使用后可继续记账。")
        awaitDescription("本月结余 ¥100.00")
        compose.onNodeWithText("记一笔", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("账本").performClick()
        compose.onNodeWithText("恢复使用").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("恢复使用").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("生活账本").performClick()
        awaitDescription("本月结余 ¥100.00")

    }
}
