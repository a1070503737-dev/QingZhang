package com.zhuangxiu.ledger.ui.components

import com.zhuangxiu.ledger.util.Money

private const val ADD = '+'
private const val SUBTRACT = '−'
private const val MAX_CENTS = 99_999_999_999L

internal fun hasAmountOperation(amount: String): Boolean = ADD in amount || SUBTRACT in amount

internal fun amountOperationHint(amount: String): String? = when {
    !hasAmountOperation(amount) -> null
    amount.last() == ADD || amount.last() == SUBTRACT -> "请输入下一项金额"
    evaluateAmount(amount) == null -> "请检查金额，最多九位整数、两位小数"
    else -> "按 = 使用计算结果"
}

internal fun applyAmountKey(amount: String, key: String): String {
    when (key) {
        "⌫" -> return amount.dropLast(1).takeUnless { it == "-" }.orEmpty()
        "=" -> {
            if (!hasAmountOperation(amount)) return amount
            val result = evaluateAmount(amount) ?: return amount
            return Money.toInput(result).ifEmpty { "0" }
        }
        "$ADD", "$SUBTRACT" -> {
            if (amount.isEmpty()) return amount
            if (amount.last() == ADD || amount.last() == SUBTRACT) return amount.dropLast(1) + key
        }
    }
    val operand = amount.substring(amount.lastIndexOfAny(charArrayOf(ADD, SUBTRACT)) + 1)
    return when {
        key == "." && '.' in operand -> amount
        key == "." && operand.isEmpty() -> amount + "0."
        key.length == 1 && key[0] in '0'..'9' &&
            '.' in operand && operand.substringAfter('.').length == 2 -> amount
        else -> amount + key
    }
}

private fun evaluateAmount(expression: String): Long? {
    val operands = expression.removePrefix("-").split(ADD, SUBTRACT)
    val first = Money.parseToCents(operands.first()) ?: return null
    var total = if (expression.startsWith('-')) -first else first
    var index = 1
    for (operator in expression) {
        if (operator != ADD && operator != SUBTRACT) continue
        val operand = Money.parseToCents(operands[index++]) ?: return null
        total = try {
            if (operator == ADD) Math.addExact(total, operand) else Math.subtractExact(total, operand)
        } catch (_: ArithmeticException) {
            return null
        }
    }
    return total.takeIf { it in -MAX_CENTS..MAX_CENTS }
}
