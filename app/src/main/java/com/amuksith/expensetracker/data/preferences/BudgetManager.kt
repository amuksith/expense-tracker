package com.amuksith.expensetracker.data.preferences

import android.content.Context
import android.content.SharedPreferences

class BudgetManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("budget_prefs", Context.MODE_PRIVATE)

    fun getBudgets(): Map<String, Double> {
        val budgets = mutableMapOf<String, Double>()
        prefs.all.forEach { (key, value) ->
            if (value is Float) {
                budgets[key] = value.toDouble()
            }
        }
        return budgets
    }

    fun setBudget(category: String, limit: Double) {
        if (limit <= 0.0) {
            removeBudget(category)
        } else {
            prefs.edit().putFloat(category, limit.toFloat()).apply()
        }
    }

    fun removeBudget(category: String) {
        prefs.edit().remove(category).apply()
    }

    fun replaceAllBudgets(newBudgets: Map<String, Double>) {
        val editor = prefs.edit()
        editor.clear()
        newBudgets.forEach { (category, limit) ->
            if (limit > 0.0) {
                editor.putFloat(category, limit.toFloat())
            }
        }
        editor.apply()
    }
}