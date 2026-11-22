package com.zhuangxiu.ledger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp


sealed interface SurfaceTone {
    data object Standard : SurfaceTone
    data object Raised : SurfaceTone
    data object Flat : SurfaceTone
    data class Accent(val color: Color) : SurfaceTone
}

@Composable
fun Modifier.ledgerContainer(shape: Shape, variant: SurfaceTone = SurfaceTone.Standard): Modifier {
    val colors = MaterialTheme.colorScheme
    val color = when (variant) {
        SurfaceTone.Standard -> colors.surfaceContainerLow
        SurfaceTone.Raised -> colors.surfaceContainerHigh
        SurfaceTone.Flat -> Color.Transparent
        is SurfaceTone.Accent -> variant.color
    }
    return clip(shape).background(color)
}

@Composable
fun LedgerBackground(
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        content()
        overlay()
    }
}

@Composable
fun LedgerPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    variant: SurfaceTone = SurfaceTone.Standard,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    role: Role? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val touch = when {
        onClick != null && onLongClick != null -> Modifier.combinedClickable(
            enabled = enabled,
            role = role,
            onLongClickLabel = onLongClickLabel,
            onLongClick = onLongClick,
            onClick = onClick,
        )
        onClick != null -> Modifier.clickable(enabled = enabled, role = role, onClick = onClick)
        else -> Modifier
    }
    Column(modifier.ledgerContainer(shape, variant).then(touch), content = content)
}

@Composable
fun LedgerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    prominent: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = if (prominent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
    shape: Shape = CircleShape,
    minHeight: Dp = 48.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
    hapticFeedback: HapticFeedbackType? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    Button(
        onClick = {
            hapticFeedback?.let(haptics::performHapticFeedback)
            onClick()
        },
        modifier = modifier.defaultMinSize(minHeight = minHeight),
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (prominent) tint else colors.secondaryContainer,
            contentColor = contentColor,
        ),
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
fun LedgerIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 40.dp,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier.size(maxOf(48.dp, size)).clip(CircleShape).clickable(
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
        ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides tint.copy(alpha = if (enabled) 1f else 0.38f)) {
            content()
        }
    }
}

@Composable
fun LedgerChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val foreground = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant
    CompositionLocalProvider(LocalContentColor provides foreground.copy(alpha = if (enabled) 1f else 0.38f)) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
            Row(
                modifier.defaultMinSize(minHeight = 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) colors.secondaryContainer else colors.surfaceContainerLow)
                    .toggleable(value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onClick() })
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leadingIcon?.invoke()
                label()
                trailingIcon?.invoke()
            }
        }
    }
}

internal fun Modifier.swipeToSelect(itemCount: Int, enabled: Boolean = true, onSelect: (Int) -> Unit): Modifier = composed {
    val select by rememberUpdatedState(onSelect)
    if (!enabled || itemCount < 2) return@composed this
    pointerInput(itemCount) {
        var x = 0f
        detectHorizontalDragGestures(
            onDragStart = { x = it.x },
            onHorizontalDrag = { change, _ -> x = change.position.x },
            onDragEnd = {
                if (size.width > 0) select((x / size.width * itemCount).toInt().coerceIn(0, itemCount - 1))
            },
        )
    }
}

@Composable
fun LedgerSegments(
    itemCount: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 62.dp,
    role: Role = Role.Tab,
    enabled: Boolean = true,
    itemContent: @Composable BoxScope.(index: Int, isSelected: Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(colors.surfaceContainerLow)
            .swipeToSelect(itemCount, enabled, onSelect)
            .selectableGroup()
            .padding(2.dp),
    ) {
        repeat(itemCount) { index ->
            val selected = index == selectedIndex
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                    .background(if (selected) colors.secondaryContainer else Color.Transparent)
                    .selectable(selected = selected, enabled = enabled, role = role, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center,
            ) {
                itemContent(index, selected)
            }
        }
    }
}

@Composable
fun LedgerTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .defaultMinSize(minHeight = 64.dp)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        navigationIcon?.invoke()
        Box(Modifier.weight(1f).padding(start = if (navigationIcon == null) 8.dp else 0.dp)) {
            ProvideTextStyle(MaterialTheme.typography.titleLarge, title)
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}
