package com.zhuangxiu.ledger

import android.graphics.Bitmap
import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Rule
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 启动真实 Activity/数据库，验证记账、导航和待付筛选，而非仅渲染静态组件。 */
@RunWith(RobolectricTestRunner::class)
@Config(application = LedgerApp::class, sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LedgerFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val settingsTab get() = compose.onNode(
        hasText("设置") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
    )

    private fun awaitText(text: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun capture(name: String) {
        val output = File("build/reports/ui/$name.png")
        requireNotNull(output.parentFile).mkdirs()
        output.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun importMultipleDocumentsRequiresConfirmationAndRefreshesHome() {
        awaitText("生活账本")
        settingsTab.performClick()
        compose.onNodeWithText("数据导入").assertDoesNotExist()
        compose.onNodeWithText("数据管理").performScrollTo().performClick()
        compose.onNodeWithText("数据导入").performScrollTo().performClick()
        awaitText("从鲨鱼记账导入")
        compose.onNodeWithText("选择 CSV 或 XLSX 文件").performClick()
        compose.runOnIdle {
            val file = File(compose.activity.cacheDir, "test-shark.csv")
            file.writeText("日期,收支类型,类别,账户,金额,备注\n" +
                "${java.time.LocalDate.now()},支出,餐饮,现金,12.34,导入测试")
            val other = File(compose.activity.cacheDir, "another-shark.csv")
            other.writeText("金额,类别,日期,收支类型\n2.66,交通,${java.time.LocalDate.now()},支出")
            val duplicate = file.copyTo(File(compose.activity.cacheDir, "copy-shark.csv"), overwrite = true)
            val invalid = File(compose.activity.cacheDir, "invalid.csv").apply { writeText("无法识别") }
            val shadow = Shadows.shadowOf(compose.activity)
            val launched = shadow.nextStartedActivityForResult
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, launched.intent.action)
            assertTrue(launched.intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
            val files = ClipData.newRawUri("账单", Uri.fromFile(file)).apply {
                listOf(other, duplicate, invalid).forEach { addItem(ClipData.Item(Uri.fromFile(it))) }
            }
            shadow.callOnActivityResult(launched.requestCode, Activity.RESULT_OK, Intent().apply { clipData = files })
        }
        val list = compose.onNodeWithTag("import-list")
        awaitText("已跳过 1 个内容完全相同的文件")
        awaitText("以下文件未加入导入")
        list.performScrollToNode(hasText("已选 2 个文件"))
        list.performScrollToNode(hasText("生成预览"))
        compose.onNodeWithText("生成预览").performClick()
        compose.waitUntil(15_000) {
            runCatching { list.performScrollToNode(hasText("确认导入")); true }.getOrDefault(false)
        }
        compose.onNodeWithText("确认导入").performClick()
        awaitText("合并到目标账本？")
        compose.onNodeWithText("导入", useUnmergedTree = true).performClick()
        list.performScrollToIndex(0)
        awaitText("成功 2 笔 · 跳过 0 笔 · 解析失败 0 笔")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("数据管理").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        settingsTab.assertIsSelected()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("本月支出 ¥15.00").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun dataManagementContainsImportAndBackupActions() {
        awaitText("生活账本")
        settingsTab.performClick().assertIsSelected()
        compose.onNodeWithText("数据管理").performScrollTo().performClick()
        compose.onNodeWithText("数据导入").assertIsDisplayed()
        awaitText("保存备份到本地")
        compose.onNodeWithText("从备份恢复").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("导出恢复前安全副本").assertDoesNotExist()
        compose.onNodeWithText("Google Drive 备份").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        settingsTab.assertIsSelected()
    }

    @Test fun settingsOpensFeedbackInsideTheApp() {
        awaitText("生活账本")
        settingsTab.performClick().assertIsSelected()
        compose.onNodeWithText("邮件反馈").assertDoesNotExist()
        compose.onNodeWithText("意见反馈").performScrollTo().performClick()
        compose.onNodeWithText("反馈内容").assertIsDisplayed()
        compose.onNodeWithText("联系方式（选填）").assertIsDisplayed()
        compose.onNodeWithText("提交反馈").assertIsNotEnabled()
        compose.onNodeWithContentDescription("返回").performClick()
        settingsTab.assertIsSelected()
    }

    @Test fun recordRenovationDepositFromHomeThenOpenPendingAndNavigateTabs() {
        awaitText("生活账本")
        compose.onNodeWithText("账本").performClick()
        awaitText("新建账本")
        compose.onNodeWithText("新建账本").performClick()
        compose.onNodeWithText("装修账本").performClick()
        compose.onNodeWithText("账本名称").performTextInput("装修测试")
        compose.onNodeWithText("保存").performScrollTo().performClick()
        awaitText("首页")
        awaitText("装修测试")
        compose.onNodeWithText("首页").performClick()
        awaitText("装修项目")
        compose.onNodeWithText("记一笔", useUnmergedTree = true).performClick()
        awaitText("记装修账")
        awaitText("装修阶段")
        compose.onNodeWithText("餐饮").assertDoesNotExist()
        compose.onNodeWithContentDescription("返回").performClick()
        awaitText("装修项目")
        compose.onNodeWithContentDescription("切换账本").performClick()
        compose.onNodeWithText("全部账本").performClick()
        awaitText("全部账本")
        compose.onNodeWithText("记一笔", useUnmergedTree = true).performClick()
        awaitText("记到哪个账本？")
        compose.onNodeWithText("装修测试").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("账单总金额").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("账单总金额").performClick()
        enterRenovationAmount("12000+800=")
        compose.onNodeWithText("定金").performScrollTo().performClick()
        compose.onNodeWithText("已付定金").performScrollTo().performClick()
        enterRenovationAmount("1000−200=")
        compose.onNodeWithText("剩余待付 ¥12,000.00").assertExists()
        capture("edit")
        compose.onNodeWithContentDescription("保存账单").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("本月支出 ¥800.00").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("切换账本").performClick()
        compose.onNodeWithText("装修测试").performClick()
        awaitText("装修项目")
        compose.onNodeWithText("装修项目").performClick()
        awaitText("剩余待付")
        compose.onNodeWithText("剩余待付").performClick()
        awaitText("仅看待付")
        awaitText("共 1 笔，合计 ¥12,800.00")
        compose.onNodeWithText("仅看待付").assertIsOn()
        capture("bills")
        compose.onNodeWithText("统计").performClick()
        awaitText("本月支出")
        capture("stats")
        settingsTab.performClick().assertIsSelected()
        awaitText("数据与备份")
        capture("mine")
        compose.onNodeWithText("检查更新").performScrollTo().performClick()
        awaitText("当前版本 ${BuildConfig.VERSION_NAME}")
        compose.onNodeWithText("检查新版本").assertIsDisplayed()
        capture("update-idle")
        compose.onNodeWithText("设置 GitHub 访问").performScrollTo().performClick()
        awaitText("连接私有 GitHub 仓库")
        compose.runOnIdle { assertTrue(compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0) }
        compose.onNodeWithText("保存并检查").assertIsNotEnabled()
        compose.onNodeWithText("取消").performScrollTo().performClick()
        awaitText("检查新版本")
        compose.runOnIdle { assertTrue(compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE == 0) }
    }

    private fun enterRenovationAmount(amount: String) {
        amount.forEach { key ->
            val description = when (key) {
                '+' -> "加"
                '−' -> "减"
                '=' -> "等于"
                else -> "数字键$key"
            }
            compose.onNodeWithContentDescription(description, useUnmergedTree = true).performClick()
        }
        compose.onNodeWithText("完成").performClick()
    }
}
