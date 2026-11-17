package com.zhuangxiu.ledger.ui.importing

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhuangxiu.ledger.data.repository.DailyRepository
import com.zhuangxiu.ledger.importing.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ImportState(
    val document: ImportDocument? = null,
    val sheet: Int = 0,
    val header: Int = 0,
    val columns: Map<ImportField, Int> = emptyMap(),
    val signedAmounts: Boolean = false,
    val target: Long? = 1,
    val newName: String = "",
    val preview: ImportPreview? = null,
    val categories: Map<ImportCategory, String> = emptyMap(),
    val skipPossible: Boolean = false,
    val skipInvalid: Boolean = false,
    val receipt: ImportReceipt? = null,
    val busy: Boolean = false,
    val error: String? = null,
) {
    val table get() = document?.sheets?.getOrNull(sheet)
    val source get() = document?.let {
        ImportSource(it.hash, "$sheet:${table?.name}", "$header:$signedAmounts:" +
            columns.entries.sortedBy { col -> col.key.ordinal }.joinToString(",") { col -> "${col.key.name}=${col.value}" })
    }
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: ImportRepository,
    books: DailyRepository,
) : ViewModel() {
    val books = books.books.map { list -> list.filterNot { it.archived } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _state = MutableStateFlow(ImportState())
    val state = _state.asStateFlow()
    private var parsed: ParsedImport? = null

    fun open(uri: Uri) = run {
        parsed = null
        _state.update { it.copy(document = null, preview = null, receipt = null) }
        val doc = withContext(Dispatchers.IO) {
            requireNotNull(context.contentResolver.openInputStream(uri)) { "无法打开文件" }.use(SharkImport::read)
        }
        val index = doc.sheets.indexOfFirst { ImportField.DATE in it.autoMapping && ImportField.AMOUNT in it.autoMapping }.coerceAtLeast(0)
        val table = doc.sheets[index]
        _state.update { it.copy(document = doc, sheet = index, header = table.headerIndex, columns = table.autoMapping,
            signedAmounts = false, preview = null, receipt = null, skipInvalid = false, skipPossible = false) }
    }

    fun change(transform: (ImportState) -> ImportState) {
        if (_state.value.busy) return
        parsed = null
        _state.update { transform(it).copy(preview = null, receipt = null, error = null) }
    }

    fun sheet(index: Int) = change { state ->
        val table = requireNotNull(state.document).sheets[index]
        state.copy(sheet = index, header = table.headerIndex, columns = table.autoMapping)
    }

    fun header(index: Int) = change { state ->
        val cols = state.table?.rows?.getOrNull(index)?.cells.orEmpty()
        state.copy(header = index, columns = guessColumns(cols))
    }

    fun options(transform: (ImportState) -> ImportState) {
        if (!_state.value.busy) _state.update(transform)
    }

    fun preview() = run {
        val state = _state.value
        val table = requireNotNull(state.table) { "请选择账单文件" }
        require(state.target == null || books.value.any { it.id == state.target }) { "请选择使用中的目标账本" }
        if (state.target == null) require(state.newName.trim().isNotEmpty()) { "请填写新账本名称" }
        val data = withContext(Dispatchers.Default) { SharkImport.parse(table, state.header, state.columns, state.signedAmounts) }
        val preview = repository.preview(state.target, requireNotNull(state.source), data)
        parsed = data
        _state.update { it.copy(preview = preview, categories = preview.categories, skipInvalid = false, skipPossible = false) }
    }

    fun commit() = run {
        val state = _state.value
        val result = repository.commit(state.target, state.newName, requireNotNull(state.source),
            requireNotNull(parsed), requireNotNull(state.preview), state.categories, state.skipInvalid, state.skipPossible)
        _state.update { it.copy(receipt = result, preview = null) }
    }

    private fun run(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "读取或导入失败，请检查文件") }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
