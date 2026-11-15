package com.zhuangxiu.ledger.domain

import com.zhuangxiu.ledger.data.db.PaymentStatus

/** 付款状态相关的纯计算逻辑 */
object PaymentLogic {

    /**
     * 根据总价和已付金额推导出真实的付款状态。
     * - 已付 >= 总价：已付
     * - 一分没付：未付
     * - 付了一部分：用户选了“定金”就保持定金，否则是“待付尾款”
     *
     * @param preferred 用户选择（或当前）的状态
     */
    fun resolveStatus(preferred: PaymentStatus, totalCents: Long, paidCents: Long): PaymentStatus = when {
        paidCents >= totalCents -> PaymentStatus.PAID
        paidCents <= 0 -> PaymentStatus.UNPAID
        preferred == PaymentStatus.DEPOSIT -> PaymentStatus.DEPOSIT
        else -> PaymentStatus.BALANCE_DUE
    }

    /**
     * 新建账单时，根据用户选择的状态计算首笔付款金额。
     * 已付 = 全额；未付 = 0；定金/待付尾款 = 用户输入的已付金额（限制在 0..总价）。
     */
    fun initialPaid(status: PaymentStatus, totalCents: Long, inputPaidCents: Long): Long = when (status) {
        PaymentStatus.PAID -> totalCents
        PaymentStatus.UNPAID -> 0
        else -> inputPaidCents.coerceIn(0, totalCents)
    }
}
