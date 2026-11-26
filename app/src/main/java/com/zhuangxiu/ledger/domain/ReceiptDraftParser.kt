package com.zhuangxiu.ledger.domain

import com.zhuangxiu.ledger.data.db.DailyCategory
import com.zhuangxiu.ledger.data.db.EntryKind
import com.zhuangxiu.ledger.data.db.PayMethod
import com.zhuangxiu.ledger.util.Money
import java.time.LocalDate
import java.time.LocalTime

internal object ReceiptDraftParser {
    private val unsettled = Regex("^(?:交易关闭|交易失败|付款失败|支付失败|待付款|等待付款|待支付|未支付|订单已取消|已取消)$")
    private val refund = Regex("退款成功|退款到账|退款已到账|已退回")
    private val received = Regex("收款成功|转账收入|到账金额|收款金额|实收金额|退款成功|退款到账")
    private val paid = Regex("支付成功|付款成功|交易成功|已支付|支付凭证|支付详情|账单详情")
    private val refundedAmount = labels("退款金额|已退金额|到账金额")
    private val paidAmount = labels("实付金额|实际支付金额|实际付款|实付|实收金额|实际金额")
    private val transactionAmount = labels("交易金额|支付金额|付款金额|收款金额|到账金额|退款金额")
    private val totalAmount = labels("合计金额|总计金额|含税合计|价税合计|合计|应收金额|总计|共计")
    private val methodLabel = labels("付款方式|支付方式|收款方式|扣款方式|扣款账户|支付渠道|付款账户")
    private val statusLabel = labels("交易状态|支付状态|当前状态|订单状态")
    private val merchantLabel = labels("商户名称|商家名称|商户全称|收款方|付款方|交易对方|商家|商户(?!号)")
    private val descriptionLabel = labels("商品说明|商品名称|交易说明|消费项目|商品|交易类型")
    private val transactionTime = labels("交易时间|支付时间|付款时间|收款时间|到账时间|退款时间|消费时间|交易日期")
    private val receiptTime = labels("开票日期|日期|打印时间|下单时间|时间")
    private val date = Regex("(?<!\\d)(20\\d{2})[-/.年]\\s*(\\d{1,2})[-/.月]\\s*(\\d{1,2})日?(?!\\d)")
    private val time = Regex("(?<!\\d)(\\d{1,2}):(\\d{2})(?::\\d{2})?(?!\\d)")
    private val currency = Regex(
        "^\\s*([+−-])?\\s*(?:¥|CNY|RMB)?\\s*([+−-])?\\s*" +
            "((?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]{1,2})?)\\s*(?:元)?\\s*$",
        RegexOption.IGNORE_CASE,
    )
    private val explicitMoney = Regex("[+−¥]|^-|元$|CNY|RMB", RegexOption.IGNORE_CASE)

