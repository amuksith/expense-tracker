package com.amuksith.expensetracker.data

import com.amuksith.expensetracker.utils.RecurringScheduleManager
import kotlinx.coroutines.flow.Flow

data class BackupSnapshot(
    val accounts: List<AccountEntity>,
    val expenses: List<ExpenseEntity>,
    val recurringRules: List<RecurringTransactionEntity>
)

class ExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val accountDao: AccountDao,
    private val recurringTransactionDao: RecurringTransactionDao
) {

    val allExpenses: Flow<List<ExpenseEntity>> = expenseDao.getAllExpenses()
    val allAccounts: Flow<List<AccountEntity>> = accountDao.getAccounts()
    val allRecurringRules: Flow<List<RecurringTransactionEntity>> = recurringTransactionDao.getAllRules()

    suspend fun insert(expense: ExpenseEntity) {
        expenseDao.insertExpense(expense)
    }

    suspend fun update(expense: ExpenseEntity) {
        expenseDao.update(expense)
    }

    suspend fun delete(expense: ExpenseEntity) {
        expenseDao.deleteExpense(expense)
    }

    suspend fun insertAccount(account: AccountEntity): Long {
        return accountDao.insertAccount(account)
    }

    suspend fun updateAccount(account: AccountEntity) {
        accountDao.updateAccount(account)
    }

    suspend fun deleteAccount(account: AccountEntity) {
        recurringTransactionDao.updateWalletForRules(account.id, MISSING_WALLET_ID)
        accountDao.deleteAccount(account)
    }

    suspend fun insertRecurringRule(rule: RecurringTransactionEntity): Long {
        return recurringTransactionDao.insertRule(rule)
    }

    suspend fun updateRecurringRule(rule: RecurringTransactionEntity) {
        val updatedRule = rule.copy(revision = RecurringScheduleManager.generateFreshRevision())
        recurringTransactionDao.updateRule(updatedRule)
    }

    suspend fun deleteRecurringRule(rule: RecurringTransactionEntity) {
        recurringTransactionDao.deleteRule(rule)
    }

    suspend fun confirmOccurrence(
        ruleId: Int,
        expectedDueEpochDay: Long,
        expectedRevision: Long,
        selectedWalletId: Int,
        selectedToWalletId: Int? = null,
        currentTodayEpochDay: Long
    ): ConfirmResult {
        return recurringTransactionDao.confirmOccurrenceTransaction(
            ruleId = ruleId,
            expectedDueEpochDay = expectedDueEpochDay,
            expectedRevision = expectedRevision,
            selectedWalletId = selectedWalletId,
            selectedToWalletId = selectedToWalletId,
            currentTodayEpochDay = currentTodayEpochDay,
            expenseDao = expenseDao,
            accountDao = accountDao
        )
    }

    suspend fun skipOccurrence(
        ruleId: Int,
        expectedDueEpochDay: Long,
        expectedRevision: Long,
        currentTodayEpochDay: Long
    ): SkipResult {
        return recurringTransactionDao.skipOccurrenceTransaction(
            ruleId = ruleId,
            expectedDueEpochDay = expectedDueEpochDay,
            expectedRevision = expectedRevision,
            currentTodayEpochDay = currentTodayEpochDay
        )
    }

    suspend fun getBackupSnapshot(): BackupSnapshot {
        val accounts = accountDao.getAllAccountsList()
        val expenses = expenseDao.getAllExpensesList()
        val rules = recurringTransactionDao.getAllRulesList()
        return BackupSnapshot(accounts, expenses, rules)
    }

    suspend fun replaceAllData(
        accounts: List<AccountEntity>,
        expenses: List<ExpenseEntity>,
        recurringRules: List<RecurringTransactionEntity> = emptyList()
    ) {
        expenseDao.deleteAllExpenses()
        accountDao.deleteAllAccounts()
        recurringTransactionDao.deleteAllRules()

        accountDao.insertAllAccounts(accounts)
        expenseDao.insertAllExpenses(expenses)

        val freshRules = recurringRules.map { rule ->
            rule.copy(revision = RecurringScheduleManager.generateFreshRevision())
        }
        recurringTransactionDao.insertAllRules(freshRules)
    }

    suspend fun markRulesMissingWallet(deletedAccountId: Int) {
        recurringTransactionDao.updateWalletForRules(deletedAccountId, MISSING_WALLET_ID)
    }
}
