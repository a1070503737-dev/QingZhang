package com.zhuangxiu.ledger.ui.navigation

import androidx.compose.material.icons.Icons
import com.zhuangxiu.ledger.ui.daily.*
import com.zhuangxiu.ledger.data.db.BookKind
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChartOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.zhuangxiu.ledger.ui.bills.BillsViewModel
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import com.zhuangxiu.ledger.ui.importing.ImportScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.zhuangxiu.ledger.ui.mine.UpdateScreen
import com.zhuangxiu.ledger.ui.viewer.ViewerScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.HOME, "首页", Icons.Outlined.Home),
    Tab(Routes.STATS, "统计", Icons.Outlined.PieChartOutline),
    Tab(Routes.BOOKS, "账本", Icons.Outlined.AccountBalanceWallet),
)

/** 底部导航栏：首页、统计和账本共用 */
@Composable
fun LedgerBottomBar(current: String, onNavigate: (String) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
        TABS.forEach { tab ->
            NavigationBarItem(
                selected = tab.route == current,
                onClick = { if (tab.route != current) onNavigate(tab.route) },
                icon = { Icon(tab.icon, contentDescription = null) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer, selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary),
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
    val dailyViewModel: DailyViewModel = hiltViewModel()
    val bookState by dailyViewModel.books.collectAsStateWithLifecycle()
    val dailyState by dailyViewModel.daily.collectAsStateWithLifecycle()
    val header: @Composable () -> Unit = {
        BookHeader(bookState, dailyViewModel::select) { navController.navigate(Routes.MINE) }
    }
    // 切换底部页面：回到起始页并保留各页面状态
    val switchTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(Routes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val back: () -> Unit = { navController.popBackStack() }
    var pickQuickBook by rememberSaveable { mutableStateOf(false) }
    if (pickQuickBook) AlertDialog(
        onDismissRequest = { pickQuickBook = false }, title = { Text("记到哪个账本？") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            bookState.books.filterNot { it.archived }.forEach { book ->
                TextButton({ pickQuickBook = false; navController.navigate(Routes.dailyEdit(book.id)) }) { Text(book.name) }
            }
        } },
        confirmButton = { TextButton({ pickQuickBook = false }) { Text("取消") } },
    )

    LaunchedEffect(pendingExpenseId) {
        if (pendingExpenseId != null) {
            navController.navigate(Routes.detail(pendingExpenseId))
            onPendingHandled()
        }
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            val projectBook = bookState.selected?.takeIf { !bookState.all && it.kind == BookKind.RENOVATION }
            DailyHomeScreen(dailyState, header, { LedgerBottomBar(Routes.HOME, switchTab) },
                dailyViewModel::month, dailyViewModel::currentMonth,
                onAdd = {
                    if (bookState.all) pickQuickBook = true
                    else bookState.selected?.let { navController.navigate(Routes.dailyEdit(it.id)) }
                },
                onEdit = { id ->
                    val projectId = id.takeIf { it.startsWith("payment:") }?.split(':')?.getOrNull(1)?.toLongOrNull()
                    if (projectId != null) navController.navigate(Routes.detail(projectId))
                    else navController.navigate(Routes.dailyEdit(1, id))
                },
                onProjects = projectBook?.let { book -> { navController.navigate(Routes.projects(book.id)) } },
                bookNames = if (bookState.all) bookState.books.associate { it.id to it.name } else emptyMap(),
                canAdd = bookState.selected != null,
            )
        }
        composable(Routes.PROJECTS, arguments = listOf(navArgument("bookId") { type = NavType.LongType })) { entry ->
            val bookId = requireNotNull(entry.arguments?.getLong("bookId"))
            HomeScreen(
                header = { androidx.compose.material3.TopAppBar(title = { Text("装修项目") },
                    navigationIcon = { TextButton(back) { Text("返回") } }) },
                bottomBar = {},
                onAdd = { navController.navigate(Routes.edit(bookId = bookId)) },
                onOpenExpense = { navController.navigate(Routes.detail(it)) },
                onOpenBills = { navController.navigate(Routes.bills(bookId)) },
                onOpenBudget = { navController.navigate(Routes.BUDGET) },
                showBudget = bookId == 2L,
                onOpenPending = {
                    navController.navigate(Routes.bills(bookId))
                    navController.getBackStackEntry(Routes.BILLS).savedStateHandle["showPending"] = true
                },
            )
        }
        composable(Routes.BOOKS) {
            BooksScreen(bookState, bottomBar = { LedgerBottomBar(Routes.BOOKS, switchTab) },
                onSelect = { dailyViewModel.select(it); switchTab(Routes.HOME) },
                onSave = dailyViewModel::saveBook,
                onSettings = { navController.navigate(Routes.MINE) }, onClearError = dailyViewModel::clearError)
        }
        composable(Routes.DAILY_EDIT, arguments = listOf(
            navArgument("bookId") { type = NavType.LongType; defaultValue = 1L },
            navArgument("entryId") { type = NavType.StringType; defaultValue = "" },
        )) { DailyEditScreen(onBack = back) }
        composable(Routes.BILLS, arguments = listOf(navArgument("bookId") { type = NavType.LongType })) { entry ->
            val bookId = requireNotNull(entry.arguments?.getLong("bookId"))
            val billsViewModel: BillsViewModel = hiltViewModel()
            // 每次从首页进入都消费一次请求，支持恢复已存在的账单页状态。
            val pendingRequest by entry.savedStateHandle.getStateFlow("showPending", false).collectAsStateWithLifecycle()
            LaunchedEffect(pendingRequest) {
                if (pendingRequest) {
                    billsViewModel.showPending()
                    entry.savedStateHandle["showPending"] = false
                }
            }
            BillsScreen(
                viewModel = billsViewModel,
                bottomBar = { LedgerBottomBar(Routes.BILLS, switchTab) },
                onAdd = { navController.navigate(Routes.edit(bookId = bookId)) },
                onOpenExpense = { navController.navigate(Routes.detail(it)) },
                onEditExpense = { navController.navigate(Routes.edit(id = it)) },
                onCopyExpense = { navController.navigate(Routes.edit(copyFrom = it, bookId = bookId)) },
            )
        }
        composable(Routes.STATS) {
            DailyStatsScreen(dailyState, header, { LedgerBottomBar(Routes.STATS, switchTab) }, dailyViewModel::month, dailyViewModel::currentMonth)
        }
        composable(Routes.MINE) {
            MineScreen(
                onBack = back,
                bottomBar = {},
                onNavigate = { navController.navigate(it) },
            )
        }
        composable(
            Routes.EDIT,
            arguments = listOf(
                navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                navArgument("copyFrom") { type = NavType.LongType; defaultValue = -1L },
                navArgument("bookId") { type = NavType.LongType; defaultValue = 2L },
            ),
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
        composable(Routes.IMPORT) { ImportScreen(onBack = back) }
        composable(Routes.REMINDER) { ReminderScreen(onBack = back) }
        composable(Routes.UPDATE) { UpdateScreen(onBack = back) }
    }
}
