package com.zhuangxiu.ledger.ui.edit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.zhuangxiu.ledger.data.db.CategoryEntity
import com.zhuangxiu.ledger.data.db.PaymentStatus
import com.zhuangxiu.ledger.ui.components.AmountKeypad
import com.zhuangxiu.ledger.ui.components.GlassVariant
import com.zhuangxiu.ledger.ui.components.LedgerCard
import com.zhuangxiu.ledger.ui.components.LedgerDatePickerDialog
import com.zhuangxiu.ledger.ui.components.LiquidGlassBackdrop
import com.zhuangxiu.ledger.ui.components.LiquidGlassButton
import com.zhuangxiu.ledger.ui.components.LiquidGlassSurface
import com.zhuangxiu.ledger.ui.components.LiquidGlassTopBar
import com.zhuangxiu.ledger.ui.components.LiquidLensChip
import com.zhuangxiu.ledger.ui.components.LiquidLensIconButton
import com.zhuangxiu.ledger.ui.components.PaymentMethodPicker
import com.zhuangxiu.ledger.ui.components.applyAmountKey
import com.zhuangxiu.ledger.ui.components.glassTextFieldColors
import com.zhuangxiu.ledger.ui.components.liquidGlass
import com.zhuangxiu.ledger.ui.theme.LedgerTheme
import com.zhuangxiu.ledger.util.Dates
import com.zhuangxiu.ledger.util.Money

private enum class AmountField { TOTAL, PAID }

