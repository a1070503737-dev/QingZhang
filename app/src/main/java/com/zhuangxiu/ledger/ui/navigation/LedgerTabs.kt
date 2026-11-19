package com.zhuangxiu.ledger.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChartOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
internal val DockHeight = 80.dp
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
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            userScrollEnabled = !editingBook && !imeVisible,
            key = { TABS[it].route },
        ) { page ->
            val semantics = if (page == pager.settledPage) Modifier else Modifier.clearAndSetSemantics {}
            Box(Modifier.fillMaxSize().then(semantics)) {
                content(TABS[page].route, openTab) { editingBook = it }
            }
        }
        if (!editingBook && !imeVisible) {
            LedgerBottomBar(
                current = TABS[pager.currentPage].route,
                onNavigate = openTab,
                modifier = Modifier.align(Alignment.BottomCenter),
                pagePosition = { pager.currentPage + pager.currentPageOffsetFraction },
            )
        }
    }
}

@Composable
fun LedgerBottomBar(
    current: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    pagePosition: (() -> Float)? = null,
) {
    val selected = tabIndexOf(current).coerceAtLeast(0)
    val changeTab by rememberUpdatedState(onNavigate)
    val activeRoute by rememberUpdatedState(current)
    val activePage by rememberUpdatedState(selected)
    val scope = rememberCoroutineScope()
    var scrubPosition by remember { mutableFloatStateOf(Float.NaN) }
    val colors = MaterialTheme.colorScheme
    Box(
        modifier.fillMaxWidth().navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, bottom = DockBottomGap),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = colors.surfaceContainer.copy(alpha = 0.84f),
            contentColor = colors.onSurface,
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.80f)),
            shadowElevation = 6.dp,
        ) {
            BoxWithConstraints(
                Modifier.height(DockHeight).pointerInput(Unit) {
                    var settle: Job? = null
                    fun track(x: Float) {
                        scrubPosition = (x / size.width * TABS.size - 0.5f).coerceIn(0f, TABS.lastIndex.toFloat())
                    }
                    fun finish(index: Int, navigate: Boolean) {
                        if (navigate && TABS[index].route != activeRoute) changeTab(TABS[index].route)
                        settle = scope.launch {
                            animate(scrubPosition, index.toFloat(), animationSpec = tween(260)) { value, _ -> scrubPosition = value }
                            scrubPosition = Float.NaN
                        }
                    }
                    detectHorizontalDragGestures(
                        onDragStart = {
                            settle?.cancel()
                            track(it.x)
                        },
                        onHorizontalDrag = { change, _ -> track(change.position.x) },
                        onDragEnd = { finish(scrubPosition.roundToInt(), navigate = true) },
                        onDragCancel = { finish(activePage, navigate = false) },
                    )
                },
            ) {
                val itemWidth = maxWidth / TABS.size
                val highlightWidth = minOf(itemWidth, 64.dp)
                Box(
                    Modifier.offset {
                        val position = if (scrubPosition.isNaN()) pagePosition?.invoke() ?: selected.toFloat() else scrubPosition
                        val x = itemWidth.toPx() * (position + 0.5f) - highlightWidth.toPx() / 2f
                        IntOffset(x.roundToInt(), 12.dp.roundToPx())
                    }.size(highlightWidth, 32.dp).background(colors.secondaryContainer, CircleShape),
                )
                Row(Modifier.fillMaxSize().selectableGroup()) {
                    TABS.forEachIndexed { index, tab ->
                        val isSelected = index == selected
                        Column(
                            Modifier.weight(1f).fillMaxHeight().selectable(
                                selected = isSelected,
                                role = Role.Tab,
                                onClick = { if (tab.route != current) onNavigate(tab.route) },
                            ).padding(top = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(Modifier.height(32.dp), contentAlignment = Alignment.Center) {
                                Icon(tab.icon, null, tint = if (isSelected) colors.onSecondaryContainer else colors.onSurfaceVariant)
                            }
                            Text(
                                tab.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) colors.onSurface else colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
