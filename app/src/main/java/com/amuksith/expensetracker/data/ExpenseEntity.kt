package com.amuksith.expensetracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val amount: Double,
    val category: String,
    val date: Long,
    var type: TransactionType = TransactionType.EXPENSE,
    var invoiceNumber: String? = null,
    var notes: String? = null,
    var accountId: Int = 1,
    var toAccountId: Int? = null
)
