package com.zhuangxiu.ledger.assistant

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionDeniedException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import com.zhuangxiu.ledger.domain.LedgerEntryService
import dagger.hilt.android.AndroidEntryPoint
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@RequiresApi(36)
@AndroidEntryPoint
@AppFunctionServiceEntryPoint(
    serviceName = "LedgerAppFunctionService",
    appFunctionXmlFileName = "ledger_app_function_service",
)
abstract class BaseLedgerAppFunctionService : AppFunctionService() {
    @Inject internal lateinit var ledger: LedgerEntryService

    /**
     * Opens a new ledger draft for the user to review and save in QingZhang.
     * Only call when asked to record an entry. Nothing is saved until the user confirms in the app.
     *
     * @param bookId Optional ordinary ledger number. Omit to use the default daily ledger.
     * @param transcript Optional text dictated by the user for this entry, in Chinese. Never ask for card or account details.
     * @param requestId A new unique idempotency key for this request. Reuse the same key when retrying.
     * @return An action for opening the review screen. Offer it to the user. Do not claim an entry has been saved.
     */
    @AppFunctionDeclaration(isEnabled = false, isDescribedByKDoc = true)
    suspend fun createLedgerEntry(bookId: Long = 0, transcript: String? = null, requestId: String): PendingIntent = authorized {
        val target = ledger.writableBook(resolveAssistantBook(bookId))
        val text = transcript.orEmpty().trim()
        require(text.length <= LedgerEntryService.MAX_TRANSCRIPT) { "草稿文字最多 2000 字" }
        open(AssistantDestination.CREATE, target.id) {
            putExtra("voiceText", text)
            putExtra("voiceRequestId", assistantRequestKey(requestId))
        }
    }

    /**
     * Opens the chosen ledger and date in QingZhang. Individual transactions stay inside the app.
     *
     * @param bookId Optional ledger number. Omit to use the default daily ledger.
     * @param month Calendar month, YYYY-MM, or omit to open the current month.
     * @param date Optional exact date, YYYY-MM-DD. It must belong to the specified month if both are provided.
     * @return An action for the user to open the relevant transactions in QingZhang.
     */
    @AppFunctionDeclaration(isEnabled = false, isDescribedByKDoc = true)
    suspend fun queryLedgerEntries(bookId: Long = 0, month: String? = null, date: String? = null): PendingIntent = authorized {
        val target = ledger.book(resolveAssistantBook(bookId))
        val period = assistantPeriod(month, date)
        open(AssistantDestination.QUERY, target.id) {
            putExtra("month", period.first.toString())
            putExtra("date", period.second?.toString())
        }
    }

    /**
     * Opens monthly income and expense statistics in QingZhang. Amounts stay inside the app.
     *
     * @param bookId Optional ledger number. Omit to use the default daily ledger.
     * @param month Calendar month, YYYY-MM, or omit to open the current month.
     * @return An action for the user to view the monthly summary in QingZhang.
     */
    @AppFunctionDeclaration(isEnabled = false, isDescribedByKDoc = true)
    suspend fun openMonthlySummary(bookId: Long = 0, month: String? = null): PendingIntent = authorized {
        val target = ledger.book(resolveAssistantBook(bookId))
        val period = assistantPeriod(month, null)
        open(AssistantDestination.MONTH, target.id) { putExtra("month", period.first.toString()) }
    }

    private suspend fun authorized(action: suspend () -> PendingIntent): PendingIntent = withContext(Dispatchers.IO) {
        // Android verifies the privileged caller before dispatch. Independently check local user consent.
        if (!AssistantPermission.enabled(this@BaseLedgerAppFunctionService)) {
            throw AppFunctionDeniedException("请先在轻账设置中授权系统助手")
        }
        try {
            action()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            throw AppFunctionInvalidArgumentException(e.message ?: "账单参数无效")
        } catch (_: Exception) {
            throw AppFunctionAppUnknownException("轻账暂时无法打开此页面")
        }
    }

    private fun open(destination: AssistantDestination, bookId: Long, extras: Intent.() -> Unit): PendingIntent {
        val intent = Intent(this, AssistantLedgerActivity::class.java).apply {
            action = destination.name
            data = Uri.fromParts("qingzhang-review", UUID.randomUUID().toString(), null)
            putExtra("bookId", bookId)
            extras()
        }
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT)
    }
}

internal fun resolveAssistantBook(bookId: Long): Long {
    require(bookId >= 0) { "账本编号必须为正数" }
    return if (bookId == 0L) 1L else bookId
}

internal fun assistantRequestKey(requestId: String): String {
    require(requestId.matches(Regex("[a-zA-Z0-9_:-]{8,96}"))) { "请提供 8–96 个字符的请求编号并在重试时沿用" }
    val hash = MessageDigest.getInstance("SHA-256").digest(requestId.toByteArray(StandardCharsets.UTF_8))
    return "assistant-" + hash.joinToString("") { "%02x".format(it) }
}

internal fun assistantPeriod(month: String?, date: String?, current: YearMonth = YearMonth.now()): Pair<YearMonth, LocalDate?> {
    val exact = date?.let {
        require(it.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) { "日期格式为 YYYY-MM-DD" }
        requireNotNull(runCatching { LocalDate.parse(it) }.getOrNull()?.takeIf { day -> day.year in 1..9999 }) { "日期无效" }
    }
    val period = month?.let {
        require(it.matches(Regex("[0-9]{4}-[0-9]{2}"))) { "月份格式为 YYYY-MM" }
        requireNotNull(runCatching { YearMonth.parse(it) }.getOrNull()?.takeIf { value -> value.year in 1..9999 }) { "月份无效" }
    } ?: exact?.let(YearMonth::from) ?: current
    require(exact == null || YearMonth.from(exact) == period) { "日期与月份不一致" }
    return period to exact
}
