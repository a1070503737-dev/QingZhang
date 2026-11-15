package com.zhuangxiu.ledger.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhuangxiu.ledger.data.db.ExpenseWithDetails
import com.zhuangxiu.ledger.data.db.PaymentEntity
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

data class DetailUiState(
    val loading: Boolean = true,
    /** 账单被删除后为 null */
    val item: ExpenseWithDetails? = null,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val expenseRepository: ExpenseRepository,
    private val imageStore: ImageStore,
) : ViewModel() {

    val expenseId: Long = checkNotNull(savedStateHandle.get<Long>("id"))

    val uiState: StateFlow<DetailUiState> = expenseRepository.observe(expenseId)
        .map { DetailUiState(loading = false, item = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    fun imageFile(fileName: String): File = imageStore.file(fileName)

    /** 登记一次付款 */
    fun addPayment(amountCents: Long, date: Long) {
        viewModelScope.launch { expenseRepository.addPayment(expenseId, amountCents, date) }
    }

    fun deletePayment(payment: PaymentEntity) {
        viewModelScope.launch { expenseRepository.deletePayment(payment) }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            expenseRepository.delete(expenseId)
            onDone()
        }
    }
}
