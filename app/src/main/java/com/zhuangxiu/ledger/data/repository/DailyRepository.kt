package com.zhuangxiu.ledger.data.repository

import androidx.room.withTransaction
import com.zhuangxiu.ledger.data.db.*
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DailyRepository @Inject constructor(private val db: AppDatabase) {
    private val dao get() = db.dailyDao()
    val books = dao.observeBooks()
    fun observeRange(book: Long, from: Long, until: Long) = dao.observeRange(book, from, until)
    suspend fun get(id: String) = dao.get(id)

    suspend fun save(entry: DailyEntryEntity, editing: Boolean) = db.withTransaction {
        require(entry.amountCents in 1..99_999_999_999L) { "请输入大于 0 且不超过 999,999,999.99 的金额" }
        require(entry.category.kind == entry.kind) { "分类与收支类型不匹配" }
        require(entry.minute in 0..1439 && LocalDate.ofEpochDay(entry.day).year in 1..9999) { "日期时间不正确" }
        require(entry.note.length <= 1000) { "备注最多 1000 字" }
        require(dao.book(entry.bookId)?.kind == BookKind.DAILY) { "请选择生活类型账本" }
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
    suspend fun addBook(name: String): Long = db.withTransaction {
        val trimmed = validName(name)
        require(dao.books().none { it.name == trimmed }) { "账本名称已存在" }
        dao.insertBook(BookEntity(name = trimmed, kind = BookKind.DAILY))
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
