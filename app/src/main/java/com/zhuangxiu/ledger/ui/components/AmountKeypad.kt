package com.zhuangxiu.ledger.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun applyAmountKey(amount: String, key: String): String = when {
    key == "⌫" -> amount.dropLast(1)
    key == "." && amount.contains('.') -> amount
    key == "." && amount.isEmpty() -> "0."
    else -> amount + key
}

@Composable
internal fun AmountKeypad(enabled: Boolean = true, onKey: (String) -> Unit) {
    listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(".", "0", "⌫")).forEach { keys ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            keys.forEach { key ->
                FilledTonalButton(onClick = { onKey(key) }, enabled = enabled,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(key, fontSize = 21.sp, modifier = Modifier.semantics {
                        contentDescription = if (key == "⌫") "退格" else "数字键$key"
                    })
                }
            }
        }
    }
}
