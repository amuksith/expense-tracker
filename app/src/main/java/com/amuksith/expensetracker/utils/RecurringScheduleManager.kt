package com.amuksith.expensetracker.utils

import com.amuksith.expensetracker.data.RecurrenceCustomUnit
import com.amuksith.expensetracker.data.RecurrenceFrequency
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

object RecurringScheduleManager {

    fun calculateNextDueEpochDay(
        currentDueEpochDay: Long,
        frequency: RecurrenceFrequency,
        originalMonthlyDay: Int,
        customIntervalCount: Int? = null,
        customIntervalUnit: RecurrenceCustomUnit? = null
    ): Long {
        val currentDate = LocalDate.ofEpochDay(currentDueEpochDay)
        val nextDate = when (frequency) {
            RecurrenceFrequency.DAILY -> currentDate.plusDays(1)
            RecurrenceFrequency.WEEKLY -> currentDate.plusWeeks(1)
            RecurrenceFrequency.MONTHLY -> {
                val targetYearMonth = YearMonth.from(currentDate.plusMonths(1))
                val maxDayInMonth = targetYearMonth.lengthOfMonth()
                val targetDay = minOf(originalMonthlyDay, maxDayInMonth)
                targetYearMonth.atDay(targetDay)
            }
            RecurrenceFrequency.CUSTOM -> {
                val count = maxOf(1, customIntervalCount ?: 1).toLong()
                when (customIntervalUnit) {
                    RecurrenceCustomUnit.DAYS -> currentDate.plusDays(count)
                    RecurrenceCustomUnit.WEEKS -> currentDate.plusWeeks(count)
                    RecurrenceCustomUnit.MONTHS, null -> {
                        val targetYearMonth = YearMonth.from(currentDate.plusMonths(count))
                        val maxDayInMonth = targetYearMonth.lengthOfMonth()
                        val targetDay = minOf(originalMonthlyDay, maxDayInMonth)
                        targetYearMonth.atDay(targetDay)
                    }
                }
            }
        }
        return nextDate.toEpochDay()
    }

    fun calculateNextDueTimestamp(
        currentTimestamp: Long,
        frequency: RecurrenceFrequency,
        originalMonthlyDay: Int,
        customIntervalCount: Int? = null,
        customIntervalUnit: RecurrenceCustomUnit? = null,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        val zdt = Instant.ofEpochMilli(currentTimestamp).atZone(zoneId)
        val currentDate = zdt.toLocalDate()
        val time = zdt.toLocalTime()
        val nextDate = when (frequency) {
            RecurrenceFrequency.DAILY -> currentDate.plusDays(1)
            RecurrenceFrequency.WEEKLY -> currentDate.plusWeeks(1)
            RecurrenceFrequency.MONTHLY -> {
                val targetYearMonth = YearMonth.from(currentDate.plusMonths(1))
                val maxDayInMonth = targetYearMonth.lengthOfMonth()
                val targetDay = minOf(originalMonthlyDay, maxDayInMonth)
                targetYearMonth.atDay(targetDay)
            }
            RecurrenceFrequency.CUSTOM -> {
                val count = maxOf(1, customIntervalCount ?: 1).toLong()
                when (customIntervalUnit) {
                    RecurrenceCustomUnit.DAYS -> currentDate.plusDays(count)
                    RecurrenceCustomUnit.WEEKS -> currentDate.plusWeeks(count)
                    RecurrenceCustomUnit.MONTHS, null -> {
                        val targetYearMonth = YearMonth.from(currentDate.plusMonths(count))
                        val maxDayInMonth = targetYearMonth.lengthOfMonth()
                        val targetDay = minOf(originalMonthlyDay, maxDayInMonth)
                        targetYearMonth.atDay(targetDay)
                    }
                }
            }
        }
        return nextDate.atTime(time).atZone(zoneId).toInstant().toEpochMilli()
    }

    fun updateDateInTimestamp(currentTimestamp: Long, newLocalDate: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Long {
        val localTime = Instant.ofEpochMilli(currentTimestamp).atZone(zoneId).toLocalTime()
        return newLocalDate.atTime(localTime).atZone(zoneId).toInstant().toEpochMilli()
    }

    fun updateTimeInTimestamp(currentTimestamp: Long, hour: Int, minute: Int, zoneId: ZoneId = ZoneId.systemDefault()): Long {
        val localDate = Instant.ofEpochMilli(currentTimestamp).atZone(zoneId).toLocalDate()
        val localTime = LocalTime.of(hour, minute)
        return localDate.atTime(localTime).atZone(zoneId).toInstant().toEpochMilli()
    }

    fun getCurrentTodayEpochDay(zoneId: ZoneId = ZoneId.systemDefault()): Long {
        return LocalDate.now(zoneId).toEpochDay()
    }

    fun epochDayToMillis(epochDay: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long {
        return LocalDate.ofEpochDay(epochDay).atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    fun millisToEpochDay(millis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long {
        return Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate().toEpochDay()
    }

    fun formatEpochDay(epochDay: Long): String {
        val date = LocalDate.ofEpochDay(epochDay)
        val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())
        return date.format(formatter)
    }

    fun formatDate(timestampMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val date = Instant.ofEpochMilli(timestampMillis).atZone(zoneId).toLocalDate()
        val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())
        return date.format(formatter)
    }

    fun formatTime(timestampMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val time = Instant.ofEpochMilli(timestampMillis).atZone(zoneId).toLocalTime()
        val formatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())
        return time.format(formatter)
    }

    fun formatDateTime(timestampMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        return "${formatDate(timestampMillis, zoneId)}, ${formatTime(timestampMillis, zoneId)}"
    }

    fun generateFreshRevision(): Long {
        val bits = UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE
        return if (bits == 0L) 1L else bits
    }
}

