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
    private const val DIGIT = "[0-9零〇一二两三四五六七八九]"
    private const val NUMBER = "(?:[0-9]+(?:[.点][0-9]+)?|[$CHINESE_NUMBER]+(?:点[零〇一二两三四五六七八九]+)?)"
    private val clauses = Regex("[，,；;。！？!？、\\n]+|然后|还有")
    private val money = Regex("(?<![0-9.])(?:¥\\s*[+负-]?$NUMBER|[+负-]?$NUMBER\\s*[元块圆])")
    private val plainAmount = Regex("(?<![0-9.])[+负-]?[0-9]+(?:\\.[0-9]+)?(?![0-9.])")
    private val datePattern = Regex("(?:[0-9]{4}年)?[0-9]{1,2}月[0-9]{1,2}[日号]?|[0-9]{4}[-/][0-9]{1,2}[-/][0-9]{1,2}|大前天|前天|昨天|昨日|昨晚|昨夜|今早|今晚|今天|今日|明晚|明天|[0-9]{1,4}天前")
    private val incomeWords = listOf("收入", "到账", "收到", "赚了", "发了", "报销", "退回", "进账")
    private val amountWords = listOf("支出", "花了", "花费", "支付", "付款", "缴费", "消费", "付了", "实付")
    private val mealActions = listOf("吃了", "吃个", "吃点", "吃顿", "吃碗", "吃份", "喝了", "喝杯")
    private val purchaseActions = listOf("买了", "买个", "买一", "买点", "买件", "买东西", "入手", "下单", "购入", "购买")
    private val mealContext = Regex("(?:^|我|早上|中午|晚上|夜里|刚才|刚刚|去)(?:在家|又|刚)?[吃喝](?!药|苦|亏|土)")
    private val purchaseContext = Regex("(?:^|我|早上|中午|晚上|刚才|刚刚|又|去)买(?!单|不起|家|卖|方)")
    private val kitchenGoods = Regex("(?:咖啡|奶茶|面包|水果|火锅)(?:机|杯|壶|锅|刀|架|模具|滤纸|滤杯|色)")
    private val totalWords = listOf("一共", "合计", "总计", "共计", "总共", "单价", "其中", "包含")
    private val quantity = Regex("^\\s*(?:块|个|件|杯|碗|盘|包|袋|份|盒|瓶|张|台|只|双|本|条|套|斤|公斤|克|升|次|天|小时|分钟|公里|寸|号)")
    private val unconfirmed = Regex("(?:没(?:有)?|还没|不|打算|准备|计划|想|要不要)(?:去)?(?:买|吃|喝|花|付|支付|下单)")
    private val approximate = Regex(
        "大概|大约|大致|差不多|左右|上下|不到|将近|几[十百千元块圆]|[十百千万]几|" +
            "[0-9$CHINESE_NUMBER][多来](?=\\s*[元块圆]|\\s*$)|[元块圆]\\s*(?:多|出头)|" +
            "$NUMBER\\s*[元块圆]?\\s*(?:到|至|~|–|—|-)\\s*$NUMBER",
    )
    private val approximateSuffix = Regex("^\\s*(?:左右|上下|多|出头)")
    private val bareChineseAmount = Regex("(?<![0-9.+负\\-$CHINESE_NUMBER])[$CHINESE_NUMBER]+(?:点$DIGIT{1,2})?\\s*$")
    private val categories = linkedMapOf(
        DailyCategory.SALARY to listOf("工资", "薪水", "薪资"),
        DailyCategory.BONUS to listOf("奖金", "年终奖"),
        DailyCategory.SIDE_JOB to listOf("兼职", "稿费"),
        DailyCategory.GIFT to listOf("红包"),
        DailyCategory.REFUND to listOf("退款", "退回"),
        DailyCategory.FOOD to listOf(
            "餐饮", "早餐", "早饭", "午饭", "午餐", "中饭", "晚饭", "晚餐", "夜宵",
            "吃饭",
            "餐费", "外卖", "咖啡", "拿铁", "奶茶", "买菜", "加餐",
            "烧烤", "火锅", "炸鸡", "汉堡", "面条", "米粉", "寿司", "蛋糕", "面包", "水果",
        ) + mealActions,
        DailyCategory.TRANSPORT to listOf("公交", "地铁", "打车", "车费", "交通", "停车", "加油", "火车票", "机票", "网约车"),
        DailyCategory.HOME to listOf("住房", "房租", "租金", "物业"),
        DailyCategory.UTILITIES to listOf("水费", "电费", "燃气", "生活缴费"),
        DailyCategory.HEALTH to listOf("医疗", "看病", "挂号", "买药", "买了药", "吃药", "药店", "处方药"),
        DailyCategory.PHONE to listOf("话费", "通讯", "流量"),
        DailyCategory.FUN to listOf("娱乐", "电影", "游戏", "门票"),
        DailyCategory.SHOPPING to (listOf("购物", "买衣服", "超市", "网购") + purchaseActions),
    )
    private val itemCues = categories.values.flatten() + incomeWords + amountWords
    private val bareAmountPrefixes = itemCues - (mealActions + purchaseActions).toSet() + totalWords
    private val nextItem = itemCues.joinToString("|")
    private val amountAtItemEnd = Regex(
        "(?<![0-9.$CHINESE_NUMBER])[+负-]?$NUMBER(?=\\s*(?:$|$nextItem))",
    )
    private val spokenFraction = Regex(
        "(?<![0-9.$CHINESE_NUMBER])(?<yuan>[+负-]?$NUMBER)\\s*[元块圆]\\s*" +
            "(?:(?<half>半)|(?<jiao>$DIGIT)\\s*[角毛](?:\\s*(?<fen>$DIGIT)\\s*分)?|" +
            "(?<onlyFen>$DIGIT)\\s*分|(?<shortJiao>$DIGIT)(?=\\s*(?:$|$nextItem)))",
    )
    private val subYuan = Regex(
        "(?<![0-9.+负\\-${CHINESE_NUMBER}元块圆])($DIGIT)\\s*[角毛](?:\\s*($DIGIT)\\s*分)?",
    )

    override fun parse(transcript: String, today: LocalDate): List<EntryDraft> {
        val normalized = Normalizer.normalize(compactChineseSpeech(transcript), Normalizer.Form.NFKC)
            .replace(Regex("(?<=\\d),(?=\\d{3}(?:[,.\\s元块圆]|$))"), "")
        var date = today.toString()
        return clauses.split(normalized).map(::normalizeFractions).flatMap(::splitSpokenItems).mapNotNull { part ->
            if (part.isBlank()) return@mapNotNull null
            val dates = datePattern.findAll(part).toList()
            if (dates.isNotEmpty()) date = dates.singleOrNull()?.let { parseDate(it.value, today)?.toString() }.orEmpty()
            val line = datePattern.replace(part, "").trim()
            if (line.isEmpty()) return@mapNotNull null
            if (unconfirmed.containsMatchIn(line)) return@mapNotNull EntryDraft(date = date, note = line)

            val explicit = statedMoney(line)
            val amountMatch = if (explicit.isNotEmpty()) explicit.singleOrNull() else {
                (plainAmount.findAll(line) + listOfNotNull(bareChineseAmount.find(line)))
                    .filter { isBareAmount(line, it) }.singleOrNull()
            }
            val trailing = amountMatch?.let { line.substring(it.range.last + 1) }.orEmpty()
            val ambiguous = explicit.size > 1 || approximate.containsMatchIn(line) ||
                Regex("^\\s*[${CHINESE_NUMBER}0-9]+").containsMatchIn(trailing)
            val amount = if (ambiguous) "" else amountMatch?.value?.let(::parseAmount).orEmpty()

            val memo = if (amountMatch != null && amount.isNotEmpty()) line.removeRange(amountMatch.range).trim() else line
            val describesPurchase = purchaseContext.containsMatchIn(memo) || purchaseActions.any { it in memo }
            val matches = categories.filter { (category, words) ->
                val description = if (category == DailyCategory.FOOD) kitchenGoods.replace(memo, "") else memo
                words.any { it in description } ||
                    (category == DailyCategory.FOOD && mealContext.containsMatchIn(description)) ||
                    (category == DailyCategory.SHOPPING && describesPurchase)
            }.keys
            // A purchase of coffee, medicine or a ticket should use its specific category.
            val candidates = (matches - DailyCategory.SHOPPING).ifEmpty { matches }
            val saidIncome = incomeWords.any { it in memo }
            val saidExpense = amountWords.any { it in memo } || describesPurchase
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

    private fun isBareAmount(line: String, number: MatchResult): Boolean {
        val before = line.substring(0, number.range.first).trimEnd()
        val after = line.substring(number.range.last + 1)
        if (quantity.containsMatchIn(after)) return false
        return (before.isEmpty() && after.isBlank()) || bareAmountPrefixes.any { before.endsWith(it) }
    }

    private fun statedMoney(line: String): List<MatchResult> = money.findAll(line).filter { match ->
        if (!match.value.endsWith('块')) return@filter true
        val tail = line.substring(match.range.last + 1).trimStart()
        val first = tail.firstOrNull()
        if (first == null || first !in '\u3400'..'\u9fff' || first in CHINESE_NUMBER ||
            tail.startsWith("钱") || approximateSuffix.containsMatchIn(tail)) return@filter true
        // A following noun can make 块 a classifier, as in 三块蛋糕.
        val nextTransaction = listOf("早餐", "早饭", "午饭", "午餐", "中饭", "晚饭", "晚餐", "打车", "买了")
        nextTransaction.any { tail.startsWith(it) } && money.containsMatchIn(tail)
    }.toList()

    private fun normalizeFractions(line: String): String {
        val withYuan = spokenFraction.replace(line) { match ->
            val whole = requireNotNull(match.groups["yuan"]).value
            val cents = if (whole in listOf("0", "零", "〇")) 0L else parseAmount(whole)?.let(Money::parseToCents)
            if (cents == null || cents % 100L != 0L) match.value
            else {
                val fraction = if (match.groups["half"] != null) 50 else {
                    val jiao = match.groups["jiao"] ?: match.groups["shortJiao"]
                    val fen = match.groups["fen"] ?: match.groups["onlyFen"]
                    10 * spokenDigit(jiao?.value.orEmpty()) + spokenDigit(fen?.value.orEmpty())
                }
                "${Money.toInput(cents + fraction)}元"
            }
        }
        return subYuan.replace(withYuan) { match ->
            val (_, jiao, fen) = match.groupValues
            "${Money.toInput((10 * spokenDigit(jiao) + spokenDigit(fen)).toLong())}元"
        }
    }

    private fun spokenDigit(value: String): Int = value.singleOrNull()?.let { it.digitToIntOrNull() ?: digit(it) } ?: 0

    private fun splitSpokenItems(line: String): List<String> {
        if (totalWords.any { it in line }) return listOf(line)
        val explicit = statedMoney(line)
        // Offline transcripts often have neither punctuation nor a currency unit.
        val implicit = amountAtItemEnd.findAll(line).filter { number ->
            explicit.none { number.range.first in it.range } && isBareAmount(line, number)
        }
        val amounts = (explicit.asSequence() + implicit).sortedBy { it.range.first }.toList()
        if (amounts.size < 2) return listOf(line)

        val boundaries = amounts.zipWithNext { first, second ->
            var boundary = first.range.last + 1
            boundary += approximateSuffix.find(line.substring(boundary, second.range.first))?.value?.length ?: 0
            val gap = line.substring(boundary, second.range.first).trim()
            // A fresh item name is needed before splitting two adjacent amounts.
            val newItem = itemCues.any { it in gap } || mealContext.containsMatchIn(gap) || purchaseContext.containsMatchIn(gap)
            if (!newItem || gap.firstOrNull()?.let { it.isDigit() || it in CHINESE_NUMBER } == true) {
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
            // Consecutive spoken digits such as 二三 are often a range.
            if (parts.first().length > 1 && parts.first().all { digit(it) != null }) return null
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
            "今天", "今日", "今早", "今晚" -> today
            "昨天", "昨日", "昨晚", "昨夜" -> today.minusDays(1)
            "前天" -> today.minusDays(2)
            "大前天" -> today.minusDays(3)
            "明天", "明晚" -> today.plusDays(1)
            else -> if (value.endsWith("天前")) today.minusDays(value.removeSuffix("天前").toLong())
            else {
                val parts = Regex("\\d+").findAll(value).map { it.value.toInt() }.toList()
                if (parts.size == 3) LocalDate.of(parts[0], parts[1], parts[2])
                else LocalDate.of(today.year, parts[0], parts[1])
            }
        }
    } catch (_: DateTimeException) { null }
}
