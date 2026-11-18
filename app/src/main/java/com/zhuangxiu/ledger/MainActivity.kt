package com.zhuangxiu.ledger

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.zhuangxiu.ledger.ui.components.LiquidGlassBackdrop
import com.zhuangxiu.ledger.ui.navigation.LedgerNavHost
import com.zhuangxiu.ledger.ui.theme.RenovationLedgerTheme
import dagger.hilt.android.AndroidEntryPoint

/** 唯一的 Activity，所有页面都是沉浸式 Liquid Glass Compose 页面 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** 从通知点进来时要打开的账单 id */
    private var pendingExpenseId by mutableStateOf<Long?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 结果在提醒设置页里体现 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            handleIntent(intent)
            requestNotificationPermissionIfNeeded()
        }
        setContent {
            RenovationLedgerTheme {
                LiquidGlassBackdrop {
                    LedgerNavHost(
                        pendingExpenseId = pendingExpenseId,
                        onPendingHandled = { pendingExpenseId = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_EXPENSE_ID, -1L) ?: -1L
        if (id > 0) pendingExpenseId = id
    }

    /** Android 13+ 首次启动时申请通知权限，用于到期提醒和预算预警 */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val EXTRA_EXPENSE_ID = "expense_id"
    }
}
