package com.zhuangxiu.ledger.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PieChartOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zhuangxiu.ledger.ui.bills.BillsScreen
import com.zhuangxiu.ledger.ui.detail.DetailScreen
import com.zhuangxiu.ledger.ui.edit.EditScreen
import com.zhuangxiu.ledger.ui.gallery.GalleryScreen
import com.zhuangxiu.ledger.ui.home.HomeScreen
import com.zhuangxiu.ledger.ui.mine.BackupScreen
import com.zhuangxiu.ledger.ui.mine.BudgetScreen
import com.zhuangxiu.ledger.ui.mine.CategoriesScreen
import com.zhuangxiu.ledger.ui.mine.MineScreen
import com.zhuangxiu.ledger.ui.mine.ReminderScreen
import com.zhuangxiu.ledger.ui.stats.StatsScreen
import com.zhuangxiu.ledger.ui.viewer.ViewerScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.HOME, "首页", Icons.Outlined.Home),
    Tab(Routes.BILLS, "账单", Icons.AutoMirrored.Outlined.ReceiptLong),
    Tab(Routes.STATS, "统计", Icons.Outlined.PieChartOutline),
    Tab(Routes.MINE, "我的", Icons.Outlined.PersonOutline),
)

/** 底部导航栏，4 个主页面共用 */
@Composable
fun LedgerBottomBar(current: String, onNavigate: (String) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        TABS.forEach { tab ->
            NavigationBarItem(
                selected = tab.route == current,
                onClick = { if (tab.route != current) onNavigate(tab.route) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
            )
        }
    }
}

/**
 * 应用的导航图。
 * @param pendingExpenseId 从通知点进来时要打开的账单 id
 * @param onPendingHandled 已跳转后回调，清除待处理的 id
 */
@Composable
fun LedgerNavHost(
    pendingExpenseId: Long?,
    onPendingHandled: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    // 切换底部页面：回到起始页并保留各页面状态
    val switchTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(Routes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val back: () -> Unit = { navController.popBackStack() }

    LaunchedEffect(pendingExpenseId) {
        if (pendingExpenseId != null) {
            navController.navigate(Routes.detail(pendingExpenseId))
            onPendingHandled()
        }
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                bottomBar = { LedgerBottomBar(Routes.HOME, switchTab) },
                onAdd = { navController.navigate(Routes.edit()) },
                onOpenExpense = { navController.navigate(Routes.detail(it)) },
                onOpenBills = { switchTab(Routes.BILLS) },
                onOpenBudget = { navController.navigate(Routes.BUDGET) },
            )
        }
        composable(Routes.BILLS) {
            BillsScreen(
                bottomBar = { LedgerBottomBar(Routes.BILLS, switchTab) },
                onAdd = { navController.navigate(Routes.edit()) },
                onOpenExpense = { navController.navigate(Routes.detail(it)) },
            )
        }
        composable(Routes.STATS) {
            StatsScreen(bottomBar = { LedgerBottomBar(Routes.STATS, switchTab) })
        }
        composable(Routes.MINE) {
            MineScreen(
                bottomBar = { LedgerBottomBar(Routes.MINE, switchTab) },
                onNavigate = { navController.navigate(it) },
            )
        }
        composable(
            Routes.EDIT,
            arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
        ) {
            EditScreen(onBack = back)
        }
        composable(Routes.DETAIL, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            DetailScreen(
                onBack = back,
                onEdit = { navController.navigate(Routes.edit(it)) },
                onViewImage = { expenseId, index -> navController.navigate(Routes.viewer(expenseId, index)) },
            )
        }
        composable(
            Routes.VIEWER,
            arguments = listOf(
                navArgument("expenseId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("index") { type = NavType.IntType; defaultValue = 0 },
            ),
        ) {
            ViewerScreen(onBack = back, onOpenExpense = { navController.navigate(Routes.detail(it)) })
        }
        composable(Routes.GALLERY) {
            GalleryScreen(onBack = back, onViewImage = { index -> navController.navigate(Routes.viewer(-1, index)) })
        }
        composable(Routes.BUDGET) { BudgetScreen(onBack = back) }
        composable(Routes.CATEGORIES) { CategoriesScreen(onBack = back) }
        composable(Routes.BACKUP) { BackupScreen(onBack = back) }
        composable(Routes.REMINDER) { ReminderScreen(onBack = back) }
    }
}