    fun parse(text: String, today: LocalDate): EntryDraft? {
        val lines = text.lineSequence().map { normalizeVoiceText(it).trim() }.filter(String::isNotEmpty).toList()
        if (lines.isEmpty()) return null
        val failed = lines.any { unsettled.matches(it) } || fields(lines, statusLabel).any(unsettled::matches)
        val isRefund = lines.any(refund::matches) || fields(lines, statusLabel).any { refund.containsMatchIn(it) }
        val haveReceived = isRefund || lines.any { received.containsMatchIn(it) }
        val havePaid = lines.any { paid.containsMatchIn(it) }
        val amounts = listOfNotNull(refundedAmount.takeIf { isRefund }, paidAmount, transactionAmount, totalAmount)
            .asSequence().map { field -> fields(lines, field).mapNotNull(::readMoney).distinct() }
            .firstOrNull { it.isNotEmpty() }
            ?: if (havePaid || haveReceived) lines.filter { explicitMoney.containsMatchIn(it) }.mapNotNull(::readMoney).distinct()
            else emptyList()
        val amount = amounts.singleOrNull().takeUnless { failed }
        val kind = when {
            failed -> null
            haveReceived || amount?.sign == "+" -> EntryKind.INCOME
            havePaid || amount != null -> EntryKind.EXPENSE
            else -> null
        }

        val description = fields(lines, descriptionLabel).firstOrNull().orEmpty().take(100)
        val merchant = fields(lines, merchantLabel).firstOrNull().orEmpty().take(100)
        val note = listOf(description, merchant).filter(String::isNotBlank).distinct().joinToString(" · ")
        val category = when {
            isRefund && kind == EntryKind.INCOME -> DailyCategory.REFUND
            note.isBlank() -> null
            else -> ChineseVoiceParser.parse(note, today).singleOrNull()?.category?.takeIf { it.kind == kind }
        }
        val statedMethods = fields(lines, methodLabel)
        val method = if (statedMethods.isNotEmpty()) {
            statedMethods.map(::readPayment).distinct().singleOrNull() ?: PayMethod.UNSPECIFIED
        } else {
            lines.mapNotNull {
                when (it) {
                    "微信支付", "微信支付凭证" -> PayMethod.WECHAT
                    "支付宝", "支付宝支付凭证" -> PayMethod.ALIPAY
                    else -> null
                }
            }.distinct().singleOrNull() ?: PayMethod.UNSPECIFIED
        }
        val timestamps = fields(lines, transactionTime).filter(::hasTimestamp)
            .ifEmpty { fields(lines, receiptTime).filter(::hasTimestamp) }
            .ifEmpty { lines.filter { date.containsMatchIn(it) } }
        val dates = timestamps.flatMap { date.findAll(it).toList() }.map { match ->
            val (_, year, month, day) = match.groupValues
            runCatching { LocalDate.of(year.toInt(), month.toInt(), day.toInt()).toString() }.getOrDefault("")
        }.distinct()
        val times = timestamps.flatMap { time.findAll(it).toList() }.map { match ->
            val (_, hour, minute) = match.groupValues
            runCatching { LocalTime.of(hour.toInt(), minute.toInt()).toString() }.getOrDefault(match.value)
        }.distinct()
        return EntryDraft(
            kind = kind,
            amount = amount?.cents?.let(Money::toInput).orEmpty(),
            date = if (dates.isEmpty()) today.toString() else dates.singleOrNull().orEmpty(),
            category = category,
            note = note,
            payMethod = method,
            time = times.joinToString(" / "),
        )
    }

    private data class Amount(val cents: Long, val sign: String)

    private fun hasTimestamp(value: String) = date.containsMatchIn(value) || time.containsMatchIn(value)

    private fun readMoney(value: String): Amount? {
        val (_, leadingSign, innerSign, number) = currency.matchEntire(value)?.groupValues ?: return null
        if (leadingSign.isNotEmpty() && innerSign.isNotEmpty()) return null
        val cents = Money.parseToCents(number)?.takeIf { it > 0 } ?: return null
        return Amount(cents, leadingSign.ifEmpty { innerSign }.replace('−', '-'))
    }

    private fun readPayment(value: String): PayMethod {
        val method = SpokenPayment.identify("用$value")
        return if (method != PayMethod.UNSPECIFIED) method else when {
            value.startsWith("零钱") -> PayMethod.WECHAT
            listOf("余额宝", "花呗", "借呗").any { it in value } -> PayMethod.ALIPAY
            listOf("银行卡", "信用卡", "储蓄卡", "借记卡", "银行").any { it in value } -> PayMethod.BANK
            else -> PayMethod.UNSPECIFIED
        }
    }

    private fun labels(names: String) = Regex("^(?:$names)\\s*:?\\s*(.*)$")

    private fun fields(lines: List<String>, label: Regex): List<String> = lines.mapIndexedNotNull { index, line ->
        val value = label.matchEntire(line)?.groupValues?.get(1)?.trim() ?: return@mapIndexedNotNull null
        value.ifBlank { lines.getOrNull(index + 1).orEmpty() }.takeIf(String::isNotBlank)
    }
}
