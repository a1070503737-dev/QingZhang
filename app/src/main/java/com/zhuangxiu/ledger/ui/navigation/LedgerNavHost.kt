package com.zhuangxiu.ledger.ui.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zhuangxiu.ledger.data.db.BookEntity
import com.zhuangxiu.ledger.data.db.BookKind
import com.zhuangxiu.ledger.ui.bills.BillsScreen
import com.zhuangxiu.ledger.ui.bills.BillsViewModel
import com.zhuangxiu.ledger.ui.components.GlassVariant
import com.zhuangxiu.ledger.ui.components.LiquidGlassBackdrop
import com.zhuangxiu.ledger.ui.components.LiquidGlassSurface
import com.zhuangxiu.ledger.ui.components.LiquidGlassTopBar
import com.zhuangxiu.ledger.ui.components.liquidGlass
import com.zhuangxiu.ledger.ui.components.liquidInteractive
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
import kotlin.math.abs
import kotlin.math.roundToInt

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.HOME, "首页", Icons.Outlined.Home),
    Tab(Routes.STATS, "统计", Icons.Outlined.PieChartOutline),
    Tab(Routes.BOOKS, "账本", Icons.Outlined.AccountBalanceWallet),
)

/**
 * 悬浮液态玻璃胶囊底栏（Liquid Glass Morphing Pill Dock）：
 * 选中滑块使用弹性低阻尼弹簧滑动，并在高速跨越时产生真实的水平拉伸与垂直微缩液滴形变。
 */
@Composable
fun LedgerBottomBar(current: String, onNavigate: (String) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val selectedIndex = TABS.indexOfFirst { it.route == current }.let { if (it < 0) 0 else it }
    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = 0.62f,
            stiffness = 380f,
        ),
        label = "liquid-lens-index",
    )
    // 运动中基于位移差产生水滴拉长与回弹形变
    val travelDelta = abs(animatedIndex - selectedIndex.toFloat()).coerceIn(0f, 1f)
    val lensScaleX = 1f + 0.16f * sinApprox(travelDelta)
    val lensScaleY = 1f - 0.08f * sinApprox(travelDelta)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .liquidGlass(
                    shape = CircleShape,
                    variant = GlassVariant.Elevated,
                    cornerRadius = 33.dp,
                    shadowElevation = 16.dp,
                )
                .padding(horizontal = 6.dp, vertical = 5.dp),
        ) {
            val tabWidth = maxWidth / TABS.size
            val tabWidthPx = constraints.maxWidth.toFloat() / TABS.size

            // 液态玻璃滑动透镜滑块
            Box(
                modifier = Modifier
                    .width(tabWidth)
                    .fillMaxHeight()
                    .offset { IntOffset((animatedIndex * tabWidthPx).roundToInt(), 0) }
                    .graphicsLayer {
                        scaleX = lensScaleX
                        scaleY = lensScaleY
                    }
                    .liquidGlass(
                        shape = CircleShape,
                        variant = GlassVariant.Tinted(MaterialTheme.colorScheme.primaryContainer),
                        cornerRadius = 28.dp,
                        shadowElevation = 4.dp,
                    ),
            )

            Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                TABS.forEach { tab ->
                    val selected = tab.route == current
                    val interactionSource = remember(tab.route) { MutableInteractionSource() }
                    val iconLift by animateFloatAsState(
                        targetValue = if (selected) 1.08f else 1f,
                        animationSpec = spring(dampingRatio = 0.56f, stiffness = Spring.StiffnessMedium),
                        label = "tab-lift-${tab.route}",
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .liquidInteractive(
                                interactionSource = interactionSource,
                                pressedScale = 1.08f,
                            )
                            .selectable(
                                selected = selected,
                                interactionSource = interactionSource,
                                indication = LocalIndication.current,
                                role = Role.Tab,
                                onClick = {
                                    if (!selected) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onNavigate(tab.route)
                                    }
                                },
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer {
                                    scaleX = iconLift
                                    scaleY = iconLift
                                },
                        )
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun sinApprox(x: Float): Float = kotlin.math.sin(x * Math.PI).toFloat()

/**
 * 应用的导航图：挂载全局极光玻璃背景与弹性空间转场动画。
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
    val switchTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(Routes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
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
            confirmButton = { TextButton({ pickQuickBook = false }) { Text("取消") } },
        )
    }

    LaunchedEffect(pendingExpenseId) {
        if (pendingExpenseId != null) {
            navController.navigate(Routes.detail(pendingExpenseId))
            onPendingHandled()
        }
    }

    LiquidGlassBackdrop {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            enterTransition = {
                fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                    scaleIn(initialScale = 0.96f, animationSpec = spring(dampingRatio = 0.84f, stiffness = Spring.StiffnessMediumLow))
            },
            exitTransition = {
                fadeOut(spring(stiffness = Spring.StiffnessMedium)) +
                    scaleOut(targetScale = 0.97f, animationSpec = spring(stiffness = Spring.StiffnessMedium))
            },
            popEnterTransition = {
                fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                    slideInHorizontally(initialOffsetX = { -it / 6 }, animationSpec = spring(dampingRatio = 0.84f, stiffness = Spring.StiffnessMediumLow))
            },
            popExitTransition = {
                fadeOut(spring(stiffness = Spring.StiffnessMedium)) +
                    slideOutHorizontally(targetOffsetX = { it / 6 }, animationSpec = spring(dampingRatio = 0.84f, stiffness = Spring.StiffnessMediumLow))
            },
        ) {
            composable(Routes.HOME) {
                val projectBook = bookState.selected?.takeIf { !bookState.all && it.kind == BookKind.RENOVATION }
                DailyHomeScreen(
                    dailyState,
                    header,
                    { LedgerBottomBar(Routes.HOME, switchTab) },
                    dailyViewModel::month,
                    dailyViewModel::currentMonth,
                    onAdd = {
                        if (bookState.all) pickQuickBook = true
                        else bookState.selected?.let(addToBook)
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
                    header = {
                        LiquidGlassTopBar(
                            title = { Text("装修项目") },
                            navigationIcon = {
                                TextButton(
                                    onClick = back,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.liquidGlass(CircleShape, GlassVariant.Clear, cornerRadius = 20.dp, shadowElevation = 0.dp),
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
                    bottomBar = { LedgerBottomBar(Routes.BOOKS, switchTab) },
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
}
