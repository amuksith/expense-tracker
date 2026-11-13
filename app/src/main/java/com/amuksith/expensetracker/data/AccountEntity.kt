package com.amuksith.expensetracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val initialBalance: Double = 0.0,
    val colorHex: Long = 0xFF1E88E5
)
