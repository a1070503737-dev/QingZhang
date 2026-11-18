package com.zhuangxiu.ledger

import com.zhuangxiu.ledger.ui.components.amountOperationHint
import com.zhuangxiu.ledger.ui.components.applyAmountKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AmountCalculationTest {
    private fun press(keys: String, initial: String = ""): String =
        keys.fold(initial) { amount, key -> applyAmountKey(amount, key.toString()) }

    @Test fun addsAndSubtractsDecimalAmountsExactly() {
        assertEquals("0.03", press(".1+.2−.27="))
        assertEquals("25.5", press("12.35+15.2−2.05="))
    }

    @Test fun handlesZeroAndNegativeResultsAndContinuesCalculating() {
        assertEquals("0", press("3−3="))
        assertEquals("-2", press("3−5="))
        assertEquals("1", press("3−5=+3="))
    }

    @Test fun unfinishedAndOversizedExpressionsDoNotBecomeAmounts() {
        assertEquals("5+", press("5+="))
        assertEquals("请输入下一项金额", amountOperationHint("5+"))
        assertEquals("999999999.99+0.01", press("999999999.99+.01="))
        assertNotNull(amountOperationHint("999999999.99+0.01"))
        assertEquals("999999999.99", press("999999999.99+.01−.01="))
    }

    @Test fun replacesAnOperatorAndSupportsBackspace() {
        assertEquals("3", press("5+−2="))
        assertEquals("6", press("5+⌫+1="))
        assertEquals("1.23", press("1.234"))
        assertEquals("", press("⌫", initial = "-2"))
    }
}
