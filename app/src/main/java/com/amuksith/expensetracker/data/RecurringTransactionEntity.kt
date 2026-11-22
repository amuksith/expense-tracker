package com.amuksith.expensetracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.amuksith.expensetracker.utils.RecurringScheduleManager
import java.time.ZoneId

const val MISSING_WALLET_ID = -1

@Entity(tableName = "recurring_transactions")
data class RecurringTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val amount: Double,
    val category: String,
    val type: TransactionType = TransactionType.EXPENSE,
    val accountId: Int = 1,
    val toAccountId: Int? = null,
    val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    val customIntervalCount: Int? = null,
    val customIntervalUnit: RecurrenceCustomUnit? = null,
    val nextDueEpochDay: Long,
    val originalMonthlyDay: Int,
    val revision: Long = 1L,
    val notes: String? = null,
    val dueTimestamp: Long? = null
) {
    fun getEffectiveDueTimestamp(zoneId: ZoneId = ZoneId.systemDefault()): Long {
        return dueTimestamp ?: RecurringScheduleManager.epochDayToMillis(nextDueEpochDay, zoneId)
    }

    fun formatFrequencyLabel(): String {
        return when (frequency) {
            RecurrenceFrequency.DAILY -> "Daily"
            RecurrenceFrequency.WEEKLY -> "Weekly"
            RecurrenceFrequency.MONTHLY -> "Monthly"
            RecurrenceFrequency.CUSTOM -> {
                val count = customIntervalCount ?: 1
                val unitStr = when (customIntervalUnit) {
                    RecurrenceCustomUnit.DAYS -> if (count == 1) "Day" else "Days"
                    RecurrenceCustomUnit.WEEKS -> if (count == 1) "Week" else "Weeks"
                    RecurrenceCustomUnit.MONTHS, null -> if (count == 1) "Month" else "Months"
                }
                "Every $count $unitStr"
            }
        }
    }
}

