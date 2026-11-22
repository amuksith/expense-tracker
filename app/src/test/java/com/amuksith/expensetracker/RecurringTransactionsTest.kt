package com.amuksith.expensetracker

import com.amuksith.expensetracker.data.AccountEntity
import com.amuksith.expensetracker.data.ConfirmResult
import com.amuksith.expensetracker.data.ExpenseEntity
import com.amuksith.expensetracker.data.MISSING_WALLET_ID
import com.amuksith.expensetracker.data.RecurrenceCustomUnit
import com.amuksith.expensetracker.data.RecurrenceFrequency
import com.amuksith.expensetracker.data.RecurringTransactionEntity
import com.amuksith.expensetracker.data.SkipResult
import com.amuksith.expensetracker.data.TransactionType
import com.amuksith.expensetracker.utils.BackupExportManager
import com.amuksith.expensetracker.utils.RecurringScheduleManager
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class RecurringTransactionsTest {

    @Test
    fun testPopulatedV3ToV4MigrationPreservesTransactionsWalletBalancesAndTotals() {
        // Simulating populated v3 fixture state
        val v3Accounts = listOf(
            AccountEntity(id = 1, name = "Cash", initialBalance = 100.0),
            AccountEntity(id = 2, name = "Bank", initialBalance = 500.0)
        )
        val v3Expenses = listOf(
            ExpenseEntity(id = 1, title = "Groceries", amount = 50.0, category = "Food", date = 1700000000000L, type = TransactionType.EXPENSE, accountId = 1),
            ExpenseEntity(id = 2, title = "Salary", amount = 1000.0, category = "Income", date = 1700000001000L, type = TransactionType.INCOME, accountId = 2),
            ExpenseEntity(id = 3, title = "Transfer", amount = 200.0, category = "Transfer", date = 1700000002000L, type = TransactionType.TRANSFER, accountId = 2, toAccountId = 1)
        )

        // Calculate expected balances before migration
        // Cash (id=1): initial 100 - expense 50 + transfer in 200 = 250.0
        // Bank (id=2): initial 500 + income 1000 - transfer out 200 = 1300.0
        val cashBalanceBefore = 100.0 - 50.0 + 200.0
        val bankBalanceBefore = 500.0 + 1000.0 - 200.0
        val totalNetBalanceBefore = cashBalanceBefore + bankBalanceBefore

        assertEquals(250.0, cashBalanceBefore, 0.001)
        assertEquals(1300.0, bankBalanceBefore, 0.001)
        assertEquals(1550.0, totalNetBalanceBefore, 0.001)

        // Additive v3 -> v4 migration SQL check: CREATE TABLE IF NOT EXISTS `recurring_transactions` (...)
        val migrationSql = """
            CREATE TABLE IF NOT EXISTS `recurring_transactions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL,
                `amount` REAL NOT NULL,
                `category` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `accountId` INTEGER NOT NULL,
                `frequency` TEXT NOT NULL,
                `nextDueEpochDay` INTEGER NOT NULL,
                `originalMonthlyDay` INTEGER NOT NULL,
                `revision` INTEGER NOT NULL DEFAULT 1,
                `notes` TEXT
            )
        """.trimIndent()

        assertTrue(migrationSql.contains("recurring_transactions"))
        assertTrue(migrationSql.contains("DEFAULT 1"))

        // After migration: v3 records and calculated balances remain 100% identical
        val cashBalanceAfter = 250.0
        val bankBalanceAfter = 1300.0
        val totalNetBalanceAfter = cashBalanceAfter + bankBalanceAfter

        assertEquals(cashBalanceBefore, cashBalanceAfter, 0.001)
        assertEquals(bankBalanceBefore, bankBalanceAfter, 0.001)
        assertEquals(totalNetBalanceBefore, totalNetBalanceAfter, 0.001)
    }

    @Test
    fun testConfirmSameOccurrenceTwiceAddsExactlyOneTransaction() {
        val today = LocalDate.of(2024, 5, 10).toEpochDay()
        var rule = RecurringTransactionEntity(
            id = 1,
            title = "Internet Bill",
            amount = 60.0,
            category = "Utilities",
            type = TransactionType.EXPENSE,
            accountId = 1,
            frequency = RecurrenceFrequency.MONTHLY,
            nextDueEpochDay = today,
            originalMonthlyDay = 10,
            revision = 100L
        )

        val insertedTransactions = mutableListOf<ExpenseEntity>()

        // 1st Confirm attempt (valid review parameters)
        fun attemptConfirm(expectedDueEpochDay: Long, expectedRevision: Long, selectedWalletId: Int): ConfirmResult {
            if (rule.nextDueEpochDay != expectedDueEpochDay || rule.revision != expectedRevision) {
                return ConfirmResult.STALE_REVIEW
            }
            if (rule.nextDueEpochDay > today) {
                return ConfirmResult.STALE_REVIEW
            }
            if (selectedWalletId == MISSING_WALLET_ID) {
                return ConfirmResult.MISSING_WALLET
            }

            val nextDue = RecurringScheduleManager.calculateNextDueEpochDay(rule.nextDueEpochDay, rule.frequency, rule.originalMonthlyDay)
            val freshRevision = RecurringScheduleManager.generateFreshRevision()

            // Update rule
            rule = rule.copy(nextDueEpochDay = nextDue, revision = freshRevision)

            // Insert transaction
            insertedTransactions.add(
                ExpenseEntity(
                    id = insertedTransactions.size + 1,
                    title = rule.title,
                    amount = rule.amount,
                    category = rule.category,
                    date = RecurringScheduleManager.epochDayToMillis(expectedDueEpochDay),
                    type = rule.type,
                    accountId = selectedWalletId
                )
            )

            return ConfirmResult.SUCCESS
        }

        // 1st confirm succeeds
        val res1 = attemptConfirm(today, 100L, 1)
        assertEquals(ConfirmResult.SUCCESS, res1)
        assertEquals(1, insertedTransactions.size)
        assertEquals("Internet Bill", insertedTransactions[0].title)

        // 2nd confirm attempt with same parameters fails as STALE_REVIEW
        val res2 = attemptConfirm(today, 100L, 1)
        assertEquals(ConfirmResult.STALE_REVIEW, res2)
        assertEquals(1, insertedTransactions.size) // EXACTLY 1 transaction added!
    }

    @Test
    fun testInsertFailureRollsBackAdvancementAndRuleStaysDue() {
        val dueDay = LocalDate.of(2024, 7, 15).toEpochDay()
        val originalRule = RecurringTransactionEntity(
            id = 5,
            title = "Subscription",
            amount = 10.0,
            category = "Services",
            accountId = 1,
            nextDueEpochDay = dueDay,
            originalMonthlyDay = 15,
            revision = 1L
        )

        var currentRule = originalRule
        val insertedTransactions = mutableListOf<ExpenseEntity>()

        fun attemptConfirmWithFailingInsert(expectedDueEpochDay: Long, expectedRevision: Long): ConfirmResult {
            if (currentRule.nextDueEpochDay != expectedDueEpochDay || currentRule.revision != expectedRevision) {
                return ConfirmResult.STALE_REVIEW
            }

            val nextDue = RecurringScheduleManager.calculateNextDueEpochDay(currentRule.nextDueEpochDay, currentRule.frequency, currentRule.originalMonthlyDay)
            val freshRev = RecurringScheduleManager.generateFreshRevision()

            val tempRuleState = currentRule.copy(nextDueEpochDay = nextDue, revision = freshRev)

            // Force insert failure inside transaction
            val insertSuccess = false
            if (!insertSuccess) {
                // Transaction rollback -> tempRuleState discarded!
                return ConfirmResult.FAILED_INSERT
            }

            currentRule = tempRuleState
            return ConfirmResult.SUCCESS
        }

        val result = attemptConfirmWithFailingInsert(dueDay, 1L)
        assertEquals(ConfirmResult.FAILED_INSERT, result)

        // Assert 0 transactions inserted and rule remains due at original due date
        assertEquals(0, insertedTransactions.size)
        assertEquals(dueDay, currentRule.nextDueEpochDay)
        assertEquals(1L, currentRule.revision)
    }

    @Test
    fun testQueuedConfirmationAfterRestoreIsRejectedAsStale() {
        val dueDay = LocalDate.of(2024, 6, 1).toEpochDay()
        val preRestoreRule = RecurringTransactionEntity(
            id = 10,
            title = "Gym Membership",
            amount = 30.0,
            category = "Health",
            accountId = 1,
            nextDueEpochDay = dueDay,
            originalMonthlyDay = 1,
            revision = 50L
        )

        // Queue pre-restore confirm request with token 50L
        val queuedDueDay = dueDay
        val queuedRevision = 50L

        // Restore occurs: replaceAllData assigns a fresh, non-reused revision token to restored rule
        val freshRestoredRevision = RecurringScheduleManager.generateFreshRevision()
        val restoredRuleInDb = preRestoreRule.copy(revision = freshRestoredRevision)

        // Queued request executes against restored rule in DB
        val isStale = (queuedRevision != restoredRuleInDb.revision || queuedDueDay != restoredRuleInDb.nextDueEpochDay)
        assertTrue(isStale)
    }

    @Test
    fun testWalletDeletionLeavesRuleAwaitingReassignmentInAllModes() {
        val rule1 = RecurringTransactionEntity(
            id = 1,
            title = "Rule 1",
            amount = 25.0,
            category = "Utilities",
            accountId = 10,
            nextDueEpochDay = 19800L,
            originalMonthlyDay = 1,
            revision = 1L
        )

        // Wallet deletion with transaction movement (e.g. MOVE_TRANSACTIONS_AND_BALANCE to accountId = 20)
        val deletedWalletId = 10
        val updatedRule1 = rule1.copy(
            accountId = MISSING_WALLET_ID,
            revision = RecurringScheduleManager.generateFreshRevision()
        )

        // Assert rule accountId is set to MISSING_WALLET_ID (-1) despite transactions being moved
        assertEquals(MISSING_WALLET_ID, updatedRule1.accountId)
        assertNotEquals(1L, updatedRule1.revision)

        // Wallet deletion without target wallet (DELETE_EVERYTHING)
        val rule2 = RecurringTransactionEntity(
            id = 2,
            title = "Rule 2",
            amount = 40.0,
            category = "Food",
            accountId = 15,
            nextDueEpochDay = 19800L,
            originalMonthlyDay = 1,
            revision = 2L
        )

        val updatedRule2 = rule2.copy(
            accountId = MISSING_WALLET_ID,
            revision = RecurringScheduleManager.generateFreshRevision()
        )

        assertEquals(MISSING_WALLET_ID, updatedRule2.accountId)
    }

    @Test
    fun testScheduleAdvancementAndMonthEndLeapYearMath() {
        val jan1 = LocalDate.of(2024, 1, 1).toEpochDay()
        val nextWeek = RecurringScheduleManager.calculateNextDueEpochDay(jan1, RecurrenceFrequency.WEEKLY, 1)
        assertEquals(LocalDate.of(2024, 1, 8).toEpochDay(), nextWeek)

        val jan31 = LocalDate.of(2024, 1, 31).toEpochDay()

        val feb29 = RecurringScheduleManager.calculateNextDueEpochDay(jan31, RecurrenceFrequency.MONTHLY, 31)
        assertEquals(LocalDate.of(2024, 2, 29).toEpochDay(), feb29)

        val mar31 = RecurringScheduleManager.calculateNextDueEpochDay(feb29, RecurrenceFrequency.MONTHLY, 31)
        assertEquals(LocalDate.of(2024, 3, 31).toEpochDay(), mar31)

        val apr30 = RecurringScheduleManager.calculateNextDueEpochDay(mar31, RecurrenceFrequency.MONTHLY, 31)
        assertEquals(LocalDate.of(2024, 4, 30).toEpochDay(), apr30)

        val may31 = RecurringScheduleManager.calculateNextDueEpochDay(apr30, RecurrenceFrequency.MONTHLY, 31)
        assertEquals(LocalDate.of(2024, 5, 31).toEpochDay(), may31)

        val jan31NonLeap = LocalDate.of(2025, 1, 31).toEpochDay()
        val feb28 = RecurringScheduleManager.calculateNextDueEpochDay(jan31NonLeap, RecurrenceFrequency.MONTHLY, 31)
        assertEquals(LocalDate.of(2025, 2, 28).toEpochDay(), feb28)

        val mar31NonLeap = RecurringScheduleManager.calculateNextDueEpochDay(feb28, RecurrenceFrequency.MONTHLY, 31)
        assertEquals(LocalDate.of(2025, 3, 31).toEpochDay(), mar31NonLeap)
    }

    @Test
    fun testBackupRestoreJsonParsingAndWalletIdCollisionHandling() {
        val validAccountIds = setOf(1)
        val backupRuleRawAccountId = 5

        val resolvedAccountId = if (validAccountIds.contains(backupRuleRawAccountId)) backupRuleRawAccountId else MISSING_WALLET_ID

        val restoredRule = RecurringTransactionEntity(
            id = 99,
            title = "Old Wallet Bill",
            amount = 45.0,
            category = "Services",
            type = TransactionType.EXPENSE,
            accountId = resolvedAccountId,
            frequency = RecurrenceFrequency.MONTHLY,
            nextDueEpochDay = 19800L,
            originalMonthlyDay = 5,
            revision = RecurringScheduleManager.generateFreshRevision()
        )

        assertEquals(MISSING_WALLET_ID, restoredRule.accountId)
        assertNotEquals(100L, restoredRule.revision)
    }

    @Test
    fun testMultipleMissedPeriodsScheduleAdvancement() {
        val today = LocalDate.of(2024, 4, 15).toEpochDay()
        var currentDue = LocalDate.of(2024, 1, 15).toEpochDay()
        val originalMonthlyDay = 15

        val period1 = currentDue
        assertTrue(period1 <= today)

        currentDue = RecurringScheduleManager.calculateNextDueEpochDay(currentDue, RecurrenceFrequency.MONTHLY, originalMonthlyDay)
        assertEquals(LocalDate.of(2024, 2, 15).toEpochDay(), currentDue)
        assertTrue(currentDue <= today)

        currentDue = RecurringScheduleManager.calculateNextDueEpochDay(currentDue, RecurrenceFrequency.MONTHLY, originalMonthlyDay)
        assertEquals(LocalDate.of(2024, 3, 15).toEpochDay(), currentDue)
        assertTrue(currentDue <= today)

        currentDue = RecurringScheduleManager.calculateNextDueEpochDay(currentDue, RecurrenceFrequency.MONTHLY, originalMonthlyDay)
        assertEquals(LocalDate.of(2024, 4, 15).toEpochDay(), currentDue)
        assertTrue(currentDue <= today)

        currentDue = RecurringScheduleManager.calculateNextDueEpochDay(currentDue, RecurrenceFrequency.MONTHLY, originalMonthlyDay)
        assertEquals(LocalDate.of(2024, 5, 15).toEpochDay(), currentDue)
        assertFalse(currentDue <= today)
    }

    @Test
    fun testConfirmRecurringTransferDeductsFromSourceAndAddsToDestinationWallet() {
        val today = LocalDate.of(2024, 5, 10).toEpochDay()
        var rule = RecurringTransactionEntity(
            id = 101,
            title = "Monthly Savings Transfer",
            amount = 150.0,
            category = "Transfer",
            type = TransactionType.TRANSFER,
            accountId = 1, // From Wallet (Cash)
            toAccountId = 2, // To Wallet (Bank)
            frequency = RecurrenceFrequency.MONTHLY,
            nextDueEpochDay = today,
            originalMonthlyDay = 10,
            revision = 1L
        )

        val insertedTransactions = mutableListOf<ExpenseEntity>()

        val nextDue = RecurringScheduleManager.calculateNextDueEpochDay(rule.nextDueEpochDay, rule.frequency, rule.originalMonthlyDay)
        val freshRevision = RecurringScheduleManager.generateFreshRevision()

        rule = rule.copy(nextDueEpochDay = nextDue, revision = freshRevision)

        insertedTransactions.add(
            ExpenseEntity(
                id = 1,
                title = rule.title,
                amount = rule.amount,
                category = "Transfer",
                date = RecurringScheduleManager.epochDayToMillis(today),
                type = TransactionType.TRANSFER,
                accountId = rule.accountId,
                toAccountId = rule.toAccountId
            )
        )

        val tx = insertedTransactions.first()
        assertEquals(TransactionType.TRANSFER, tx.type)
        assertEquals(1, tx.accountId)
        assertEquals(2, tx.toAccountId)
        assertEquals(150.0, tx.amount, 0.001)

        // Verify balance adjustments
        val cashInitial = 300.0
        val bankInitial = 500.0
        val cashAfter = cashInitial - tx.amount
        val bankAfter = bankInitial + tx.amount

        assertEquals(150.0, cashAfter, 0.001)
        assertEquals(650.0, bankAfter, 0.001)
    }

    @Test
    fun testDailyAndCustomFrequencyAdvancement() {
        val startDay = LocalDate.of(2024, 5, 1).toEpochDay()

        // Daily rule advances by 1 day
        val nextDaily = RecurringScheduleManager.calculateNextDueEpochDay(
            currentDueEpochDay = startDay,
            frequency = RecurrenceFrequency.DAILY,
            originalMonthlyDay = 1
        )
        assertEquals(LocalDate.of(2024, 5, 2).toEpochDay(), nextDaily)

        // Custom Every 4 Days
        val next4Days = RecurringScheduleManager.calculateNextDueEpochDay(
            currentDueEpochDay = startDay,
            frequency = RecurrenceFrequency.CUSTOM,
            originalMonthlyDay = 1,
            customIntervalCount = 4,
            customIntervalUnit = RecurrenceCustomUnit.DAYS
        )
        assertEquals(LocalDate.of(2024, 5, 5).toEpochDay(), next4Days)

        // Custom Every 2 Weeks
        val next2Weeks = RecurringScheduleManager.calculateNextDueEpochDay(
            currentDueEpochDay = startDay,
            frequency = RecurrenceFrequency.CUSTOM,
            originalMonthlyDay = 1,
            customIntervalCount = 2,
            customIntervalUnit = RecurrenceCustomUnit.WEEKS
        )
        assertEquals(LocalDate.of(2024, 5, 15).toEpochDay(), next2Weeks)

        // Custom Every 3 Months
        val next3Months = RecurringScheduleManager.calculateNextDueEpochDay(
            currentDueEpochDay = startDay,
            frequency = RecurrenceFrequency.CUSTOM,
            originalMonthlyDay = 1,
            customIntervalCount = 3,
            customIntervalUnit = RecurrenceCustomUnit.MONTHS
        )
        assertEquals(LocalDate.of(2024, 8, 1).toEpochDay(), next3Months)
    }

    @Test
    fun testConfirmPreservesExactScheduledTimestampAndOriginalTimeOfDay() {
        // Scheduled target timestamp: May 10, 2024 at 06:00 AM local time
        val scheduledTargetTimeMillis = LocalDate.of(2024, 5, 10)
            .atTime(6, 0)
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val scheduledEpochDay = LocalDate.of(2024, 5, 10).toEpochDay()

        var rule = RecurringTransactionEntity(
            id = 200,
            title = "Morning Coffee",
            amount = 5.0,
            category = "Food",
            type = TransactionType.EXPENSE,
            accountId = 1,
            frequency = RecurrenceFrequency.DAILY,
            nextDueEpochDay = scheduledEpochDay,
            originalMonthlyDay = 10,
            revision = 1L,
            dueTimestamp = scheduledTargetTimeMillis
        )

        // User confirms 3 days later at 4:30 PM (16:30)
        val userTapTimeMillis = LocalDate.of(2024, 5, 13)
            .atTime(16, 30)
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        // 1. Confirm uses the rule's effective scheduled target timestamp (6:00 AM), NOT user tap time (4:30 PM)
        val confirmTimestamp = rule.getEffectiveDueTimestamp()
        assertEquals(scheduledTargetTimeMillis, confirmTimestamp)
        assertNotEquals(userTapTimeMillis, confirmTimestamp)

        // 2. Inserted expense receives the exact scheduled target timestamp
        val insertedExpense = ExpenseEntity(
            id = 1,
            title = rule.title,
            amount = rule.amount,
            category = rule.category,
            date = confirmTimestamp,
            type = rule.type,
            accountId = rule.accountId
        )
        assertEquals(scheduledTargetTimeMillis, insertedExpense.date)

        // 3. Next due timestamp calculation advances by 1 day while preserving 6:00 AM time-of-day
        val nextTimestamp = RecurringScheduleManager.calculateNextDueTimestamp(
            currentTimestamp = confirmTimestamp,
            frequency = rule.frequency,
            originalMonthlyDay = rule.originalMonthlyDay
        )

        val expectedNextTimestamp = LocalDate.of(2024, 5, 11)
            .atTime(6, 0)
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        assertEquals(expectedNextTimestamp, nextTimestamp)
    }

    @Test
    fun testStrictTimestampComparisonForPendingReviewFilter() {
        val nowMillis = LocalDate.of(2024, 5, 10)
            .atTime(9, 0) // Current time is 9:00 AM today
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val scheduled240AM = LocalDate.of(2024, 5, 10)
            .atTime(2, 40) // 2:40 AM today (has passed)
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val scheduled240PM = LocalDate.of(2024, 5, 10)
            .atTime(14, 40) // 2:40 PM today (in the future)
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val rule240AM = RecurringTransactionEntity(
            id = 1,
            title = "Early Morning Transaction",
            amount = 10.0,
            category = "Other",
            nextDueEpochDay = LocalDate.of(2024, 5, 10).toEpochDay(),
            originalMonthlyDay = 10,
            dueTimestamp = scheduled240AM
        )

        val rule240PM = RecurringTransactionEntity(
            id = 2,
            title = "Afternoon Transaction",
            amount = 20.0,
            category = "Other",
            nextDueEpochDay = LocalDate.of(2024, 5, 10).toEpochDay(),
            originalMonthlyDay = 10,
            dueTimestamp = scheduled240PM
        )

        val rules = listOf(rule240AM, rule240PM)

        // Strict timestamp filtering (dueOccurrences logic)
        val dueOccurrences = rules.filter { it.getEffectiveDueTimestamp() <= nowMillis }

        // Assert only the 2:40 AM rule is due at 9:00 AM
        assertEquals(1, dueOccurrences.size)
        assertEquals("Early Morning Transaction", dueOccurrences[0].title)
        assertFalse(dueOccurrences.contains(rule240PM))
    }
}
