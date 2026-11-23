package com.zhuangxiu.ledger.ui.navigation

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.zhuangxiu.ledger.LedgerShortcut
import com.zhuangxiu.ledger.data.db.BookEntity
import com.zhuangxiu.ledger.data.db.BookKind
import com.zhuangxiu.ledger.ui.bills.BillsScreen
import com.zhuangxiu.ledger.ui.bills.BillsViewModel
import com.zhuangxiu.ledger.ui.components.SurfaceTone
import com.zhuangxiu.ledger.ui.components.LedgerBackground
import com.zhuangxiu.ledger.ui.components.LedgerButton
import com.zhuangxiu.ledger.ui.components.LedgerPanel
import com.zhuangxiu.ledger.ui.components.LedgerTopBar
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
import com.zhuangxiu.ledger.ui.mine.FeedbackScreen
import com.zhuangxiu.ledger.ui.mine.MineScreen
import com.zhuangxiu.ledger.ui.mine.ReminderScreen
import com.zhuangxiu.ledger.ui.mine.UpdateScreen
import com.zhuangxiu.ledger.ui.viewer.ViewerScreen
import com.zhuangxiu.ledger.ui.voice.VoiceEntryScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LedgerNavHost(
    pendingExpenseId: Long?,
    onPendingHandled: () -> Unit,
    pendingShortcut: LedgerShortcut?,
    onShortcutHandled: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    val dailyViewModel: DailyViewModel = hiltViewModel()
    val bookState by dailyViewModel.books.collectAsStateWithLifecycle()
    val dailyState by dailyViewModel.daily.collectAsStateWithLifecycle()
    val appContext = LocalContext.current.applicationContext
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val header: @Composable () -> Unit = {
        BookHeader(bookState, dailyViewModel::select)
    }
    val switchTab: (String) -> Unit = { route ->
        val primary = if (isTabHost(currentRoute)) {
            backStackEntry
        } else {
            navController.popBackStack(Routes.HOME, inclusive = false)
            navController.getBackStackEntry(Routes.HOME)
        }
        primary?.savedStateHandle?.set(RequestedTabKey, route)
    }
    val back: () -> Unit = { navController.popBackStack() }
    val addToBook: (BookEntity) -> Unit = { book ->
        val args = backStackEntry?.arguments
        val alreadyInNewEntry = args != null && args.getLong("bookId") == book.id && when (currentRoute) {
            Routes.DAILY_EDIT -> args.getString("entryId").isNullOrEmpty()
            Routes.EDIT -> args.getLong("id", -1) < 0 && args.getLong("copyFrom", -1) < 0
            else -> false
        }
        if (!alreadyInNewEntry) {
            val route = when (book.kind) {
                BookKind.DAILY -> Routes.dailyEdit(book.id)
                BookKind.RENOVATION -> Routes.edit(bookId = book.id)
            }
            navController.navigate(route)
        }
    }
    val openEntryEdit: (String) -> Unit = { id ->
        val projectId = id.takeIf { it.startsWith("payment:") }?.split(':')?.getOrNull(1)?.toLongOrNull()
        if (projectId != null) navController.navigate(Routes.detail(projectId))
        else navController.navigate(Routes.dailyEdit(1, id))
    }

    val dockSpacer: @Composable () -> Unit = {
        Spacer(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(DockHeight + DockBottomGap),
        )
    }

    var pickQuickBook by rememberSaveable { mutableStateOf(false) }
    val currentActiveBook = bookState.selected?.takeUnless { it.archived || bookState.all }
    val activeBooks = bookState.books.filterNot { it.archived }
    val openQuickEntry: () -> Unit = {
        when {
            currentActiveBook != null -> addToBook(currentActiveBook)
            activeBooks.isNotEmpty() -> pickQuickBook = true
            else -> switchTab(Routes.BOOKS)
        }
    }
    if (pickQuickBook) {
        ModalBottomSheet(
            onDismissRequest = { pickQuickBook = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("记到哪个账本？", style = MaterialTheme.typography.titleLarge)
                activeBooks.forEach { book ->
                    LedgerPanel(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        variant = SurfaceTone.Flat,
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
                LedgerButton(
                    onClick = { pickQuickBook = false },
                    prominent = false,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("取消") }
            }
        }
    }

    LaunchedEffect(pendingExpenseId) {
        if (pendingExpenseId != null) {
            navController.navigate(Routes.detail(pendingExpenseId))
            onPendingHandled()
        }
    }

    LaunchedEffect(pendingShortcut, currentRoute, bookState.loaded) {
        if (pendingShortcut == null || currentRoute == null) return@LaunchedEffect
        when (pendingShortcut) {
            LedgerShortcut.NEW_ENTRY -> {
                if (!bookState.loaded) return@LaunchedEffect
                openQuickEntry()
            }
            LedgerShortcut.STATISTICS -> {
                pickQuickBook = false
                if (isTabHost(currentRoute)) {
                    switchTab(Routes.STATS)
                } else {
                    navController.navigate(Routes.STATS) { launchSingleTop = true }
                }
            }
        }
        onShortcutHandled()
    }

    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val showBottomBar = !imeVisible && currentRoute == Routes.BILLS
    val billsDockBackdrop = rememberDockBackdrop()

    LedgerBackground(
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
                    backdrop = billsDockBackdrop,
                )
            }
        },
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            enterTransition = {
                fadeIn(tween(200, easing = FastOutSlowInEasing)) +
                    slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { it / 6 }
            },
            exitTransition = { fadeOut(tween(160, easing = FastOutSlowInEasing)) },
            popEnterTransition = {
                fadeIn(tween(180, easing = FastOutSlowInEasing)) +
                    slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { -it / 7 }
            },
            popExitTransition = {
                fadeOut(tween(150, easing = FastOutSlowInEasing)) +
                    slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { it / 6 }
            },
        ) {
            TABS.forEach { tab ->
                composable(tab.route) { entry ->
                    LedgerTabPages(entry, tab.route) { route, openTab, onEditingBook ->
                        when (route) {
                            Routes.HOME -> {
                                val archivedBook = bookState.selected?.takeIf { !bookState.all && it.archived }
                                val projectBook = bookState.selected?.takeIf { !bookState.all && !it.archived && it.kind == BookKind.RENOVATION }
                                DailyHomeScreen(
                                    dailyState,
                                    header,
                                    dockSpacer,
                                    dailyViewModel::month,
                                    dailyViewModel::currentMonth,
                                    onAdd = openQuickEntry,
                                    onEdit = openEntryEdit,
                                    onProjects = projectBook?.let { book -> { navController.navigate(Routes.projects(book.id)) } },
                                    bookNames = if (bookState.all) bookState.books.associate { it.id to it.name } else emptyMap(),
                                    canAdd = if (bookState.all) bookState.books.any { !it.archived } else bookState.selected?.archived == false,
                                    readOnly = archivedBook != null,
                                    archivedBookIds = bookState.books.filter { it.archived }.map { it.id }.toSet(),
                                    onDelete = { id ->
                                        dailyViewModel.deleteEntry(id) { message ->
                                            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                )
                            }
                            Routes.STATS -> {
                                DailyStatsScreen(
                                    state = dailyState,
                                    header = header,
                                    bottomBar = dockSpacer,
                                    onMonth = dailyViewModel::month,
                                    onToday = dailyViewModel::currentMonth,
                                    onEdit = openEntryEdit.takeUnless { !bookState.all && bookState.selected?.archived == true },
                                    bookNames = if (bookState.all) bookState.books.associate { it.id to it.name } else emptyMap(),
                                )
                            }
                            Routes.BOOKS -> {
                                BooksScreen(
                                    bookState,
                                    bottomBar = dockSpacer,
                                    onSelect = {
                                        dailyViewModel.select(it)
                                        openTab(Routes.HOME)
                                    },
                                    onSave = dailyViewModel::saveBook,
                                    onClearError = dailyViewModel::clearError,
                                    onEditingChange = onEditingBook,
                                )
                            }
                            Routes.MINE -> MineScreen(
                                bottomBar = dockSpacer,
                                onNavigate = { navController.navigate(it) },
                            )
                        }
                    }
                }
            }
            composable(Routes.PROJECTS, arguments = listOf(navArgument("bookId") { type = NavType.LongType })) { entry ->
                val bookId = requireNotNull(entry.arguments?.getLong("bookId"))
                HomeScreen(
                    header = {
                        LedgerTopBar(
                            title = { Text("装修项目") },
                            navigationIcon = {
                                LedgerButton(
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
            composable(
                Routes.DAILY_EDIT,
                arguments = listOf(
                    navArgument("bookId") { type = NavType.LongType; defaultValue = 1L },
                    navArgument("entryId") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                val bookId = requireNotNull(entry.arguments?.getLong("bookId"))
                DailyEditScreen(onBack = back, onVoice = { navController.navigate(Routes.voiceEntry(bookId)) })
            }
            composable(Routes.VOICE_ENTRY, arguments = listOf(navArgument("bookId") { type = NavType.LongType })) {
                VoiceEntryScreen(onBack = back, onSaved = { navController.popBackStack(Routes.DAILY_EDIT, inclusive = true) })
            }
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
                Box(Modifier.fillMaxSize().recordDockBackdrop(if (showBottomBar) billsDockBackdrop else null)) {
                    BillsScreen(
                        viewModel = billsViewModel,
                        bottomBar = dockSpacer,
                        onAdd = { navController.navigate(Routes.edit(bookId = bookId)) },
                        onOpenExpense = { navController.navigate(Routes.detail(it)) },
                        onEditExpense = { navController.navigate(Routes.edit(id = it)) },
                        onCopyExpense = { navController.navigate(Routes.edit(copyFrom = it, bookId = bookId)) },
                    )
                }
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
            composable(Routes.BACKUP) {
                BackupScreen(onBack = back, onImport = { navController.navigate(Routes.IMPORT) })
            }
            composable(Routes.IMPORT) { ImportScreen(onBack = back) }
            composable(Routes.FEEDBACK) { FeedbackScreen(onBack = back) }
            composable(Routes.REMINDER) { ReminderScreen(onBack = back) }
            composable(Routes.UPDATE) { UpdateScreen(onBack = back) }
        }
    }
}
