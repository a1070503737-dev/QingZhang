package com.zhuangxiu.ledger.data.repository

import androidx.room.withTransaction
import com.zhuangxiu.ledger.data.db.AppDatabase
import com.zhuangxiu.ledger.data.db.CategoryEntity
import com.zhuangxiu.ledger.data.db.CategoryKind
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** 分类仓库：装修阶段、费用类型的增删改和排序 */
@Singleton
class CategoryRepository @Inject constructor(private val db: AppDatabase) {
    private val dao = db.categoryDao()

    /** 全部分类，按种类和排序序号排列 */
    fun observeAll(): Flow<List<CategoryEntity>> = dao.observeAll()

    suspend fun list(kind: CategoryKind): List<CategoryEntity> = dao.listByKind(kind)

    /** 新增分类，排在同类的最后。名称为空或重名时返回 null */
    suspend fun add(kind: CategoryKind, name: String): Long? = db.withTransaction {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || dao.listByKind(kind).any { it.name == trimmed }) return@withTransaction null
        dao.insert(CategoryEntity(kind = kind, name = trimmed, sortOrder = dao.maxSortOrder(kind) + 1))
    }

    /** 重命名。名称为空或与同类其他分类重名时返回 false */
    suspend fun rename(category: CategoryEntity, newName: String): Boolean = db.withTransaction {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return@withTransaction false
        if (dao.listByKind(category.kind).any { it.id != category.id && it.name == trimmed }) return@withTransaction false
        dao.update(category.copy(name = trimmed))
        true
    }

    /**
     * 删除分类。已有账单使用、或是同类最后一个分类时不允许删除，返回 false。
     * 删除成功时一并删除该分类的预算。
     */
    suspend fun delete(category: CategoryEntity): Boolean = db.withTransaction {
        if (dao.usageCount(category.id) > 0) return@withTransaction false
        if (dao.listByKind(category.kind).size <= 1) return@withTransaction false
        dao.deleteById(category.id)
        db.budgetDao().delete(category.id)
        true
    }

    /** 上移（offset = -1）或下移（offset = 1）一个分类 */
    suspend fun move(category: CategoryEntity, offset: Int) = db.withTransaction {
        val list = dao.listByKind(category.kind).toMutableList()
        val from = list.indexOfFirst { it.id == category.id }
        val to = from + offset
        if (from < 0 || to !in list.indices) return@withTransaction
        list.add(to, list.removeAt(from))
        // 重新编号，保证序号连续
        list.forEachIndexed { index, item -> dao.updateSortOrder(item.id, index) }
    }
}
