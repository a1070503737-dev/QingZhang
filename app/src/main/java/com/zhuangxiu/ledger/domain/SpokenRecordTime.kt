package com.zhuangxiu.ledger.domain

import java.time.LocalTime

internal object SpokenRecordTime {
    private const val digits = "零〇一二两三四五六七八九十"
    private const val number = "(?:[0-9]{1,2}|[$digits]{1,3})"
    private const val periods = "凌晨|清早|早晨|早上|上午|今早|中午|下午|傍晚|晚上|夜里|半夜|昨晚|昨夜|今晚|明晚"
    private val clock = Regex(
        "(?<![0-9$digits:.])(?:(?<period>$periods)\\s*)?(?<hour>$number)\\s*" +
            "(?::\\s*(?<clockMinute>[0-9]{2})|[点時时]\\s*(?:钟\\s*)?" +
            "(?:(?<portion>半|一刻(?:钟)?|三刻(?:钟)?)|(?<minute>$number)\\s*分?)?)" +
            "(?![0-9$digits:点時时元块圆角毛%])",
    )

    fun time(text: String): String = clock.findAll(text).map { match ->
        val time = toTime(match)
        time?.toString() ?: match.value.trim()
    }.distinct().joinToString(" / ")

    fun withoutTime(text: String): String = clock.replace(text) { " ".repeat(it.value.length) }

    private fun toTime(match: MatchResult): LocalTime? {
        val hour = number(match.groups["hour"]!!.value) ?: return null
        val period = match.groups["period"]?.value
        val adjustedHour = when (period) {
            "下午", "傍晚", "晚上", "夜里", "昨晚", "昨夜", "今晚", "明晚" -> if (hour in 1..11) hour + 12 else hour
            "凌晨", "半夜" -> if (hour == 12) 0 else hour.takeIf { it < 12 } ?: return null
            "清早", "早晨", "早上", "上午", "今早" -> hour.takeIf { it <= 12 } ?: return null
            "中午" -> if (hour in 1..2) hour + 12 else hour
            else -> hour
        }
        val spokenMinute = match.groups["clockMinute"] ?: match.groups["minute"]
        val minute = when (val portion = match.groups["portion"]?.value) {
            null -> if (spokenMinute == null) 0 else number(spokenMinute.value) ?: return null
            "半" -> 30
            else -> if (portion.startsWith("一")) 15 else 45
        }
        return if (adjustedHour in 0..23 && minute in 0..59) LocalTime.of(adjustedHour, minute) else null
    }

    private fun number(text: String): Int? {
        text.toIntOrNull()?.let { return it }
        if ('十' in text) {
            val parts = text.split('十')
            if (parts.size != 2) return null
            val tens = if (parts[0].isEmpty()) 1 else digit(parts[0]) ?: return null
            val ones = if (parts[1].isEmpty()) 0 else digit(parts[1]) ?: return null
            return tens * 10 + ones
        }
        return text.map { digit(it.toString()) ?: return null }.joinToString("").toIntOrNull()
    }

    private fun digit(text: String): Int? {
        val char = text.singleOrNull() ?: return null
        return "零一二三四五六七八九".indexOf(when (char) {
            '〇' -> '零'
            '两' -> '二'
            else -> char
        }).takeIf { it >= 0 }
    }
}
