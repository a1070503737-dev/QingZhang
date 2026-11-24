package com.zhuangxiu.ledger.domain

import com.zhuangxiu.ledger.data.db.DailyCategory
import com.zhuangxiu.ledger.data.db.EntryKind
import com.zhuangxiu.ledger.util.Money
import java.text.Normalizer
import java.time.LocalDate
import java.time.DateTimeException

internal data class EntryDraft(
    val kind: EntryKind? = null,
    val amount: String = "",
    val date: String = "",
    val category: DailyCategory? = null,
    val note: String = "",
)

/** Alternative parsers can propose drafts. Parsing can never save a transaction. */
internal fun interface VoiceTextParser {
    fun parse(transcript: String, today: LocalDate): List<EntryDraft>
}

private val chineseWordGap = Regex("(?<=[\\u3400-\\u9fff])\\s+(?=[\\u3400-\\u9fff])")
internal fun compactChineseSpeech(text: String): String = chineseWordGap.replace(text, "")

internal object ChineseVoiceParser : VoiceTextParser {
    private const val CHINESE_DIGITS = "零一二三四五六七八九"
    private const val CHINESE_NUMBER = "零〇一二两三四五六七八九十百千万亿"
    private const val NUMBER = "(?:[0-9]+(?:[.点][0-9]+)?|[$CHINESE_NUMBER]+(?:点[零〇一二两三四五六七八九]+)?)"
    private val clauses = Regex("[，,；;。！？!？、\\n]+|然后|还有")
    private val money = Regex("(?<![0-9.])(?:¥\\s*[+-]?$NUMBER|[+-]?$NUMBER\\s*[元块圆])")
    private val plainAmount = Regex("(?<![0-9.])[+-]?[0-9]+(?:\\.[0-9]+)?(?![0-9.])")
    private val datePattern = Regex("(?:[0-9]{4}年)?[0-9]{1,2}月[0-9]{1,2}[日号]?|[0-9]{4}[-/][0-9]{1,2}[-/][0-9]{1,2}|大前天|前天|昨天|昨日|今天|今日|明天|[0-9]{1,4}天前")
    private val incomeWords = listOf("收入", "到账", "收到", "赚了", "发了", "报销", "退回", "进账")
    private val expenseWords = listOf("支出", "花了", "花费", "支付", "买了", "付款", "缴费", "消费", "付了")
    private val totalWords = listOf("一共", "合计", "总计", "共计", "总共", "单价")
    private val categories = linkedMapOf(
        DailyCategory.SALARY to listOf("工资", "薪水", "薪资"),
        DailyCategory.BONUS to listOf("奖金", "年终奖"),
        DailyCategory.SIDE_JOB to listOf("兼职", "稿费"),
        DailyCategory.GIFT to listOf("红包"),
        DailyCategory.REFUND to listOf("退款", "退回"),
        DailyCategory.FOOD to listOf("餐饮", "早餐", "早饭", "午饭", "午餐", "晚饭", "晚餐", "吃饭", "外卖", "咖啡", "奶茶", "买菜", "加餐"),
        DailyCategory.TRANSPORT to listOf("公交", "地铁", "打车", "车费", "交通", "停车", "加油"),
        DailyCategory.HOME to listOf("住房", "房租", "租金", "物业"),
        DailyCategory.UTILITIES to listOf("水费", "电费", "燃气", "生活缴费"),
        DailyCategory.HEALTH to listOf("医疗", "看病", "挂号", "买药"),
        DailyCategory.PHONE to listOf("话费", "通讯", "流量"),
        DailyCategory.FUN to listOf("娱乐", "电影", "游戏", "门票"),
        DailyCategory.SHOPPING to listOf("购物", "买衣服", "超市", "网购"),
    )

    override fun parse(transcript: String, today: LocalDate): List<EntryDraft> {
        val normalized = Normalizer.normalize(compactChineseSpeech(transcript), Normalizer.Form.NFKC)
            .replace(Regex("(?<=\\d),(?=\\d{3}(?:[,.\\s元块圆]|$))"), "")
        var date = today.toString()
        return clauses.split(normalized).flatMap(::splitSpokenItems).mapNotNull { part ->
            if (part.isBlank()) return@mapNotNull null
            val dates = datePattern.findAll(part).toList()
            if (dates.isNotEmpty()) date = dates.singleOrNull()?.let { parseDate(it.value, today)?.toString() }.orEmpty()
            val line = datePattern.replace(part, "").trim()
            if (line.isEmpty()) return@mapNotNull null

            val explicit = money.findAll(line).toList()
            val amountMatch = if (explicit.isNotEmpty()) explicit.singleOrNull() else plainAmount.findAll(line).singleOrNull()
            val trailing = amountMatch?.let { line.substring(it.range.last + 1) }.orEmpty()
            // A second amount or a spoken fraction after 元 needs a review instead of guessing.
            val ambiguous = explicit.size > 1 || Regex("^\\s*[${CHINESE_NUMBER}0-9]+[毛角分]?").containsMatchIn(trailing)
            val amount = if (ambiguous) "" else amountMatch?.value?.let(::parseAmount).orEmpty()

            val memo = if (amountMatch != null && !ambiguous) line.removeRange(amountMatch.range).trim() else line
            val candidates = categories.filterValues { words -> words.any { it in memo } }.keys
            val saidIncome = incomeWords.any { it in memo }
            val saidExpense = expenseWords.any { it in memo }
            val kind = when {
                saidIncome && saidExpense -> null
                saidIncome -> EntryKind.INCOME
                saidExpense -> EntryKind.EXPENSE
                else -> candidates.map { it.kind }.distinct().singleOrNull()
            }
            val category = candidates.filter { it.kind == kind }.singleOrNull()
            EntryDraft(kind, amount, date, category, memo)
        }
    }

