package com.zhuangxiu.ledger.ui.voice

import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zhuangxiu.ledger.data.db.BookEntity
import com.zhuangxiu.ledger.ui.components.LedgerButton
import com.zhuangxiu.ledger.ui.edit.CameraCaptureDialog
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReceiptImageAction(
    book: BookEntity?,
    pickBook: () -> Unit,
    showAfterBookChoice: Boolean,
    onBookChoiceHandled: () -> Unit,
    onImage: (BookEntity, Uri, File?) -> Unit,
    content: @Composable (() -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var sources by rememberSaveable { mutableStateOf(false) }
    var camera by rememberSaveable { mutableStateOf(false) }
    var targetBookId by rememberSaveable { mutableStateOf<Long?>(null) }
    fun picked(uri: Uri?) {
        val target = book?.takeIf { it.id == targetBookId }
        targetBookId = null
        if (target != null && uri != null) onImage(target, uri, null)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), onResult = ::picked)
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), onResult = ::picked)
    LaunchedEffect(showAfterBookChoice, book?.id) {
        if (showAfterBookChoice && book != null) {
            sources = true
            onBookChoiceHandled()
        }
    }
    content { if (book == null) pickBook() else sources = true }
    if (sources && book != null) {
        ModalBottomSheet(
            onDismissRequest = { sources = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("图片记账", style = MaterialTheme.typography.titleLarge)
                Text("选择单笔交易详情截图或小票", style = MaterialTheme.typography.bodyMedium)
                LedgerButton(
                    onClick = {
                        targetBookId = book.id
                        sources = false
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("从相册选图") }
                LedgerButton(
                    onClick = {
                        targetBookId = book.id
                        sources = false
                        files.launch(arrayOf("image/*"))
                    },
                    prominent = false,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("从文件选图") }
                if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
                    LedgerButton(
                        onClick = {
                            targetBookId = book.id
                            sources = false
                            camera = true
                        },
                        prominent = false,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("拍摄小票") }
                }
            }
        }
    }
    if (camera) {
        CameraCaptureDialog(
            createFile = { File(context.cacheDir, "receipt-${UUID.randomUUID()}.jpg") },
            onCaptured = { file ->
                val target = book?.takeIf { camera && it.id == targetBookId }
                camera = false
                targetBookId = null
                if (target != null) onImage(target, Uri.fromFile(file), file) else file.delete()
            },
            onDismiss = {
                camera = false
                targetBookId = null
            },
        )
    }
}
