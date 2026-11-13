package com.amuksith.expensetracker.data

import androidx.room.TypeConverter

class Converters {

    @TypeConverter
    fun fromTransactionType(value: TransactionType): String {
        return value.name
    }

    @TypeConverter
    fun toTransactionType(value: String): TransactionType {
        return enumValueOf<TransactionType>(value)
    }
}
