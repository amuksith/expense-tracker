package com.amuksith.expensetracker.data

import androidx.room.TypeConverter

class Converters {

    @TypeConverter
    fun fromTransactionType(value: TransactionType): String {
        return value.name
    }

    @TypeConverter
    fun toTransactionType(value: String): TransactionType {
        return try {
            enumValueOf<TransactionType>(value)
        } catch (_: Exception) {
            TransactionType.EXPENSE
        }
    }

    @TypeConverter
    fun fromRecurrenceFrequency(value: RecurrenceFrequency): String {
        return value.name
    }

    @TypeConverter
    fun toRecurrenceFrequency(value: String): RecurrenceFrequency {
        return try {
            enumValueOf<RecurrenceFrequency>(value)
        } catch (_: Exception) {
            RecurrenceFrequency.MONTHLY
        }
    }

    @TypeConverter
    fun fromCustomUnit(value: RecurrenceCustomUnit?): String? {
        return value?.name
    }

    @TypeConverter
    fun toCustomUnit(value: String?): RecurrenceCustomUnit? {
        return value?.let {
            try {
                enumValueOf<RecurrenceCustomUnit>(it)
            } catch (_: Exception) {
                null
            }
        }
    }
}
