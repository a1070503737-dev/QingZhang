package com.zhuangxiu.ledger.assistant

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zhuangxiu.ledger.data.db.EntryKind
import com.zhuangxiu.ledger.domain.DailySummary
import com.zhuangxiu.ledger.domain.LedgerEntryService
import com.zhuangxiu.ledger.ui.components.LedgerCard
import com.zhuangxiu.ledger.ui.components.SubPageScaffold
import com.zhuangxiu.ledger.ui.theme.RenovationLedgerTheme
import com.zhuangxiu.ledger.ui.voice.VoiceEntryScreen
import com.zhuangxiu.ledger.util.Money
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class AssistantDestination { CREATE, QUERY, MONTH }

@AndroidEntryPoint
internal class AssistantLedgerActivity : ComponentActivity() {
    @Inject lateinit var ledger: LedgerEntryService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AssistantPermission.enabled(this)) {
            finish()
            return
        }
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val destination = AssistantDestination.entries.firstOrNull { it.name == intent.action }
        if (destination == null) {
            finish()
            return
        }
        val bookId = intent.getLongExtra("bookId", -1L)
        val period = if (destination == AssistantDestination.CREATE) null else
            assistantPeriod(intent.getStringExtra("month"), intent.getStringExtra("date"))
        setContent {
            RenovationLedgerTheme {
                if (period == null) {
                    VoiceEntryScreen(onBack = ::finish, onSaved = ::finish)
                } else {
                    AssistantReport(
                        ledger, bookId, period.first, period.second,
                        summaryOnly = destination == AssistantDestination.MONTH, onBack = ::finish,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!AssistantPermission.enabled(this)) finish()
    }
}

@Composable
private fun AssistantReport(
    ledger: LedgerEntryService,
    bookId: Long,
    month: YearMonth,
    date: LocalDate?,
    summaryOnly: Boolean,
    onBack: () -> Unit,
) {
    val report by produceState<Pair<String, DailySummary>?>(null, bookId, month) {
        value = withContext(Dispatchers.IO) {
            try {
                ledger.book(bookId).name to ledger.month(bookId, month)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
    }
    SubPageScaffold(title = if (summaryOnly) "月度统计" else "查询账单", onBack = onBack) { modifier ->
        val current = report
        if (current == null) {
            Text("暂未读取到账目", modifier.padding(20.dp))
        } else {
            val (name, summary) = current
            val rows = if (date == null) summary.entries else summary.entries.filter { it.day == date.toEpochDay() }
            LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text("账本 · $name  /  ${date ?: month}", style = MaterialTheme.typography.titleMedium) }
                if (summaryOnly) {
                    item {
                        LedgerCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("收入 ${Money.format(summary.totals.income)}")
                                Text("支出 ${Money.format(summary.totals.expense)}")
                                Text("结余 ${Money.format(summary.totals.balance)}")
                            }
                        }
                    }
                    items(summary.categories) { (name, amount) -> Text("$name  ${Money.format(amount)}", Modifier.padding(10.dp)) }
                } else {
                    if (rows.isEmpty()) item { Text("这段时间没有账目") }
                    items(rows, key = { it.id }) { row ->
                        LedgerCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                val sign = if (row.kind == EntryKind.INCOME) "+" else "−"
                                Text("${row.displayCategory}  $sign${Money.format(row.amountCents)}")
                                Text(LocalDate.ofEpochDay(row.day).toString(), style = MaterialTheme.typography.bodySmall)
                                if (row.note.isNotBlank()) Text(row.note)
                            }
                        }
                    }
                }
            }
        }
    }
}
