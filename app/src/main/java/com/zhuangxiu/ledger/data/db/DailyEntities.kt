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
enum class PayMethod(val label: String) { WECHAT("微信"), ALIPAY("支付宝"), BANK("银行卡"), CASH("现金"), OTHER("其他"), UNSPECIFIED("未指定") }

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: BookKind,
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
    @ColumnInfo(defaultValue = "1") val trackPayments: Boolean = kind == BookKind.RENOVATION,
    @ColumnInfo(defaultValue = "1") val trackPhotos: Boolean = kind == BookKind.RENOVATION,
)

/** 日期采用用户选择的当地日历日和分钟，不因旅行或时区变化挪到其他统计月份。金额始终为正数分。 */
@Entity(tableName = "daily_entries", foreignKeys = [ForeignKey(entity = BookEntity::class,
    parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["bookId", "day", "minute"]), Index("bookId", "importFingerprint")])
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
    val categoryName: String? = null,
    val sourceCategory: String? = null,
    val account: String? = null,
    val sourceMethod: String? = null,
    val importFingerprint: String? = null,
    val importBatchId: String? = null,
) {
    val displayCategory get() = categoryName ?: category.label
    val displayPayment get() = listOfNotNull(account, sourceMethod).distinct().joinToString(" · ").ifEmpty { payMethod.label }
}

@Entity(tableName = "daily_categories", primaryKeys = ["kind", "name"])
data class DailyCategoryEntity(val kind: EntryKind, val name: String)

@Entity(tableName = "import_batches", indices = [Index("bookId", "fileHash")])
data class ImportBatchEntity(
    @PrimaryKey val id: String,
    val bookId: Long,
    val fileHash: String,
    val sheet: String,
    val mapping: String,
    val importedAt: Long,
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
    @Query("SELECT * FROM daily_entries WHERE (:bookId = 0 OR bookId = :bookId) AND day >= :from AND day < :until")
    fun observeActual(bookId: Long, from: Long, until: Long): Flow<List<DailyEntryEntity>>
    @Query("SELECT p.id AS id, e.id AS expenseId, e.bookId AS bookId, p.amountCents AS amountCents, p.date AS day, p.payMethod AS payMethod, c.name AS categoryName, CASE WHEN p.note = '' THEN e.note ELSE p.note END AS note FROM payments p JOIN expenses e ON p.expenseId = e.id JOIN categories c ON c.id = e.typeId JOIN books b ON b.id = e.bookId WHERE (:bookId = 0 OR e.bookId = :bookId) AND p.date >= :from AND p.date < :until AND p.amountCents > 0")
    fun observeProjectPayments(bookId: Long, from: Long, until: Long): Flow<List<ProjectPayment>>
    @Query("SELECT * FROM daily_entries WHERE id = :id") suspend fun get(id: String): DailyEntryEntity?
    @Query("SELECT * FROM daily_entries WHERE id GLOB (:prefix || '*')")
    suspend fun reviewedBatch(prefix: String): List<DailyEntryEntity>
    @Query("SELECT * FROM daily_entries") suspend fun all(): List<DailyEntryEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(entry: DailyEntryEntity): Long
    @Update suspend fun update(entry: DailyEntryEntity): Int
    @Query("DELETE FROM daily_entries WHERE id = :id") suspend fun delete(id: String): Int
    @Insert suspend fun insertImported(entries: List<DailyEntryEntity>)
    @Insert suspend fun insertBatch(batch: ImportBatchEntity)
    @Query("SELECT * FROM import_batches") suspend fun allBatches(): List<ImportBatchEntity>
    @Query("SELECT e.id FROM daily_entries e JOIN import_batches i ON e.importBatchId = i.id WHERE e.bookId = :bookId AND i.bookId = :bookId AND i.fileHash = :hash AND i.sheet = :sheet AND i.mapping = :mapping")
    suspend fun importedIds(bookId: Long, hash: String, sheet: String, mapping: String): List<String>
    @Query("SELECT * FROM daily_entries WHERE bookId = :bookId AND day BETWEEN :from AND :until")
    suspend fun existing(bookId: Long, from: Long, until: Long): List<DailyEntryEntity>
    @Query("SELECT * FROM daily_categories ORDER BY name") suspend fun dailyCategories(): List<DailyCategoryEntity>
    @Query("SELECT DISTINCT kind, categoryName AS name FROM daily_entries WHERE bookId = :bookId AND categoryName IS NOT NULL AND categoryName <> '未指定' ORDER BY name")
    suspend fun usedCategories(bookId: Long): List<DailyCategoryEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertCategories(categories: List<DailyCategoryEntity>)
}

data class ProjectPayment(
    val id: Long, val expenseId: Long, val bookId: Long, val amountCents: Long,
    val day: Long, val categoryName: String, val note: String, val payMethod: PayMethod,
) {
    fun actualEntry() = DailyEntryEntity("payment:$expenseId:$id", bookId, amountCents, EntryKind.EXPENSE,
        DailyCategory.OTHER_EXPENSE, day, -1, payMethod, note, categoryName = categoryName)
}
