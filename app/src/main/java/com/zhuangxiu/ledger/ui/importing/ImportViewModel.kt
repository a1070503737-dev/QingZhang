package com.zhuangxiu.ledger.ui.importing

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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

data class ImportFile(
    val name: String,
    val document: ImportDocument,
    val sheet: Int,
    val header: Int,
    val columns: Map<ImportField, Int>,
    val signedAmounts: Boolean = false,
) {
    val table get() = document.sheets[sheet]
    val source get() = ImportSource(document.hash, "$sheet:${table.name}", "$header:$signedAmounts:" +
        columns.entries.sortedBy { it.key.ordinal }.joinToString(",") { "${it.key.name}=${it.value}" })
}

data class ImportFileError(val name: String, val reason: String)

data class ImportState(
    val files: List<ImportFile> = emptyList(),
    val fileIndex: Int = 0,
    val fileErrors: List<ImportFileError> = emptyList(),
    val duplicateFiles: Int = 0,
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
    val file get() = files.getOrNull(fileIndex)
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
    private var prepared: List<ImportInput> = emptyList()

    fun open(uris: List<Uri>) = run {
        prepared = emptyList()
        _state.update { it.copy(files = emptyList(), fileErrors = emptyList(), duplicateFiles = 0,
            fileIndex = 0, preview = null, receipt = null, skipInvalid = false, skipPossible = false) }
        val files = mutableListOf<ImportFile>()
        val errors = mutableListOf<ImportFileError>()
        val hashes = hashSetOf<String>()
        var duplicateFiles = 0
        var retainedRows = 0
        withContext(Dispatchers.IO) {
            uris.distinct().forEachIndexed { index, uri ->
                val name = fileName(uri, index)
                try {
                    val doc = requireNotNull(context.contentResolver.openInputStream(uri)) { "无法打开文件" }
                        .use(SharkImport::read)
                    if (doc.hash in hashes) {
                        duplicateFiles++
                        return@forEachIndexed
                    }
                    val rowCount = doc.sheets.sumOf { it.rows.size }
                    require(retainedRows + rowCount <= MAX_ROWS + 100) { "选中的文件超过单批 $MAX_ROWS 笔的上限，请分批导入" }
                    val sheet = doc.sheets.indexOfFirst {
                        ImportField.DATE in it.autoMapping && ImportField.AMOUNT in it.autoMapping
                    }.coerceAtLeast(0)
                    val table = doc.sheets[sheet]
                    files += ImportFile(name, doc, sheet, table.headerIndex, table.autoMapping)
                    hashes += doc.hash
                    retainedRows += rowCount
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    errors += ImportFileError(name, e.message ?: "无法读取文件")
                }
            }
        }
        _state.update { it.copy(files = files, fileErrors = errors, duplicateFiles = duplicateFiles) }
    }

    private fun fileName(uri: Uri, index: Int): String {
        val displayName = try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        return displayName?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: "文件 ${index + 1}"
    }

    fun change(transform: (ImportState) -> ImportState) {
        if (_state.value.busy) return
        prepared = emptyList()
        _state.update { transform(it).copy(preview = null, receipt = null, error = null) }
    }

    fun selectFile(index: Int) = options { it.copy(fileIndex = index) }

    fun removeFile() = change { state ->
        val remaining = state.files.filterIndexed { index, _ -> index != state.fileIndex }
        state.copy(files = remaining, fileIndex = state.fileIndex.coerceAtMost(remaining.lastIndex.coerceAtLeast(0)))
    }

    fun changeFile(transform: (ImportFile) -> ImportFile) = change { state ->
        state.copy(files = state.files.mapIndexed { index, file -> if (index == state.fileIndex) transform(file) else file })
    }

    fun sheet(index: Int) = changeFile { file ->
        val table = file.document.sheets[index]
        file.copy(sheet = index, header = table.headerIndex, columns = table.autoMapping)
    }

    fun header(index: Int) = changeFile { file ->
        file.copy(header = index, columns = guessColumns(file.table.rows[index].cells))
    }

    fun options(transform: (ImportState) -> ImportState) {
        if (!_state.value.busy) _state.update(transform)
    }

    fun preview() = run {
        val state = _state.value
        require(state.files.isNotEmpty()) { "请选择账单文件" }
        require(state.target == null || books.value.any { it.id == state.target }) { "请选择使用中的目标账本" }
        if (state.target == null) require(state.newName.trim().isNotEmpty()) { "请填写新账本名称" }
        val data = withContext(Dispatchers.Default) {
            state.files.mapIndexed { index, file ->
                val parsed = try {
                    SharkImport.parse(file.table, file.header, file.columns, file.signedAmounts)
                } catch (e: IllegalArgumentException) {
                    _state.update { it.copy(fileIndex = index, preview = null) }
                    throw IllegalArgumentException("${file.name}：${e.message}", e)
                }
                ImportInput(file.source, parsed, file.name)
            }
        }
        require(data.sumOf { it.parsed.rows.size + it.parsed.issues.size } <= MAX_ROWS) {
            "单批最多导入 $MAX_ROWS 笔账单，请分批导入"
        }
        val preview = repository.preview(state.target, data)
        prepared = data
        _state.update { it.copy(preview = preview, categories = preview.categories, skipInvalid = false, skipPossible = false) }
    }

    fun commit() = run {
        val state = _state.value
        val result = repository.commit(state.target, state.newName, prepared,
            requireNotNull(state.preview), state.categories, state.skipInvalid, state.skipPossible)
        _state.update { it.copy(receipt = result, preview = null) }
        prepared = emptyList()
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
