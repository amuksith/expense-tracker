package com.amuksith.expensetracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.amuksith.expensetracker.utils.RecurringScheduleManager
import kotlinx.coroutines.flow.Flow

enum class ConfirmResult {
    SUCCESS,
    STALE_REVIEW,
    MISSING_WALLET,
    FAILED_INSERT
}

enum class SkipResult {
    SUCCESS,
    STALE_REVIEW
}

@Dao
abstract class RecurringTransactionDao {

    @Query("SELECT * FROM recurring_transactions ORDER BY nextDueEpochDay ASC, id ASC")
    abstract fun getAllRules(): Flow<List<RecurringTransactionEntity>>

    @Query("SELECT * FROM recurring_transactions")
    abstract suspend fun getAllRulesList(): List<RecurringTransactionEntity>

    @Query("SELECT * FROM recurring_transactions WHERE id = :id")
    abstract suspend fun getRuleById(id: Int): RecurringTransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertRule(rule: RecurringTransactionEntity): Long

    @Update
    abstract suspend fun updateRule(rule: RecurringTransactionEntity)

    @Delete
    abstract suspend fun deleteRule(rule: RecurringTransactionEntity)

    @Query("DELETE FROM recurring_transactions")
    abstract suspend fun deleteAllRules()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAllRules(rules: List<RecurringTransactionEntity>)

    @Query("""
        UPDATE recurring_transactions 
        SET nextDueEpochDay = :newNextDueEpochDay, revision = :newRevision, accountId = :newAccountId, toAccountId = :newToAccountId, dueTimestamp = :newDueTimestamp
        WHERE id = :id AND nextDueEpochDay = :expectedNextDueEpochDay AND revision = :expectedRevision
    """)
    abstract suspend fun advanceRule(
        id: Int,
        expectedNextDueEpochDay: Long,
        expectedRevision: Long,
        newNextDueEpochDay: Long,
        newRevision: Long,
        newAccountId: Int,
        newToAccountId: Int?,
        newDueTimestamp: Long?
    ): Int

    @Query("""
        UPDATE recurring_transactions
        SET accountId = CASE WHEN accountId = :oldAccountId THEN :newAccountId ELSE accountId END,
            toAccountId = CASE WHEN toAccountId = :oldAccountId THEN :newAccountId ELSE toAccountId END,
            revision = revision + 1
        WHERE accountId = :oldAccountId OR toAccountId = :oldAccountId
    """)
    abstract suspend fun updateWalletForRules(oldAccountId: Int, newAccountId: Int): Int

    @Transaction
    open suspend fun confirmOccurrenceTransaction(
        ruleId: Int,
        expectedDueEpochDay: Long,
        expectedRevision: Long,
        selectedWalletId: Int,
        selectedToWalletId: Int? = null,
        currentTodayEpochDay: Long,
        expenseDao: ExpenseDao,
        accountDao: AccountDao
    ): ConfirmResult {
        val rule = getRuleById(ruleId) ?: return ConfirmResult.STALE_REVIEW
        if (rule.nextDueEpochDay != expectedDueEpochDay || rule.revision != expectedRevision) {
            return ConfirmResult.STALE_REVIEW
        }

        if (rule.nextDueEpochDay > currentTodayEpochDay) {
            return ConfirmResult.STALE_REVIEW
        }

        if (selectedWalletId == MISSING_WALLET_ID) {
            return ConfirmResult.MISSING_WALLET
        }
        val wallet = accountDao.getAccountById(selectedWalletId) ?: return ConfirmResult.MISSING_WALLET

        val finalToWalletId = if (rule.type == TransactionType.TRANSFER) {
            val toId = selectedToWalletId ?: rule.toAccountId ?: return ConfirmResult.MISSING_WALLET
            if (toId == MISSING_WALLET_ID || toId == selectedWalletId) return ConfirmResult.MISSING_WALLET
            accountDao.getAccountById(toId) ?: return ConfirmResult.MISSING_WALLET
            toId
        } else null

        val currentEffectiveTimestamp = rule.getEffectiveDueTimestamp()
        val nextDueTimestamp = RecurringScheduleManager.calculateNextDueTimestamp(
            currentTimestamp = currentEffectiveTimestamp,
            frequency = rule.frequency,
            originalMonthlyDay = rule.originalMonthlyDay,
            customIntervalCount = rule.customIntervalCount,
            customIntervalUnit = rule.customIntervalUnit
        )
        val newNextDueEpochDay = RecurringScheduleManager.millisToEpochDay(nextDueTimestamp)
        val freshRevision = RecurringScheduleManager.generateFreshRevision()

        val rowsUpdated = advanceRule(
            id = ruleId,
            expectedNextDueEpochDay = expectedDueEpochDay,
            expectedRevision = expectedRevision,
            newNextDueEpochDay = newNextDueEpochDay,
            newRevision = freshRevision,
            newAccountId = selectedWalletId,
            newToAccountId = finalToWalletId,
            newDueTimestamp = nextDueTimestamp
        )
        if (rowsUpdated == 0) {
            return ConfirmResult.STALE_REVIEW
        }

        val expense = ExpenseEntity(
            title = if (rule.title.isBlank() && rule.type == TransactionType.TRANSFER) "Transfer" else rule.title,
            amount = rule.amount,
            category = if (rule.type == TransactionType.TRANSFER) "Transfer" else rule.category,
            date = currentEffectiveTimestamp,
            type = rule.type,
            accountId = selectedWalletId,
            toAccountId = finalToWalletId,
            notes = rule.notes
        )

        val insertedId = expenseDao.insertExpense(expense)
        if (insertedId <= 0L) {
            throw IllegalStateException("Failed to insert ExpenseEntity for confirmed recurring occurrence")
        }

        return ConfirmResult.SUCCESS
    }

    @Transaction
    open suspend fun skipOccurrenceTransaction(
        ruleId: Int,
        expectedDueEpochDay: Long,
        expectedRevision: Long,
        currentTodayEpochDay: Long
    ): SkipResult {
        val rule = getRuleById(ruleId) ?: return SkipResult.STALE_REVIEW
        if (rule.nextDueEpochDay != expectedDueEpochDay || rule.revision != expectedRevision) {
            return SkipResult.STALE_REVIEW
        }

        if (rule.nextDueEpochDay > currentTodayEpochDay) {
            return SkipResult.STALE_REVIEW
        }

        val currentEffectiveTimestamp = rule.getEffectiveDueTimestamp()
        val nextDueTimestamp = RecurringScheduleManager.calculateNextDueTimestamp(
            currentTimestamp = currentEffectiveTimestamp,
            frequency = rule.frequency,
            originalMonthlyDay = rule.originalMonthlyDay,
            customIntervalCount = rule.customIntervalCount,
            customIntervalUnit = rule.customIntervalUnit
        )
        val newNextDueEpochDay = RecurringScheduleManager.millisToEpochDay(nextDueTimestamp)
        val freshRevision = RecurringScheduleManager.generateFreshRevision()

        val rowsUpdated = advanceRule(
            id = ruleId,
            expectedNextDueEpochDay = expectedDueEpochDay,
            expectedRevision = expectedRevision,
            newNextDueEpochDay = newNextDueEpochDay,
            newRevision = freshRevision,
            newAccountId = rule.accountId,
            newToAccountId = rule.toAccountId,
            newDueTimestamp = nextDueTimestamp
        )
        if (rowsUpdated == 0) {
            return SkipResult.STALE_REVIEW
        }

        return SkipResult.SUCCESS
    }
}
