package com.zhuangxiu.ledger.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhuangxiu.ledger.data.db.AttachmentEntity
import com.zhuangxiu.ledger.data.repository.ExpenseRepository
import com.zhuangxiu.ledger.data.repository.ImageStore
import com.zhuangxiu.ledger.util.Dates
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** 凭证库里的一张图片；index 是它在“全部图片（时间倒序）”里的下标 */
data class GalleryImage(val index: Int, val attachment: AttachmentEntity)

/** 同一个月添加的图片 */
data class GalleryMonth(val month: YearMonth, val images: List<GalleryImage>)

data class GalleryUiState(
    val loading: Boolean = true,
    val months: List<GalleryMonth> = emptyList(),
    val count: Int = 0,
)

@HiltViewModel
class GalleryViewModel @Inject constructor(
    expenseRepository: ExpenseRepository,
    private val imageStore: ImageStore,
) : ViewModel() {

    val uiState: StateFlow<GalleryUiState> = expenseRepository.observeAllAttachments()
        .map { list ->
            val images = list.mapIndexed { index, item -> GalleryImage(index, item.attachment) }
            GalleryUiState(
                loading = false,
                months = images
                    .groupBy { YearMonth.from(LocalDate.ofEpochDay(Dates.millisToEpochDay(it.attachment.createdAt))) }
                    .map { (month, items) -> GalleryMonth(month, items) },
                count = images.size,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GalleryUiState())

    fun imageFile(fileName: String): File = imageStore.file(fileName)
}
