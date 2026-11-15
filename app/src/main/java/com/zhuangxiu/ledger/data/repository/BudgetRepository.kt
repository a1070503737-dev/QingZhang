package com.zhuangxiu.ledger.data.repository

import com.zhuangxiu.ledger.data.db.AppDatabase
import com.zhuangxiu.ledger.data.db.BudgetEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** 预算仓库：总预算和各分类预算 */
@Singleton
class BudgetRepository @Inject constructor(db: AppDatabase) {
    private val dao = db.budgetDao()

    fun observeAll(): Flow<List<BudgetEntity>> = dao.observeAll()

    suspend fun listAll(): List<BudgetEntity> = dao.listAll()

    /**
     * 设置预算。
     * @param categoryId 分类 id；总预算传 [BudgetEntity.TOTAL_ID]
     * @param amountCents 预算金额（分）；传 0 或负数表示取消这项预算
     */
    suspend fun setBudget(categoryId: Long, amountCents: Long) {
        if (amountCents <= 0) dao.delete(categoryId) else dao.upsert(BudgetEntity(categoryId, amountCents))
    }
}
