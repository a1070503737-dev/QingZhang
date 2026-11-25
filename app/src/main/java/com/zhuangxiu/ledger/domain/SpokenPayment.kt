package com.zhuangxiu.ledger.domain

import com.zhuangxiu.ledger.data.db.PayMethod

internal object SpokenPayment {
    private val channels = mapOf(
        PayMethod.WECHAT to listOf("微信钱包", "微信零钱", "微信", "wechat pay", "wechatpay"),
        PayMethod.ALIPAY to listOf("支付宝", "alipay"),
        PayMethod.BANK to listOf("银行卡", "信用卡", "借记卡", "储蓄卡", "银联卡", "刷卡", "银行卡转账", "银行转账", "网银"),
        PayMethod.CASH to listOf("现金", "现钱", "纸币"),
        PayMethod.OTHER to listOf("云闪付", "apple pay", "google pay", "paypal", "数字人民币"),
    ).flatMap { (method, aliases) -> aliases.map { it to method } }.toMap()
    private val aliases = channels.keys.sortedByDescending(String::length).joinToString("|", transform = Regex::escape)
    private const val prefix = "用(?:的是)?|通过|刷|走|从|付(?:了)?"
    private const val action = "(?:支付|付款|付钱|付|买单|结账|刷|扣|收款|收到|转账|到账)(?:了|的)?"
    private val phrase = Regex("(?<prefix>$prefix)?(?<alias>$aliases)(?<action>\\s*$action)?", RegexOption.IGNORE_CASE)
    private val negated = Regex("(?:没(?:有)?|不是|不|打算|准备|想)\\s*$")
    private val choice = Regex("(?:还是|或者|或是|或)\\s*$")
    private val nextChoice = Regex("^\\s*(?:还是|或者|或是|或)")
    private val statedMethod = Regex("(?:收到|收了|支付|付款|(?:收款|付款|支付)方式(?:是|为)?)\\s*$")
    private val amountEnd = Regex("[0-9零〇一二两三四五六七八九十百千万元块圆角毛分钱]\\s*$")
    private val transactionActions = setOf("支付", "付款", "付钱", "付", "买单", "结账")
    private val explicitChannels = setOf("刷卡", "银行转账", "银行卡转账")
    val start = "(?:$prefix)?(?:$aliases)"
    private val startPattern = Regex("^$start", RegexOption.IGNORE_CASE)

    fun identify(text: String): PayMethod = mentions(normalizeVoiceText(text)).map { match ->
        channels.getValue(requireNotNull(match.groups["alias"]).value.lowercase())
    }.distinct().toList().singleOrNull() ?: PayMethod.UNSPECIFIED

    fun isStandalone(text: String): Boolean = phrase.matches(text.trim())

    fun begins(text: String): Boolean = startPattern.containsMatchIn(text)

    fun trailing(text: String): MatchResult? = mentions(text).lastOrNull { it.range.last == text.lastIndex }

    fun afterAmount(gap: String): Int {
        val space = gap.indexOfFirst { !it.isWhitespace() }
        if (space < 0) return 0
        val match = phrase.matchAt(gap, space) ?: return 0
        // “微信支付” after an amount can close that item. “用微信付午饭”
        // starts the next one, whereas “用微信付的” closes the previous one.
        val action = match.groups["action"]?.value?.trim()
        return if (match.value.endsWith("的") || match.groups["prefix"] == null && action in transactionActions) {
            match.range.last + 1
        } else 0
    }

    private fun mentions(text: String): Sequence<MatchResult> = phrase.findAll(text).filter { match ->
        val before = text.substring(0, match.range.first)
        val after = text.substring(match.range.last + 1)
        val alias = requireNotNull(match.groups["alias"]).value
        !negated.containsMatchIn(before) && (
            match.groups["prefix"] != null || match.groups["action"] != null ||
                after.startsWith("买了") || after.startsWith("扣了") || choice.containsMatchIn(before) ||
                nextChoice.containsMatchIn(after) ||
                statedMethod.containsMatchIn(before) || alias in explicitChannels ||
                after.isBlank() && (before.isBlank() || amountEnd.containsMatchIn(before) ||
                    before.trimEnd().endsWith("是"))
            )
    }
}
