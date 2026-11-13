package com.amuksith.expensetracker

import com.amuksith.expensetracker.data.ExpenseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ExpenseAmountTest {

    @Test
    fun testNegativeExpenseAmountToString() {
        val expense = ExpenseEntity(
            id = 1,
            title = "Refund",
            amount = -200.0,
            category = "Other",
            date = System.currentTimeMillis()
        )
        val amountText = expense.amount.toString()
        assertEquals("-200.0", amountText)
        val parsed = amountText.toDoubleOrNull()
        assertNotNull(parsed)
        assertEquals(-200.0, parsed!!, 0.001)
    }

    @Test
    fun testAmountFilteringPreservesMinusSign() {
        val input = "-200.5"
        val filtered = input.filter { it.isDigit() || it == '.' || it == '-' }
        assertEquals("-200.5", filtered)
        val parsed = filtered.toDoubleOrNull()
        assertNotNull(parsed)
        assertEquals(-200.5, parsed!!, 0.001)
    }

    @Test
    fun testNegativeAmountParsing() {
        val amountString = "-200"
        val parsed = amountString.toDoubleOrNull()
        assertNotNull(parsed)
        assertEquals(-200.0, parsed!!, 0.001)
    }
}
