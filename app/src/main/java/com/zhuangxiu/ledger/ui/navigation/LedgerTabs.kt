package com.zhuangxiu.ledger.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChartOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class Tab(val route: String, val label: String, val icon: ImageVector)

internal val TABS = listOf(
    Tab(Routes.HOME, "首页", Icons.Outlined.Home),
    Tab(Routes.STATS, "统计", Icons.Outlined.PieChartOutline),
    Tab(Routes.BOOKS, "账本", Icons.Outlined.AccountBalanceWallet),
)

internal fun tabIndexOf(route: String?): Int = TABS.indexOfFirst { it.route == route }
internal fun isTabHost(route: String?): Boolean = route == Routes.HOME || route == Routes.STATS || route == Routes.BOOKS
internal const val RequestedTabKey = "requestedTab"
internal val DockHeight = 66.dp
internal val DockBottomGap = 10.dp

@Composable
internal fun LedgerTabPages(
    entry: NavBackStackEntry,
    initialRoute: String,
    content: @Composable (route: String, openTab: (String) -> Unit, onEditingBook: (Boolean) -> Unit) -> Unit,
) {
    val pager = rememberPagerState(initialPage = tabIndexOf(initialRoute).coerceAtLeast(0)) { TABS.size }
    val request by entry.savedStateHandle.getStateFlow<String?>(RequestedTabKey, null).collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editingBook by rememberSaveable { mutableStateOf(false) }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val dockBackdrop = rememberDockBackdrop()
    val showDock = !editingBook && !imeVisible
    val openTab: (String) -> Unit = { route ->
        val index = tabIndexOf(route)
        if (index >= 0) {
            scope.launch { pager.animateScrollToPage(index, animationSpec = tween(260)) }
        }
    }
    LaunchedEffect(request) {
        val index = tabIndexOf(request)
        if (index >= 0) {
            entry.savedStateHandle.set<String?>(RequestedTabKey, null)
            openTab(TABS[index].route)
        }
    }
    BackHandler(enabled = initialRoute == Routes.HOME && pager.settledPage != 0 && !editingBook) {
        openTab(Routes.HOME)
    }
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize().recordDockBackdrop(if (showDock) dockBackdrop else null),
            beyondViewportPageCount = 1,
            userScrollEnabled = !editingBook && !imeVisible,
            key = { TABS[it].route },
        ) { page ->
            val semantics = if (page == pager.settledPage) Modifier else Modifier.clearAndSetSemantics {}
            Box(Modifier.fillMaxSize().then(semantics)) {
                content(TABS[page].route, openTab) { editingBook = it }
            }
        }
        if (showDock) {
            LedgerBottomBar(
                current = TABS[pager.currentPage].route,
                onNavigate = openTab,
                modifier = Modifier.align(Alignment.BottomCenter),
                pagePosition = { pager.currentPage + pager.currentPageOffsetFraction },
                backdrop = dockBackdrop,
            )
        }
    }
}

@Composable
internal fun LedgerBottomBar(
    current: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    pagePosition: (() -> Float)? = null,
    backdrop: DockBackdrop? = null,
) {
    val selected = tabIndexOf(current).coerceAtLeast(0)
    val changeTab by rememberUpdatedState(onNavigate)
    val activeRoute by rememberUpdatedState(current)
    val activePage by rememberUpdatedState(selected)
    val scope = rememberCoroutineScope()
    var scrubPosition by remember { mutableFloatStateOf(Float.NaN) }
    var dragging by remember { mutableStateOf(false) }
    val lift by animateFloatAsState(if (dragging) 1f else 0f, tween(175), label = "Dock lens lift")
    val position = { if (scrubPosition.isNaN()) pagePosition?.invoke() ?: selected.toFloat() else scrubPosition }
    val direction = LocalLayoutDirection.current
    val colors = MaterialTheme.colorScheme
    val activeTint = lerp(colors.onSurface, colors.primary, 0.65f)
    Box(
        modifier.fillMaxWidth().navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, bottom = DockBottomGap),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.widthIn(max = 480.dp).fillMaxWidth().height(DockHeight)
                .shadow(5.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .pointerInput(direction) {
                    var settle: Job? = null
                    fun track(x: Float) {
                        val fromStart = if (direction == LayoutDirection.Ltr) x else size.width - x
                        scrubPosition = (fromStart / size.width * TABS.size - 0.5f)
                            .coerceIn(0f, TABS.lastIndex.toFloat())
                    }
                    fun finish(index: Int, navigate: Boolean) {
                        dragging = false
                        if (navigate && TABS[index].route != activeRoute) changeTab(TABS[index].route)
                        settle = scope.launch {
                            animate(scrubPosition, index.toFloat(), animationSpec = tween(260)) { value, _ ->
                                scrubPosition = value
                            }
                            scrubPosition = Float.NaN
                        }
                    }
                    detectHorizontalDragGestures(
                        onDragStart = {
                            settle?.cancel()
                            dragging = true
                            track(it.x)
                        },
                        onHorizontalDrag = { change, _ -> track(change.position.x) },
                        onDragEnd = { finish(scrubPosition.roundToInt(), navigate = true) },
                        onDragCancel = { finish(activePage, navigate = false) },
                    )
                },
        ) {
            LiquidGlassDock(backdrop, position, lift = { lift }, modifier = Modifier.matchParentSize())
            Row(Modifier.fillMaxSize().selectableGroup()) {
                val visualPosition = position()
                TABS.forEachIndexed { index, tab ->
                    val focus = (1f - abs(visualPosition - index)).coerceIn(0f, 1f)
                    val tint = lerp(colors.onSurfaceVariant, activeTint, focus)
                    Column(
                        Modifier.weight(1f).fillMaxHeight().selectable(
                            selected = index == selected,
                            role = Role.Tab,
                            onClick = { if (tab.route != current) onNavigate(tab.route) },
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                    ) {
                        Icon(tab.icon, null, tint = tint, modifier = Modifier.size(23.dp))
                        Text(
                            tab.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = tint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
