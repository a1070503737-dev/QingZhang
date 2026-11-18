package com.zhuangxiu.ledger.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Cottage
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChartOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zhuangxiu.ledger.data.db.BookEntity
import com.zhuangxiu.ledger.data.db.BookKind
import com.zhuangxiu.ledger.ui.bills.BillsScreen
import com.zhuangxiu.ledger.ui.bills.BillsViewModel
import com.zhuangxiu.ledger.ui.components.GlassVariant
import com.zhuangxiu.ledger.ui.components.LiquidGlassBackdrop
import com.zhuangxiu.ledger.ui.components.LiquidGlassButton
import com.zhuangxiu.ledger.ui.components.LiquidGlassSurface
import com.zhuangxiu.ledger.ui.components.LiquidGlassTopBar
import com.zhuangxiu.ledger.ui.components.LiquidLensTrack
import com.zhuangxiu.ledger.ui.daily.BookHeader
import com.zhuangxiu.ledger.ui.daily.BooksScreen
import com.zhuangxiu.ledger.ui.daily.DailyEditScreen
import com.zhuangxiu.ledger.ui.daily.DailyHomeScreen
import com.zhuangxiu.ledger.ui.daily.DailyStatsScreen
import com.zhuangxiu.ledger.ui.daily.DailyViewModel
import com.zhuangxiu.ledger.ui.detail.DetailScreen
import com.zhuangxiu.ledger.ui.edit.EditScreen
import com.zhuangxiu.ledger.ui.gallery.GalleryScreen
import com.zhuangxiu.ledger.ui.home.HomeScreen
import com.zhuangxiu.ledger.ui.importing.ImportScreen
import com.zhuangxiu.ledger.ui.mine.BackupScreen
import com.zhuangxiu.ledger.ui.mine.BudgetScreen
import com.zhuangxiu.ledger.ui.mine.CategoriesScreen
import com.zhuangxiu.ledger.ui.mine.MineScreen
import com.zhuangxiu.ledger.ui.mine.ReminderScreen
import com.zhuangxiu.ledger.ui.mine.UpdateScreen
import com.zhuangxiu.ledger.ui.theme.LedgerTheme
import com.zhuangxiu.ledger.ui.viewer.ViewerScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.HOME, "首页", Icons.Outlined.Home),
    Tab(Routes.STATS, "统计", Icons.Outlined.PieChartOutline),
    Tab(Routes.BOOKS, "账本", Icons.Outlined.AccountBalanceWallet),
)

private fun tabIndexOf(route: String?): Int = TABS.indexOfFirst { it.route == route }

/**
 * 全局唯一持久化悬浮液态水滴导航栏：
 * 独立于 NavHost 之外，切换“首页 / 统计 / 账本”时底栏实例永不销毁重建，
 * 水滴透镜全程丝滑跟手穿过三个 Tab 并实时穿透折射底部滚动页面内容，彻底消除双底栏重影与断层！
 */
