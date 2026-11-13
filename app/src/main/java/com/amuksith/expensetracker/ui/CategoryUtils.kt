package com.amuksith.expensetracker.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.amuksith.expensetracker.data.TransactionType
import com.amuksith.expensetracker.ui.theme.*
import java.util.Locale

fun formatCurrency(amount: Double, symbol: String, prefixSign: String = ""): String {
    val absAmount = kotlin.math.abs(amount)
    val formattedNumber = String.format(Locale.US, "%,.2f", absAmount)

    if (formattedNumber == "0.00") {
        return "${symbol}0.00"
    }

    return if (amount < 0) {
        "-${symbol}${formattedNumber}"
    } else if (prefixSign.isNotEmpty()) {
        "${prefixSign}${symbol}${formattedNumber}"
    } else {
        "${symbol}${formattedNumber}"
    }
}

object CategoryUtils {
    val expenseCategories = listOf("Food", "Transport", "Utilities", "Entertainment", "Health", "Shopping", "Inventory/Stock", "Other")
    val incomeCategories = listOf("Salary", "Business Sales", "Client Payment", "Refund/Discount", "Investment", "Other")

    val allFilterCategories: List<String> = (expenseCategories.filter { it != "Other" } + incomeCategories.filter { it != "Other" } + listOf("Transfer", "Other")).distinct()

    fun getCategories(type: TransactionType): List<String> {
        return if (type == TransactionType.INCOME) incomeCategories else expenseCategories
    }

    val ColorInventory = Color(0xFF8D6E63)
    val ColorSalary = Color(0xFF4CAF50)
    val ColorBusiness = Color(0xFF03A9F4)
    val ColorClient = Color(0xFF9C27B0)
    val ColorRefund = Color(0xFFFF9800)
    val ColorInvestment = Color(0xFF009688)

    fun getCategoryColor(category: String): Color {
        return when (category) {
            "Food" -> ColorFood
            "Transport" -> ColorTransport
            "Utilities" -> ColorUtilities
            "Entertainment" -> ColorEntertainment
            "Health" -> ColorHealth
            "Shopping" -> ColorShopping
            "Inventory/Stock" -> ColorInventory
            "Salary" -> ColorSalary
            "Business Sales" -> ColorBusiness
            "Client Payment" -> ColorClient
            "Refund/Discount" -> ColorRefund
            "Investment" -> ColorInvestment
            else -> ColorOther
        }
    }

    fun getCategoryIcon(category: String): ImageVector {
        return when (category) {
            "Food" -> Icons.Default.LocalDining
            "Transport" -> Icons.Default.DirectionsCar
            "Utilities" -> Icons.Default.Home
            "Entertainment" -> Icons.Default.Theaters
            "Health" -> Icons.Default.Favorite
            "Shopping" -> Icons.Default.ShoppingCart
            "Inventory/Stock" -> Icons.Default.Inventory
            "Salary" -> Icons.Default.AttachMoney
            "Business Sales" -> Icons.Default.Store
            "Client Payment" -> Icons.Default.Payment
            "Refund/Discount" -> Icons.Default.ReceiptLong
            "Investment" -> Icons.Default.TrendingUp
            else -> Icons.Default.MoreHoriz
        }
    }
}
