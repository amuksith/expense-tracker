package com.amuksith.expensetracker.data.preferences

import android.content.Context
import android.content.SharedPreferences

data class CurrencyItem(val code: String, val symbol: String, val displayName: String)

class CurrencyManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("financial_ledger_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_CURRENCY_CODE = "currency_code"
        val currencies = listOf(
            CurrencyItem("LKR", "Rs. ", "Sri Lankan Rupee (Rs.)"),
            CurrencyItem("USD", "$", "US Dollar ($)"),
            CurrencyItem("EUR", "€", "Euro (€)"),
            CurrencyItem("GBP", "£", "British Pound (£)"),
            CurrencyItem("INR", "₹", "Indian Rupee (₹)")
        )
    }

    fun getSelectedCurrencyCode(): String {
        return prefs.getString(KEY_CURRENCY_CODE, "LKR") ?: "LKR"
    }

    fun getSelectedCurrency(): CurrencyItem {
        val code = getSelectedCurrencyCode()
        return currencies.find { it.code == code } ?: currencies.first()
    }

    fun setCurrency(code: String) {
        prefs.edit().putString(KEY_CURRENCY_CODE, code).apply()
    }
}