    private fun splitSpokenItems(line: String): List<String> {
        val amounts = money.findAll(line).toList()
        if (amounts.size < 2 || totalWords.any { it in line }) return listOf(line)

        val cues = categories.values.flatten() + incomeWords + expenseWords
        val boundaries = amounts.zipWithNext { first, second ->
            val boundary = first.range.last + 1
            val gap = line.substring(boundary, second.range.first).trim()
            // A fresh item name is needed before splitting two adjacent amounts.
            if (cues.none { it in gap } || gap.firstOrNull()?.let { it.isDigit() || it in CHINESE_NUMBER } == true) {
                return listOf(line)
            }
            boundary
        }
        return (listOf(0) + boundaries + line.length).zipWithNext { start, end -> line.substring(start, end) }
    }

    private fun parseAmount(raw: String): String? {
        val amount = raw.trim().trimStart('¥').trimEnd('元', '块', '圆').trim().replace('点', '.')
        val input = if (amount.firstOrNull()?.isDigit() == true) amount else {
            val parts = amount.split('.')
            val integer = chineseInteger(parts.first()) ?: return null
            val fraction = parts.getOrNull(1)?.map { digit(it) ?: return null }?.joinToString("")
            if (fraction == null) integer.toString() else "$integer.$fraction"
        }
        return Money.parseToCents(input)?.takeIf { it > 0 }?.let(Money::toInput)
    }

    private fun digit(char: Char): Int? = CHINESE_DIGITS.indexOf(if (char == '两') '二' else if (char == '〇') '零' else char)
        .takeIf { it >= 0 }

    private fun chineseInteger(input: String): Long? {
        if (input.all { digit(it) != null }) return input.map { digit(it) }.joinToString("").toLongOrNull()
        var total = 0L
        var section = 0L
        var previousUnit = 10_000
        var previousLargeUnit = Long.MAX_VALUE
        var current: Int? = null
        var explicitZero = false
        for (char in input) {
            val digit = digit(char)
            when {
                digit == 0 -> explicitZero = true
                digit != null -> {
                    if (current != null) return null
                    current = digit
                }
                char == '万' || char == '亿' -> {
                    val multiplier = if (char == '万') 10_000L else 100_000_000L
                    if (multiplier >= previousLargeUnit || section + (current ?: 0) == 0L) return null
                    total += (section + (current ?: 0)) * multiplier
                    section = 0
                    previousUnit = 10_000
                    previousLargeUnit = multiplier
                    current = null
                    explicitZero = false
                }
                else -> {
                    val unit = when (char) { '十' -> 10; '百' -> 100; '千' -> 1000; else -> return null }
                    if (unit >= previousUnit || (current == null && !(unit == 10 && section == 0L))) return null
                    section += (current ?: 1) * unit
                    previousUnit = unit
                    current = null
                    explicitZero = false
                }
            }
        }
        if (current != null && !explicitZero && (previousUnit in 100..1000 || (section == 0L && total > 0))) return null
        return total + section + (current ?: 0)
    }

    private fun parseDate(value: String, today: LocalDate): LocalDate? = try {
        when (value) {
            "今天", "今日" -> today
            "昨天", "昨日" -> today.minusDays(1)
            "前天" -> today.minusDays(2)
            "大前天" -> today.minusDays(3)
            "明天" -> today.plusDays(1)
            else -> if (value.endsWith("天前")) today.minusDays(value.removeSuffix("天前").toLong())
            else {
                val parts = Regex("\\d+").findAll(value).map { it.value.toInt() }.toList()
                if (parts.size == 3) LocalDate.of(parts[0], parts[1], parts[2])
                else LocalDate.of(today.year, parts[0], parts[1])
            }
        }
    } catch (_: DateTimeException) { null }
}
