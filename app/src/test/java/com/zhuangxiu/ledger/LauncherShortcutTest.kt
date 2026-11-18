package com.zhuangxiu.ledger

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(application = LedgerApp::class, sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherShortcutTest {
    @get:Rule val compose = androidx.compose.ui.test.junit4.v2.createEmptyComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun intent(shortcut: LedgerShortcut) =
        Intent(context, MainActivity::class.java).setAction(shortcut.action)

    private fun openShortcut(scenario: ActivityScenario<MainActivity>, shortcut: LedgerShortcut) {
        scenario.onActivity {
            InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(it, intent(shortcut))
        }
    }

    private fun awaitText(text: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun awaitDescription(description: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun declaredShortcutsForwardToExistingTaskWithoutClearTask() {
        val main = ComponentName(context, MainActivity::class.java)
        val actions = mutableListOf<String>()
        val android = "http://schemas.android.com/apk/res/android"
        context.resources.getXml(R.xml.shortcuts).use { xml ->
            while (xml.next() != XmlPullParser.END_DOCUMENT) {
                if (xml.eventType == XmlPullParser.START_TAG && xml.name == "intent") {
                    assertEquals(context.packageName, xml.getAttributeValue(android, "targetPackage"))
                    assertEquals(LauncherShortcutActivity::class.java.name, xml.getAttributeValue(android, "targetClass"))
                    actions += requireNotNull(xml.getAttributeValue(android, "action"))
                }
            }
        }
        assertEquals(LedgerShortcut.entries.map { it.action }, actions)
        for (action in actions) {
            val entry = Intent(context, LauncherShortcutActivity::class.java).setAction(action)
            val forwarder = Robolectric.buildActivity(LauncherShortcutActivity::class.java, entry).create().get()
            val forwarded = Shadows.shadowOf(forwarder).nextStartedActivity
            assertEquals(main, forwarded.component)
            assertEquals(action, forwarded.action)
            assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP, forwarded.flags)
            assertTrue(forwarder.isFinishing)
        }
    }

    @Test fun newEntryOpensFromColdStartAndReusesAnOpenDraft() {
        ActivityScenario.launch<MainActivity>(intent(LedgerShortcut.NEW_ENTRY)).use { scenario ->
            awaitDescription("日常记账金额")
            compose.onNodeWithContentDescription("数字键7", useUnmergedTree = true).performClick()
            openShortcut(scenario, LedgerShortcut.NEW_ENTRY)
            compose.onNodeWithContentDescription("日常记账金额").assertTextContains("7")
            compose.onNodeWithContentDescription("返回").performClick()
            awaitText("记一笔")
            compose.onNodeWithContentDescription("日常记账金额").assertDoesNotExist()
        }
    }

    @Test fun statisticsOpensOverDraftAndAllBooksShortcutPromptsForTarget() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitText("生活账本")
            compose.onNodeWithContentDescription("切换账本").performClick()
            compose.onNodeWithText("全部账本").performClick()
            awaitText("全部账本")
            openShortcut(scenario, LedgerShortcut.NEW_ENTRY)
            awaitText("记到哪个账本？")
            compose.onNodeWithText("生活账本").performClick()
            awaitDescription("日常记账金额")
            compose.onNodeWithContentDescription("数字键8", useUnmergedTree = true).performClick()
            openShortcut(scenario, LedgerShortcut.STATISTICS)
            awaitText("收支日历")
            compose.onNodeWithText("统计").assertIsSelected()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            awaitDescription("日常记账金额")
            compose.onNodeWithContentDescription("日常记账金额").assertTextContains("8")
        }
    }
}