@Composable
fun EditScreen(onBack: () -> Unit, viewModel: EditViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val glass = LedgerTheme.glass
    var amountField by rememberSaveable { mutableStateOf<AmountField?>(null) }
    val openAmount: (AmountField) -> Unit = { field ->
        if (!state.saving) {
            keyboard?.hide()
            focusManager.clearFocus()
            amountField = field
        }
    }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showDuePicker by rememberSaveable { mutableStateOf(false) }
    var showCamera by rememberSaveable { mutableStateOf(false) }

    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris ->
        viewModel.onImagesPicked(uris)
    }

    LaunchedEffect(state.saved) { if (state.saved) onBack() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LiquidGlassBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                LiquidGlassTopBar(
                    title = { Text(if (state.isNew) "记装修账" else "编辑装修账单") },
                    navigationIcon = {
                        LiquidLensIconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        LiquidGlassButton(
                            onClick = viewModel::save,
                            enabled = !state.saving && !state.loading,
                            minHeight = 38.dp,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            modifier = Modifier.semantics { contentDescription = "保存账单" },
                        ) { Text("保存") }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            if (state.loading) return@Scaffold
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                LedgerCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "账单总金额" },
                    onClick = { openAmount(AmountField.TOTAL) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Column(Modifier.padding(horizontal = 22.dp, vertical = 18.dp)) {
                        Text(
                            "总价",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("¥", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                state.amountText.ifEmpty { "0.00" },
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = if (state.amountText.isEmpty()) 0.5f else 1f),
                            )
                        }
                    }
                }

                if (state.trackPayments) {
                    FormSection("付款状态") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PaymentStatus.entries.forEach { status ->
                                LiquidLensChip(
                                    selected = state.status == status,
                                    onClick = { viewModel.setStatus(status) },
                                    label = { Text(status.label) },
                                )
                            }
                        }
                        val partial = state.status == PaymentStatus.DEPOSIT || state.status == PaymentStatus.BALANCE_DUE
                        if (state.isNew && partial) {
                            Spacer(Modifier.height(8.dp))
                            PickerRow(
                                icon = Icons.Outlined.Payments,
                                label = paidAmountLabel(state.status),
                                value = "¥${state.paidText.ifEmpty { "0.00" }}",
                                onClick = { openAmount(AmountField.PAID) },
                            )
                            val total = Money.parseToCents(state.amountText)
                            val paid = Money.parseToCents(state.paidText)
                            if (total != null && paid != null && paid < total) {
                                Text("剩余待付 ${Money.format(total - paid)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (!state.isNew) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (state.status == PaymentStatus.PAID) {
                                    "已付 ${Money.format(state.existingPaidCents)}；保存为“已付”会自动补记一笔结清付款"
                                } else {
                                    "已付 ${Money.format(state.existingPaidCents)}；分期付款请在账单详情页登记，状态会随付款记录自动更新"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (state.status != PaymentStatus.PAID) {
                            Spacer(Modifier.height(8.dp))
                            PickerRow(
                                icon = Icons.Outlined.Event,
                                label = "待付到期日",
                                value = state.dueDate?.let(Dates::format) ?: "不设置",
                                onClick = { showDuePicker = true },
                                onClear = if (state.dueDate != null) ({ viewModel.setDueDate(null) }) else null,
                            )
                        }
                    }
                }

                if (state.status != PaymentStatus.UNPAID && (state.isNew ||
                        state.status == PaymentStatus.PAID &&
                        (Money.parseToCents(state.amountText) ?: 0L) > state.existingPaidCents)
                ) {
                    PaymentMethodPicker(state.payMethod, viewModel::setPayMethod)
                }

                PickerRow(
                    icon = Icons.Outlined.CalendarToday,
                    label = "日期",
                    value = Dates.format(state.date),
                    onClick = { showDatePicker = true },
                )

                FormSection("装修阶段") { CategoryChips(state.stages, state.stageId, viewModel::setStage) }
                FormSection("费用类型") { CategoryChips(state.types, state.typeId, viewModel::setType) }

                OutlinedTextField(
                    value = state.payee,
                    onValueChange = viewModel::setPayee,
                    label = { Text("商家 / 工人") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = glassTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.note,
                    onValueChange = viewModel::setNote,
                    label = { Text("备注") },
                    minLines = 2,
                    maxLines = 5,
                    shape = RoundedCornerShape(18.dp),
                    colors = glassTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (state.trackPhotos || state.images.isNotEmpty()) {
                    FormSection("凭证（小票、合同照片）") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            state.images.forEach { image ->
                                Box(
                                    Modifier
                                        .size(84.dp)
                                        .liquidGlass(RoundedCornerShape(16.dp), GlassVariant.Clear, cornerRadius = 16.dp, shadowElevation = 4.dp),
                                ) {
                                    AsyncImage(
                                        model = viewModel.imageFile(image.fileName),
                                        contentDescription = "凭证",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(16.dp)),
                                    )
                                    Box(
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(22.dp)
                                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f), CircleShape)
                                            .clickable { viewModel.removeImage(image) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Outlined.Close,
                                            contentDescription = "移除",
                                            tint = MaterialTheme.colorScheme.surface,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                            }
                            if (state.trackPhotos) {
                                AddImageTile(Icons.Outlined.PhotoCamera, "拍照") { showCamera = true }
                                AddImageTile(Icons.Outlined.PhotoLibrary, "相册") {
                                    pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                            }
                        }
                    }
                }

                LiquidGlassButton(
                    onClick = viewModel::save,
                    enabled = !state.saving,
                    minHeight = 52.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (state.saving) "保存中..." else "保存") }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    amountField?.let { field ->
        val isTotal = field == AmountField.TOTAL
        val amount = if (isTotal) state.amountText else state.paidText
        val close = { amountField = null }
        AlertDialog(
            onDismissRequest = close,
            shape = RoundedCornerShape(30.dp),
            containerColor = glass.elevatedTop.copy(alpha = if (glass.isDark) 0.90f else 0.94f),
            tonalElevation = 0.dp,
            modifier = Modifier.border(BorderStroke(1.dp, glass.specularBright), RoundedCornerShape(30.dp)),
            title = { Text(if (isTotal) "输入装修总价" else paidAmountLabel(state.status)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("¥${amount.ifEmpty { "0.00" }}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    AmountKeypad { key ->
                        val change: (String) -> String = { applyAmountKey(it, key) }
                        if (isTotal) viewModel.updateAmount(change) else viewModel.updatePaid(change)
                    }
                }
            },
            confirmButton = {
                LiquidGlassButton(
                    onClick = close,
                    minHeight = 40.dp,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                ) { Text("完成") }
            },
        )
    }

    if (showDatePicker) {
        LedgerDatePickerDialog(
            initialEpochDay = state.date,
            onPick = {
                viewModel.setDate(it)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
    if (showDuePicker) {
        LedgerDatePickerDialog(
            initialEpochDay = state.dueDate,
            onPick = {
                viewModel.setDueDate(it)
                showDuePicker = false
            },
            onDismiss = { showDuePicker = false },
        )
    }
    if (showCamera) {
        CameraCaptureDialog(
            createFile = viewModel::newImageFile,
            onCaptured = {
                viewModel.onPhotoTaken(it)
                showCamera = false
            },
            onDismiss = { showCamera = false },
        )
    }
}

private fun paidAmountLabel(status: PaymentStatus): String =
    if (status == PaymentStatus.DEPOSIT) "已付定金" else "已付金额"

@Composable
private fun FormSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun CategoryChips(categories: List<CategoryEntity>, selectedId: Long?, onSelect: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.forEach { category ->
            LiquidLensChip(
                selected = category.id == selectedId,
                onClick = { onSelect(category.id) },
                label = { Text(category.name) },
            )
        }
    }
}

@Composable
private fun PickerRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    LiquidGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        cornerRadius = 18.dp,
        variant = GlassVariant.Regular,
        shadowElevation = 3.dp,
        pressedScale = 0.984f,
        onClick = onClick,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onClear != null) {
                Spacer(Modifier.width(8.dp))
                LiquidLensIconButton(onClick = onClear, size = 28.dp) {
                    Icon(Icons.Outlined.Close, contentDescription = "清除", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun AddImageTile(icon: ImageVector, label: String, onClick: () -> Unit) {
    LiquidGlassButton(
        onClick = onClick,
        prominent = false,
        shape = RoundedCornerShape(18.dp),
        cornerRadius = 18.dp,
        minHeight = 84.dp,
        contentPadding = PaddingValues(12.dp),
        modifier = Modifier.size(84.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
