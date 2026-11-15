package com.zhuangxiu.ledger.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** 日期工具：数据库里日期统一存 epochDay */
object Dates {
    private val FULL = DateTimeFormatter.ofPattern("yyyy年M月d日")
    private val SHORT = DateTimeFormatter.ofPattern("M月d日")
    private val WEEKDAYS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun today(): Long = LocalDate.now().toEpochDay()

    /** 2025年3月8日 */
    fun format(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(FULL)

    /** 3月8日（跨年时带年份） */
    fun formatShort(epochDay: Long): String {
        val date = LocalDate.ofEpochDay(epochDay)
        return if (date.year == LocalDate.now().year) date.format(SHORT) else date.format(FULL)
    }

    /** 账单列表分组标题：今天 / 昨天 / 3月8日 周六 */
    fun formatGroupTitle(epochDay: Long): String {
        val diff = today() - epochDay
        val date = LocalDate.ofEpochDay(epochDay)
        val weekday = WEEKDAYS[date.dayOfWeek.value - 1]
        return when (diff) {
            0L -> "今天 · $weekday"
            1L -> "昨天 · $weekday"
            else -> "${formatShort(epochDay)} $weekday"
        }
    }

    /** DatePicker 使用 UTC 毫秒，这里做两者互转 */
    fun epochDayToUtcMillis(epochDay: Long): Long =
        LocalDate.ofEpochDay(epochDay).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun utcMillisToEpochDay(millis: Long): Long =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()

    /** 毫秒时间戳 -> 本地日期的 epochDay */
    fun millisToEpochDay(millis: Long): Long =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
}
