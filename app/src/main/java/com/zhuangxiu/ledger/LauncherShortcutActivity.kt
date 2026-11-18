package com.zhuangxiu.ledger

import android.app.Activity
import android.content.Intent
import android.os.Bundle

internal enum class LedgerShortcut(val action: String) {
    NEW_ENTRY("com.zhuangxiu.ledger.action.NEW_ENTRY"),
    STATISTICS("com.zhuangxiu.ledger.action.STATISTICS");

    companion object {
        fun fromAction(action: String?): LedgerShortcut? = entries.firstOrNull { it.action == action }
    }
}

class LauncherShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 系统为静态快捷方式首个 Intent 添加 CLEAR_TASK，隔离入口以保留主任务里的草稿。
        LedgerShortcut.fromAction(intent.action)?.let { shortcut ->
            startActivity(
                Intent(this, MainActivity::class.java)
                    .setAction(shortcut.action)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
        finish()
    }
}
