package com.zhuangxiu.ledger.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

enum class BookKind { DAILY, RENOVATION }
enum class EntryKind(val label: String) { EXPENSE("支出"), INCOME("收入") }
enum class DailyCategory(val label: String, val kind: EntryKind) {
    FOOD("餐饮", EntryKind.EXPENSE), SHOPPING("购物", EntryKind.EXPENSE), TRANSPORT("交通", EntryKind.EXPENSE),
    HOME("住房", EntryKind.EXPENSE), UTILITIES("生活缴费", EntryKind.EXPENSE), FUN("娱乐", EntryKind.EXPENSE),
    HEALTH("医疗", EntryKind.EXPENSE), PHONE("通讯", EntryKind.EXPENSE), OTHER_EXPENSE("其他", EntryKind.EXPENSE),
    SALARY("工资", EntryKind.INCOME), BONUS("奖金", EntryKind.INCOME), SIDE_JOB("兼职", EntryKind.INCOME),
    GIFT("红包", EntryKind.INCOME), REFUND("退款", EntryKind.INCOME), OTHER_INCOME("其他", EntryKind.INCOME),
}
enum class PayMethod(val label: String) { WECHAT("微信"), ALIPAY("支付宝"), BANK("银行卡"), CASH("现金"), OTHER("其他") }

@Entity(tableName = "books")
data class BookEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val kind: BookKind)

/** 日期采用用户选择的当地日历日和分钟，不因旅行或时区变化挪到其他统计月份。金额始终为正数分。 */
@Entity(tableName = "daily_entries", foreignKeys = [ForeignKey(entity = BookEntity::class,
    parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["bookId", "day", "minute"])])
data class DailyEntryEntity(
    @PrimaryKey val id: String,
    val bookId: Long,
    val amountCents: Long,
    val kind: EntryKind,
    val category: DailyCategory,
    val day: Long,
    val minute: Int,
    val payMethod: PayMethod,
    val note: String = "",
)

@Dao
interface DailyDao {
    @Query("SELECT * FROM books ORDER BY id") fun observeBooks(): Flow<List<BookEntity>>
    @Query("SELECT * FROM books ORDER BY id") suspend fun books(): List<BookEntity>
    @Query("SELECT * FROM books WHERE id = :id") suspend fun book(id: Long): BookEntity?
    @Insert suspend fun insertBook(book: BookEntity): Long
    @Update suspend fun updateBook(book: BookEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun copyBook(book: BookEntity): Long
    @Query("SELECT * FROM daily_entries WHERE bookId = :bookId AND day >= :from AND day < :until ORDER BY day DESC, minute DESC, id DESC")
    fun observeRange(bookId: Long, from: Long, until: Long): Flow<List<DailyEntryEntity>>
    @Query("SELECT * FROM daily_entries WHERE id = :id") suspend fun get(id: String): DailyEntryEntity?
    @Query("SELECT * FROM daily_entries") suspend fun all(): List<DailyEntryEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(entry: DailyEntryEntity): Long
    @Update suspend fun update(entry: DailyEntryEntity): Int
    @Query("DELETE FROM daily_entries WHERE id = :id") suspend fun delete(id: String): Int
}
