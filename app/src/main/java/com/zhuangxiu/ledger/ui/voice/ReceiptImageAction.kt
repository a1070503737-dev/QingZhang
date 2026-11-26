package com.zhuangxiu.ledger.ui.voice

import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhuangxiu.ledger.data.db.BookEntity
import com.zhuangxiu.ledger.ui.components.LedgerButton
import com.zhuangxiu.ledger.ui.edit.CameraCaptureDialog
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReceiptImageAction(
    book: BookEntity,
    enabled: Boolean,
    onOpen: () -> Unit,
    model: VoiceEntryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val review by model.state.collectAsStateWithLifecycle()
    var sources by rememberSaveable { mutableStateOf(false) }
    var camera by rememberSaveable { mutableStateOf(false) }
    fun picked(uri: Uri?) {
        if (uri != null) model.openImage(book, uri)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), onResult = ::picked)
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), onResult = ::picked)
    LedgerButton(
        onClick = {
            onOpen()
            sources = true
        },
        enabled = enabled && review.bookName == null,
        tint = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(Icons.Outlined.ImageSearch, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("图片记账", maxLines = 1)
    }
    if (sources) {
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
                        sources = false
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("从相册选图") }
                LedgerButton(
                    onClick = {
                        sources = false
                        files.launch(arrayOf("image/*"))
                    },
                    prominent = false,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("从文件选图") }
                if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
                    LedgerButton(
                        onClick = {
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
                if (camera) model.openImage(book, Uri.fromFile(file), file) else file.delete()
                camera = false
            },
            onDismiss = { camera = false },
        )
    }
    if (review.bookName != null) VoiceReviewSheet(review, model)
}
