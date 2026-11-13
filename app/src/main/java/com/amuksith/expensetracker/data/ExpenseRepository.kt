package com.amuksith.expensetracker.data

import kotlinx.coroutines.flow.Flow

class ExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val accountDao: AccountDao
) {

    val allExpenses: Flow<List<ExpenseEntity>> = expenseDao.getAllExpenses()
    val allAccounts: Flow<List<AccountEntity>> = accountDao.getAccounts()

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
        accountDao.deleteAccount(account)
    }

    suspend fun replaceAllData(accounts: List<AccountEntity>, expenses: List<ExpenseEntity>) {
        expenseDao.deleteAllExpenses()
        accountDao.deleteAllAccounts()
        accountDao.insertAllAccounts(accounts)
        expenseDao.insertAllExpenses(expenses)
    }
}