@Composable
fun LedgerBottomBar(current: String, onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    val selectedIndex = tabIndexOf(current).let { if (it < 0) 0 else it }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        LiquidLensTrack(
            itemCount = TABS.size,
            selectedIndex = selectedIndex,
            height = 64.dp,
            role = Role.Tab,
            liveSelectOnScrub = true,
            onSelect = { idx ->
                val target = TABS[idx].route
                if (target != current) onNavigate(target)
            },
        ) { idx, isSelected ->
            val tab = TABS[idx]
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun LedgerNavHost(
    pendingExpenseId: Long?,
    onPendingHandled: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    val dailyViewModel: DailyViewModel = hiltViewModel()
    val bookState by dailyViewModel.books.collectAsStateWithLifecycle()
    val dailyState by dailyViewModel.daily.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val header: @Composable () -> Unit = {
        BookHeader(bookState, dailyViewModel::select) { navController.navigate(Routes.MINE) }
    }
    val switchTab: (String) -> Unit = { route ->
        if (currentRoute != route) {
            navController.navigate(route) {
                popUpTo(Routes.HOME) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
    val back: () -> Unit = { navController.popBackStack() }
    val addToBook: (BookEntity) -> Unit = { book ->
        val route = when (book.kind) {
            BookKind.DAILY -> Routes.dailyEdit(book.id)
            BookKind.RENOVATION -> Routes.edit(bookId = book.id)
        }
        navController.navigate(route)
    }
    val openEntryEdit: (String) -> Unit = { id ->
        val projectId = id.takeIf { it.startsWith("payment:") }?.split(':')?.getOrNull(1)?.toLongOrNull()
        if (projectId != null) navController.navigate(Routes.detail(projectId))
        else navController.navigate(Routes.dailyEdit(1, id))
    }

    // 透明占位高度，确保各主页 FAB 精准悬停在持久化底栏上方，同时页面内容可滚入底栏下方供水滴实时折射
    val dockSpacer: @Composable () -> Unit = {
        Spacer(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(84.dp),
        )
    }

    var pickQuickBook by rememberSaveable { mutableStateOf(false) }
    val glass = LedgerTheme.glass
    if (pickQuickBook) {
        AlertDialog(
            onDismissRequest = { pickQuickBook = false },
            shape = RoundedCornerShape(30.dp),
            containerColor = glass.elevatedTop.copy(alpha = if (glass.isDark) 0.90f else 0.94f),
            tonalElevation = 0.dp,
            modifier = Modifier.border(BorderStroke(1.dp, glass.specularBright), RoundedCornerShape(30.dp)),
            title = { Text("记到哪个账本？") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    bookState.books.filterNot { it.archived }.forEach { book ->
                        LiquidGlassSurface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            cornerRadius = 18.dp,
                            variant = GlassVariant.Clear,
                            shadowElevation = 0.dp,
                            onClick = {
                                pickQuickBook = false
                                addToBook(book)
                            },
                        ) {
                            Row(
                                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    if (book.kind == BookKind.DAILY) Icons.Outlined.AccountBalanceWallet else Icons.Outlined.Cottage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(book.name, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                LiquidGlassButton(
                    onClick = { pickQuickBook = false },
                    prominent = false,
                    minHeight = 38.dp,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                ) { Text("取消") }
            },
        )
    }

    LaunchedEffect(pendingExpenseId) {
        if (pendingExpenseId != null) {
            navController.navigate(Routes.detail(pendingExpenseId))
            onPendingHandled()
        }
    }

    val showBottomBar = tabIndexOf(currentRoute) >= 0 || currentRoute == Routes.BILLS || currentRoute == null

    LiquidGlassBackdrop(
        overlay = {
            AnimatedVisibility(
                visible = showBottomBar,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(tween(180)) + slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it / 2 },
                exit = fadeOut(tween(140)) + slideOutVertically(tween(180, easing = FastOutSlowInEasing)) { it / 2 },
            ) {
                LedgerBottomBar(
                    current = currentRoute ?: Routes.HOME,
                    onNavigate = switchTab,
                )
            }
        },
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            enterTransition = {
                val fromTab = tabIndexOf(initialState.destination.route)
                val toTab = tabIndexOf(targetState.destination.route)
                if (fromTab >= 0 && toTab >= 0) {
                    val dir = if (toTab >= fromTab) 1 else -1
                    fadeIn(tween(180, easing = FastOutSlowInEasing)) +
                        slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { fullWidth -> dir * (fullWidth / 14) }
                } else {
                    fadeIn(tween(200, easing = FastOutSlowInEasing)) +
                        slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { it / 6 }
                }
            },
            exitTransition = {
                val fromTab = tabIndexOf(initialState.destination.route)
                val toTab = tabIndexOf(targetState.destination.route)
                if (fromTab >= 0 && toTab >= 0) {
                    val dir = if (toTab >= fromTab) 1 else -1
                    fadeOut(tween(130, easing = FastOutSlowInEasing)) +
                        slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { fullWidth -> -dir * (fullWidth / 14) }
                } else {
                    fadeOut(tween(160, easing = FastOutSlowInEasing))
                }
            },
            popEnterTransition = {
                fadeIn(tween(180, easing = FastOutSlowInEasing)) +
                    slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { -it / 7 }
            },
            popExitTransition = {
                fadeOut(tween(150, easing = FastOutSlowInEasing)) +
                    slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { it / 6 }
            },
        ) {
            composable(Routes.HOME) {
                val projectBook = bookState.selected?.takeIf { !bookState.all && it.kind == BookKind.RENOVATION }
                DailyHomeScreen(
                    dailyState,
                    header,
                    dockSpacer,
                    dailyViewModel::month,
                    dailyViewModel::currentMonth,
                    onAdd = {
                        if (bookState.all) pickQuickBook = true
                        else bookState.selected?.let(addToBook)
                    },
                    onEdit = openEntryEdit,
                    onProjects = projectBook?.let { book -> { navController.navigate(Routes.projects(book.id)) } },
                    bookNames = if (bookState.all) bookState.books.associate { it.id to it.name } else emptyMap(),
                    canAdd = bookState.selected != null,
                )
            }
            composable(Routes.PROJECTS, arguments = listOf(navArgument("bookId") { type = NavType.LongType })) { entry ->
                val bookId = requireNotNull(entry.arguments?.getLong("bookId"))
                HomeScreen(
                    header = {
                        LiquidGlassTopBar(
                            title = { Text("装修项目") },
                            navigationIcon = {
                                LiquidGlassButton(
                                    onClick = back,
                                    prominent = false,
                                    minHeight = 36.dp,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("返回")
                                }
                            },
                        )
                    },
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
                BooksScreen(
                    bookState,
                    bottomBar = dockSpacer,
                    onSelect = {
                        dailyViewModel.select(it)
                        switchTab(Routes.HOME)
                    },
                    onSave = dailyViewModel::saveBook,
                    onSettings = { navController.navigate(Routes.MINE) },
                    onClearError = dailyViewModel::clearError,
                )
            }
            composable(
                Routes.DAILY_EDIT,
                arguments = listOf(
                    navArgument("bookId") { type = NavType.LongType; defaultValue = 1L },
                    navArgument("entryId") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { DailyEditScreen(onBack = back) }
            composable(Routes.BILLS, arguments = listOf(navArgument("bookId") { type = NavType.LongType })) { entry ->
                val bookId = requireNotNull(entry.arguments?.getLong("bookId"))
                val billsViewModel: BillsViewModel = hiltViewModel()
                val pendingRequest by entry.savedStateHandle.getStateFlow("showPending", false).collectAsStateWithLifecycle()
                LaunchedEffect(pendingRequest) {
                    if (pendingRequest) {
                        billsViewModel.showPending()
                        entry.savedStateHandle["showPending"] = false
                    }
                }
                BillsScreen(
                    viewModel = billsViewModel,
                    bottomBar = dockSpacer,
                    onAdd = { navController.navigate(Routes.edit(bookId = bookId)) },
                    onOpenExpense = { navController.navigate(Routes.detail(it)) },
                    onEditExpense = { navController.navigate(Routes.edit(id = it)) },
                    onCopyExpense = { navController.navigate(Routes.edit(copyFrom = it, bookId = bookId)) },
                )
            }
            composable(Routes.STATS) {
                DailyStatsScreen(
                    state = dailyState,
                    header = header,
                    bottomBar = dockSpacer,
                    onMonth = dailyViewModel::month,
                    onToday = dailyViewModel::currentMonth,
                    onEdit = openEntryEdit,
                    bookNames = if (bookState.all) bookState.books.associate { it.id to it.name } else emptyMap(),
                )
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
}
