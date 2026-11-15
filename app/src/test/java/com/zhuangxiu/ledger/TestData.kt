package com.zhuangxiu.ledger

import com.zhuangxiu.ledger.data.db.CategoryEntity
import com.zhuangxiu.ledger.data.db.CategoryKind
import com.zhuangxiu.ledger.data.db.ExpenseEntity
import com.zhuangxiu.ledger.data.db.ExpenseWithDetails
import com.zhuangxiu.ledger.data.db.PaymentEntity
import com.zhuangxiu.ledger.data.db.PaymentStatus
import java.time.LocalDate

/** 测试用的固定分类和造数据的小工具 */
object TestData {
    val stageWater = CategoryEntity(id = 1, kind = CategoryKind.STAGE, name = "水电", sortOrder = 0)
    val stageWood = CategoryEntity(id = 2, kind = CategoryKind.STAGE, name = "木工", sortOrder = 1)
    val typeMaterial = CategoryEntity(id = 10, kind = CategoryKind.TYPE, name = "主材", sortOrder = 0)
    val typeLabor = CategoryEntity(id = 11, kind = CategoryKind.TYPE, name = "人工", sortOrder = 1)
    val categories = listOf(stageWater, stageWood, typeMaterial, typeLabor)

    private var nextId = 1L

    /** 造一笔账单：金额单位是分，paid 是已付金额 */
    fun expense(
        total: Long,
        paid: Long = total,
        stage: CategoryEntity = stageWater,
        type: CategoryEntity = typeMaterial,
        date: LocalDate = LocalDate.of(2025, 3, 8),
    ): ExpenseWithDetails {
        val id = nextId++
        return ExpenseWithDetails(
            expense = ExpenseEntity(
                id = id,
                totalCents = total,
                date = date.toEpochDay(),
                stageId = stage.id,
                typeId = type.id,
                status = if (paid >= total) PaymentStatus.PAID else PaymentStatus.BALANCE_DUE,
            ),
            stage = stage,
            type = type,
            payments = if (paid > 0) listOf(PaymentEntity(id = id, expenseId = id, amountCents = paid, date = date.toEpochDay())) else emptyList(),
            attachments = emptyList(),
        )
    }
}
