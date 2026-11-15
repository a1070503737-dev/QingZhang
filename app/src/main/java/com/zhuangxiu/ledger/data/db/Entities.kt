package com.zhuangxiu.ledger.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** 分类种类：装修阶段 / 费用类型（两种分类共用一张表） */
enum class CategoryKind(val label: String) {
    STAGE("装修阶段"),
    TYPE("费用类型"),
}

/** 付款状态 */
enum class PaymentStatus(val label: String) {
    PAID("已付"),
    DEPOSIT("定金"),
    BALANCE_DUE("待付尾款"),
    UNPAID("未付"),
}

/** 分类（装修阶段或费用类型），可增删改、排序 */
@Entity(tableName = "categories", indices = [Index("kind", "sortOrder")])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: CategoryKind,
    val name: String,
    /** 排序序号，越小越靠前 */
    val sortOrder: Int,
)

/**
 * 一笔账单（订单）。
 * 金额统一用 Long 按“分”存储，避免浮点误差；日期用 epochDay（自 1970-01-01 起的天数）。
 */
@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["stageId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["typeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("stageId"), Index("typeId"), Index("date")],
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 订单总价（分） */
    val totalCents: Long,
    /** 账单日期（epochDay） */
    val date: Long,
    val stageId: Long,
    val typeId: Long,
    /** 商家 / 工人 */
    val payee: String = "",
    val note: String = "",
    val status: PaymentStatus = PaymentStatus.PAID,
    /** 待付款到期日（epochDay），已付清时为 null */
    val dueDate: Long? = null,
    /** 创建时间（毫秒时间戳） */
    val createdAt: Long = System.currentTimeMillis(),
)

/** 付款记录：一笔账单可以分多次付款 */
@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expenseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("expenseId")],
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val expenseId: Long,
    /** 本次付款金额（分） */
    val amountCents: Long,
    /** 付款日期（epochDay） */
    val date: Long,
    val note: String = "",
)

/** 凭证图片：文件本体保存在 App 私有目录，这里只记录文件名 */
@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expenseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("expenseId")],
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val expenseId: Long,
    val fileName: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * 预算。categoryId = [TOTAL_ID] 表示总预算，否则是某个阶段/类型的单独预算。
 */
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val categoryId: Long,
    val amountCents: Long,
) {
    companion object {
        const val TOTAL_ID = 0L
    }
}

/** 账单 + 关联的分类、付款记录、凭证，界面上绝大多数地方用这个聚合对象 */
data class ExpenseWithDetails(
    @Embedded val expense: ExpenseEntity,
    @Relation(parentColumn = "stageId", entityColumn = "id") val stage: CategoryEntity,
    @Relation(parentColumn = "typeId", entityColumn = "id") val type: CategoryEntity,
    @Relation(parentColumn = "id", entityColumn = "expenseId") val payments: List<PaymentEntity>,
    @Relation(parentColumn = "id", entityColumn = "expenseId") val attachments: List<AttachmentEntity>,
) {
    /** 已付金额（分）= 所有付款记录之和 */
    val paidCents: Long get() = payments.sumOf { it.amountCents }

    /** 剩余待付金额（分），不会小于 0 */
    val remainingCents: Long get() = (expense.totalCents - paidCents).coerceAtLeast(0)
}

/** 凭证 + 所属账单，凭证库页面使用 */
data class AttachmentWithExpense(
    @Embedded val attachment: AttachmentEntity,
    @Relation(parentColumn = "expenseId", entityColumn = "id") val expense: ExpenseEntity,
)
