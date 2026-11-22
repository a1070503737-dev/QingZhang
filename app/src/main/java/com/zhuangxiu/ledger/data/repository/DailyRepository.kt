package com.zhuangxiu.ledger.data.repository

import androidx.room.withTransaction
import com.zhuangxiu.ledger.data.db.*
import java.time.LocalDate
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DailyRepository @Inject constructor(private val db: AppDatabase) {
    private val dao get() = db.dailyDao()
    val books = dao.observeBooks()
    fun observeRange(book: Long, from: Long, until: Long) = dao.observeRange(book, from, until)
    fun observeActual(book: Long, from: Long, until: Long) =
        combine(dao.observeActual(book, from, until), dao.observeProjectPayments(book, from, until)) { entries, payments ->
            entries + payments.map(ProjectPayment::actualEntry)
        }
    suspend fun get(id: String) = dao.get(id)
    suspend fun book(id: Long) = dao.book(id)
    suspend fun categories() = dao.dailyCategories()
    suspend fun categoriesForBook(bookId: Long) = dao.usedCategories(bookId)

    suspend fun save(entry: DailyEntryEntity, editing: Boolean) = db.withTransaction {
        require(entry.amountCents in 1..99_999_999_999L) { "请输入大于 0 且不超过 999,999,999.99 的金额" }
        require(entry.category.kind == entry.kind) { "分类与收支类型不匹配" }
        require(entry.minute in -1..1439 && LocalDate.ofEpochDay(entry.day).year in 1..9999) { "日期时间不正确" }
        require(entry.note.length <= 1000) { "备注最多 1000 字" }
        val book = requireNotNull(dao.book(entry.bookId)) { "账本不存在" }
        require(editing || !book.archived) { "请先恢复已归档的账本" }
        if (editing) {
            val current = requireNotNull(dao.get(entry.id)) { "这笔记录已被删除" }
            require(current.bookId == entry.bookId) { "不能修改记录所属账本" }
            check(dao.update(entry) == 1)
        } else {
            // 同一编辑会话的 UUID 不变，重复提交不会新增第二笔。
            dao.insert(entry)
        }
    }
    suspend fun delete(id: String) { dao.delete(id) }

    suspend fun deleteFromActiveBook(id: String): Unit = db.withTransaction {
        val entry = dao.get(id) ?: return@withTransaction
        require(dao.book(entry.bookId)?.archived == false) { "请先恢复已归档的账本" }
        dao.delete(id)
    }

    suspend fun addBook(name: String, kind: BookKind = BookKind.DAILY,
                        trackPayments: Boolean = kind == BookKind.RENOVATION, trackPhotos: Boolean = kind == BookKind.RENOVATION): Long = db.withTransaction {
        val trimmed = validName(name)
        require(dao.books().none { it.name == trimmed }) { "账本名称已存在" }
        dao.insertBook(BookEntity(name = trimmed, kind = kind, trackPayments = trackPayments, trackPhotos = trackPhotos))
    }
    suspend fun editBook(book: BookEntity) = db.withTransaction {
        val current = requireNotNull(dao.book(book.id))
        val name = validName(book.name)
        require(dao.books().none { it.id != book.id && it.name == name }) { "账本名称已存在" }
        dao.updateBook(current.copy(name = name, archived = book.archived,
            trackPayments = current.kind == BookKind.RENOVATION && book.trackPayments,
            trackPhotos = current.kind == BookKind.RENOVATION && book.trackPhotos))
    }
    suspend fun renameBook(id: Long, name: String) = db.withTransaction {
        val trimmed = validName(name)
        require(dao.books().none { it.id != id && it.name == trimmed }) { "账本名称已存在" }
        dao.updateBook(requireNotNull(dao.book(id)).copy(name = trimmed))
    }
    private fun validName(name: String): String = name.trim().also {
        require(it.isNotEmpty() && it.length <= 20) { "账本名称需要 1–20 个字" }
    }
}
