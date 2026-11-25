package com.zhuangxiu.ledger.ui.navigation

/** 全部页面路由 */
object Routes {
    // 首页、统计、账本及保留的装修子页面
    const val HOME = "home"
    const val BILLS = "bills/{bookId}"
    fun bills(bookId: Long) = "bills/$bookId"
    const val PROJECTS = "projects/{bookId}"
    fun projects(bookId: Long) = "projects/$bookId"
    const val IMPORT = "data-import"
    const val STATS = "stats"
    const val MINE = "mine"
    const val BOOKS = "books"
    const val DAILY_EDIT = "daily-edit?bookId={bookId}&entryId={entryId}"
    fun dailyEdit(bookId: Long, entryId: String = "") = "daily-edit?bookId=$bookId&entryId=$entryId"

    // 记账 / 编辑页：id 为 -1 表示新建；copyFrom 为某笔账单 id 时，新建并预填它的阶段、类型、商家
    const val EDIT = "edit?id={id}&copyFrom={copyFrom}&bookId={bookId}"
    fun edit(id: Long = -1, copyFrom: Long = -1, bookId: Long = 2) = "edit?id=$id&copyFrom=$copyFrom&bookId=$bookId"

    // 账单详情页
    const val DETAIL = "detail/{id}"
    fun detail(id: Long) = "detail/$id"

    // 图片查看页：expenseId 为 -1 表示查看凭证库里的全部图片
    const val VIEWER = "viewer?expenseId={expenseId}&index={index}"
    fun viewer(expenseId: Long, index: Int) = "viewer?expenseId=$expenseId&index=$index"

    // “我的”里的子页面
    const val GALLERY = "gallery"
    const val BUDGET = "budget"
    const val CATEGORIES = "categories"
    const val BACKUP = "backup"
    const val FEEDBACK = "feedback"
    const val CLOUD_VOICE = "cloud-voice"
    const val REMINDER = "reminder"
    const val UPDATE = "update"
}
