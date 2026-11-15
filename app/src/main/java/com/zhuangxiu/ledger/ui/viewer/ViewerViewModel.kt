package com.zhuangxiu.ledger.ui.viewer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhuangxiu.ledger.data.db.AttachmentEntity
import com.zhuangxiu.ledger.data.repository.ExpenseRepository
import com.zhuangxiu.ledger.data.repository.ImageStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ViewerUiState(
    val loading: Boolean = true,
    val images: List<AttachmentEntity> = emptyList(),
)

@HiltViewModel
class ViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val expenseRepository: ExpenseRepository,
    private val imageStore: ImageStore,
) : ViewModel() {

    /** -1 表示从凭证库进入，查看全部图片 */
    val expenseId: Long = savedStateHandle.get<Long>("expenseId") ?: -1L
    val startIndex: Int = savedStateHandle.get<Int>("index") ?: 0

    val uiState: StateFlow<ViewerUiState> = (
        if (expenseId > 0) {
            expenseRepository.observe(expenseId).map { it?.attachments.orEmpty() }
        } else {
            // 顺序必须和凭证库页面一致（按时间倒序），这样传入的下标才对得上
            expenseRepository.observeAllAttachments().map { list -> list.map { it.attachment } }
        }
        )
        .map { ViewerUiState(loading = false, images = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ViewerUiState())

    fun imageFile(fileName: String): File = imageStore.file(fileName)

    fun delete(attachment: AttachmentEntity) {
        viewModelScope.launch { expenseRepository.deleteAttachment(attachment.id) }
    }
}
