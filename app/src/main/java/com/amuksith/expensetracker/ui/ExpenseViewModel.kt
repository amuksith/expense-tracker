package com.amuksith.expensetracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.amuksith.expensetracker.data.AccountEntity
import com.amuksith.expensetracker.data.ExpenseEntity
import com.amuksith.expensetracker.data.ExpenseRepository
import com.amuksith.expensetracker.data.TransactionType
import com.amuksith.expensetracker.data.preferences.CurrencyManager
import com.amuksith.expensetracker.data.preferences.BudgetManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

enum class AnalyticsPeriod { THIS_MONTH, LAST_3_MONTHS, ALL_TIME }

enum class BudgetStatus { SAFE, WARNING, EXCEEDED }

enum class WalletDeletionMode {
    MOVE_TRANSACTIONS_AND_BALANCE,
    MOVE_REMAINING_BALANCE_ONLY,
    DELETE_EVERYTHING
}

data class CategoryBudgetProgress(
    val category: String,
    val budgetLimit: Double,
    val spentThisMonth: Double,
    val remainingAmount: Double,
    val usagePercentage: Float,
    val status: BudgetStatus
)

enum class TransactionDateFilter(val label: String) {
    ALL_TIME("All Dates"),
    TODAY("Today"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    LAST_3_MONTHS("Last 3 Months"),
    CUSTOM("Custom Range")
}

data class TransactionFilters(
    val query: String,
    val category: String?,
    val dateFilter: TransactionDateFilter,
    val customStart: Long?,
    val customEnd: Long?
)

data class CategoryStat(val category: String, val amount: Double, val percentage: Float, val count: Int)

class ExpenseViewModel(private val repository: ExpenseRepository, private val currencyManager: CurrencyManager, private val budgetManager: BudgetManager) : ViewModel() {

    private val _currentCurrencySymbol = MutableStateFlow(currencyManager.getSelectedCurrency().symbol)
    val currentCurrencySymbol: StateFlow<String> = _currentCurrencySymbol.asStateFlow()

    fun setCurrency(code: String) {
        currencyManager.setCurrency(code)
        _currentCurrencySymbol.value = currencyManager.getSelectedCurrency().symbol
    }

    val accounts: StateFlow<List<AccountEntity>> = repository.allAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedAccountId = MutableStateFlow<Int?>(null)

    private val _allExpenses = repository.allExpenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allUnfilteredExpenses: StateFlow<List<ExpenseEntity>> = _allExpenses

    val accountBalances: StateFlow<Map<Int, Double>> = combine(
        accounts,
        _allExpenses
    ) { accList, expList ->
        accList.associate { wallet ->
            var balance = wallet.initialBalance
            for (tx in expList) {
                when (tx.type) {
                    TransactionType.INCOME -> {
                        if (tx.accountId == wallet.id) balance += tx.amount
                    }
                    TransactionType.EXPENSE -> {
                        if (tx.accountId == wallet.id) balance -= tx.amount
                    }
                    TransactionType.TRANSFER -> {
                        if (tx.accountId == wallet.id) balance -= tx.amount
                        if (tx.toAccountId == wallet.id) balance += tx.amount
                    }
                }
            }
            wallet.id to balance
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val expenses: StateFlow<List<ExpenseEntity>> = combine(
        _allExpenses,
        selectedAccountId
    ) { allExp, selectedId ->
        if (selectedId == null) {
            allExp
        } else {
            allExp.filter { it.accountId == selectedId || (it.type == TransactionType.TRANSFER && it.toAccountId == selectedId) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchQuery = MutableStateFlow("")
    val selectedCategoryFilter = MutableStateFlow<String?>(null)
    val selectedDateFilter = MutableStateFlow(TransactionDateFilter.ALL_TIME)
    val customDateRange = MutableStateFlow<Pair<Long?, Long?>>(null to null)

    fun clearFilters() {
        searchQuery.value = ""
        selectedCategoryFilter.value = null
        selectedDateFilter.value = TransactionDateFilter.ALL_TIME
        customDateRange.value = null to null
    }

    private val activeFilters = combine(
        searchQuery, selectedCategoryFilter, selectedDateFilter, customDateRange
    ) { q, cat, df, customRange ->
        TransactionFilters(q, cat, df, customRange.first, customRange.second)
    }

    val hasActiveFilters: StateFlow<Boolean> = activeFilters.map {
        it.query.isNotBlank() || it.category != null || it.dateFilter != TransactionDateFilter.ALL_TIME
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val filteredExpenses: StateFlow<List<ExpenseEntity>> = combine(
        expenses,
        activeFilters
    ) { baseList, filters ->
        var list = baseList

        if (filters.query.isNotBlank()) {
            val q = filters.query.trim()
            list = list.filter {
                it.title.contains(q, ignoreCase = true) ||
                it.category.contains(q, ignoreCase = true) ||
                (it.notes?.contains(q, ignoreCase = true) ?: false) ||
                (it.invoiceNumber?.contains(q, ignoreCase = true) ?: false)
            }
        }

        if (filters.category != null) {
            list = list.filter {
                if (filters.category == "Transfer") {
                    it.type == TransactionType.TRANSFER
                } else {
                    it.type != TransactionType.TRANSFER && it.category.equals(filters.category, ignoreCase = true)
                }
            }
        }

        if (filters.dateFilter != TransactionDateFilter.ALL_TIME) {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            when (filters.dateFilter) {
                TransactionDateFilter.TODAY -> {
                    val start = calendar.timeInMillis
                    list = list.filter { it.date >= start }
                }
                TransactionDateFilter.THIS_WEEK -> {
                    calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                    val start = calendar.timeInMillis
                    list = list.filter { it.date >= start }
                }
                TransactionDateFilter.THIS_MONTH -> {
                    calendar.set(Calendar.DAY_OF_MONTH, 1)
                    val start = calendar.timeInMillis
                    list = list.filter { it.date >= start }
                }
                TransactionDateFilter.LAST_3_MONTHS -> {
                    calendar.add(Calendar.MONTH, -3)
                    val start = calendar.timeInMillis
                    list = list.filter { it.date >= start }
                }
                TransactionDateFilter.CUSTOM -> {
                    val startBound = filters.customStart?.let { 
                        val c = Calendar.getInstance().apply { timeInMillis = it }
                        c.set(Calendar.HOUR_OF_DAY, 0)
                        c.set(Calendar.MINUTE, 0)
                        c.set(Calendar.SECOND, 0)
                        c.set(Calendar.MILLISECOND, 0)
                        c.timeInMillis
                    }
                    val endBound = filters.customEnd?.let {
                        val c = Calendar.getInstance().apply { timeInMillis = it }
                        c.set(Calendar.HOUR_OF_DAY, 23)
                        c.set(Calendar.MINUTE, 59)
                        c.set(Calendar.SECOND, 59)
                        c.set(Calendar.MILLISECOND, 999)
                        c.timeInMillis
                    }
                    list = list.filter { exp ->
                        val afterStart = startBound?.let { exp.date >= it } ?: true
                        val beforeEnd = endBound?.let { exp.date <= it } ?: true
                        afterStart && beforeEnd
                    }
                }
                TransactionDateFilter.ALL_TIME -> {}
            }
        }
        
        list.sortedByDescending { it.date }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalIncome: StateFlow<Double> = combine(_allExpenses, selectedAccountId) { allExp, selectedId ->
        if (selectedId == null) {
            allExp.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        } else {
            allExp.filter { it.type == TransactionType.INCOME && it.accountId == selectedId }.sumOf { it.amount }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalExpense: StateFlow<Double> = combine(_allExpenses, selectedAccountId) { allExp, selectedId ->
        if (selectedId == null) {
            allExp.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        } else {
            allExp.filter { it.type == TransactionType.EXPENSE && it.accountId == selectedId }.sumOf { it.amount }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val netBalance: StateFlow<Double> = combine(accountBalances, selectedAccountId) { balances, selectedId ->
        if (selectedId == null) {
            balances.values.sum()
        } else {
            balances[selectedId] ?: 0.0
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val selectedPeriod = MutableStateFlow(AnalyticsPeriod.THIS_MONTH)

    private fun filterByPeriod(expenses: List<ExpenseEntity>, period: AnalyticsPeriod): List<ExpenseEntity> {
        if (period == AnalyticsPeriod.ALL_TIME) return expenses
        
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        when (period) {
            AnalyticsPeriod.THIS_MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
            }
            AnalyticsPeriod.LAST_3_MONTHS -> {
                calendar.add(Calendar.MONTH, -3)
            }
            else -> {}
        }
        val startTime = calendar.timeInMillis
        return expenses.filter { it.date >= startTime }
    }

    private val analyticsExpenses = combine(expenses, selectedPeriod) { expList, period ->
        filterByPeriod(expList.filter { it.type != TransactionType.TRANSFER }, period)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenseCategoryStats: StateFlow<List<CategoryStat>> = analyticsExpenses.map { list ->
        val filtered = list.filter { it.type == TransactionType.EXPENSE }
        val total = filtered.sumOf { it.amount }
        if (total == 0.0) emptyList()
        else {
            filtered.groupBy { it.category }
                .map { (cat, items) ->
                    val amount = items.sumOf { it.amount }
                    CategoryStat(cat, amount, (amount / total * 100).toFloat(), items.size)
                }
                .sortedByDescending { it.amount }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incomeCategoryStats: StateFlow<List<CategoryStat>> = analyticsExpenses.map { list ->
        val filtered = list.filter { it.type == TransactionType.INCOME }
        val total = filtered.sumOf { it.amount }
        if (total == 0.0) emptyList()
        else {
            filtered.groupBy { it.category }
                .map { (cat, items) ->
                    val amount = items.sumOf { it.amount }
                    CategoryStat(cat, amount, (amount / total * 100).toFloat(), items.size)
                }
                .sortedByDescending { it.amount }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val periodIncome: StateFlow<Double> = analyticsExpenses.map { list ->
        list.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val periodExpense: StateFlow<Double> = analyticsExpenses.map { list ->
        list.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    private val _categoryBudgets = MutableStateFlow<Map<String, Double>>(budgetManager.getBudgets())
    val categoryBudgets: StateFlow<Map<String, Double>> = _categoryBudgets.asStateFlow()

    fun setCategoryBudget(category: String, limit: Double) {
        budgetManager.setBudget(category, limit)
        _categoryBudgets.value = budgetManager.getBudgets()
    }

    fun removeCategoryBudget(category: String) {
        budgetManager.removeBudget(category)
        _categoryBudgets.value = budgetManager.getBudgets()
    }

    val currentMonthBudgetProgress: StateFlow<List<CategoryBudgetProgress>> = combine(
        expenses,
        _categoryBudgets
    ) { expList, budgets ->
        if (budgets.isEmpty()) return@combine emptyList()

        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfCurrentMonthMillis = calendar.timeInMillis

        val currentMonthExpenses = expList.filter { 
            it.type == TransactionType.EXPENSE && it.date >= startOfCurrentMonthMillis 
        }

        val progressList = budgets.mapNotNull { (category, limit) ->
            if (limit <= 0.0) return@mapNotNull null
            
            val spent = currentMonthExpenses.filter { it.category == category }.sumOf { it.amount }
            val pct = ((spent / limit) * 100.0).toFloat()
            
            val status = when {
                spent >= limit -> BudgetStatus.EXCEEDED
                pct >= 80f -> BudgetStatus.WARNING
                else -> BudgetStatus.SAFE
            }
            
            CategoryBudgetProgress(
                category = category,
                budgetLimit = limit,
                spentThisMonth = spent,
                remainingAmount = limit - spent,
                usagePercentage = pct,
                status = status
            )
        }
        
        progressList.sortedByDescending { it.usagePercentage }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getAccountBalance(accountId: Int): Double {
        return accountBalances.value[accountId] ?: 0.0
    }

    fun getTotalConsolidatedBalance(): Double {
        return accountBalances.value.values.sum()
    }

    private val _editingExpense = MutableStateFlow<ExpenseEntity?>(null)
    val editingExpense: StateFlow<ExpenseEntity?> = _editingExpense.asStateFlow()

    fun setEditingExpense(expense: ExpenseEntity?) {
        _editingExpense.value = expense
    }

    fun addExpense(title: String, amount: Double, category: String, date: Long, type: TransactionType, accountId: Int = 1, toAccountId: Int? = null) {
        viewModelScope.launch {
            val expense = ExpenseEntity(
                title = title,
                amount = amount,
                category = category,
                date = date,
                type = type,
                accountId = accountId,
                toAccountId = toAccountId
            )
            repository.insert(expense)
        }
    }

    fun addAccount(name: String, initialBalance: Double) {
        viewModelScope.launch {
            repository.insertAccount(
                AccountEntity(
                    name = name,
                    initialBalance = initialBalance
                )
            )
        }
    }

    fun updateAccount(account: AccountEntity) {
        viewModelScope.launch {
            repository.updateAccount(account)
        }
    }

    fun updateExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            repository.update(expense)
            _editingExpense.value = null
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            repository.delete(expense)
        }
    }

    fun getLinkedTransactionCount(accountId: Int): Int {
        val allTx = _allExpenses.value
        return allTx.count { it.accountId == accountId || (it.type == TransactionType.TRANSFER && it.toAccountId == accountId) }
    }

    fun deleteAccountSafely(
        accountToDelete: AccountEntity,
        mode: WalletDeletionMode,
        targetAccountId: Int? = null,
        newTargetWalletName: String? = null
    ) {
        val currentAccounts = accounts.value
        if (currentAccounts.size <= 1 && newTargetWalletName.isNullOrBlank()) return

        viewModelScope.launch {
            val allTx = _allExpenses.value
            val currentRemainingBalance = getAccountBalance(accountToDelete.id)
            val linkedTx = allTx.filter { it.accountId == accountToDelete.id || (it.type == TransactionType.TRANSFER && it.toAccountId == accountToDelete.id) }

            // 1. Resolve target account
            var resolvedTargetId: Int? = null
            if (mode != WalletDeletionMode.DELETE_EVERYTHING) {
                if (!newTargetWalletName.isNullOrBlank()) {
                    val newAccId = repository.insertAccount(AccountEntity(name = newTargetWalletName.trim(), initialBalance = 0.0)).toInt()
                    resolvedTargetId = newAccId
                } else {
                    resolvedTargetId = targetAccountId
                }
            }

            // We need the latest state of all accounts to adjust initialBalances
            val updatedAccounts = currentAccounts.associateBy { it.id }.toMutableMap()
            if (resolvedTargetId != null && !updatedAccounts.containsKey(resolvedTargetId)) {
                updatedAccounts[resolvedTargetId] = AccountEntity(id = resolvedTargetId, name = newTargetWalletName?.trim() ?: "New", initialBalance = 0.0)
            }

            when (mode) {
                WalletDeletionMode.MOVE_TRANSACTIONS_AND_BALANCE -> {
                    if (resolvedTargetId != null && resolvedTargetId != accountToDelete.id) {
                        val targetAcc = updatedAccounts[resolvedTargetId]!!
                        updatedAccounts[resolvedTargetId] = targetAcc.copy(initialBalance = targetAcc.initialBalance + accountToDelete.initialBalance)

                        for (tx in linkedTx) {
                            if (tx.type != TransactionType.TRANSFER && tx.accountId == accountToDelete.id) {
                                repository.update(tx.copy(accountId = resolvedTargetId))
                            } else if (tx.type == TransactionType.TRANSFER) {
                                val newFrom = if (tx.accountId == accountToDelete.id) resolvedTargetId else tx.accountId
                                val newTo = if (tx.toAccountId == accountToDelete.id) resolvedTargetId else tx.toAccountId
                                if (newFrom == newTo) {
                                    repository.delete(tx)
                                } else {
                                    repository.update(tx.copy(accountId = newFrom, toAccountId = newTo))
                                }
                            }
                        }
                    }
                }
                WalletDeletionMode.MOVE_REMAINING_BALANCE_ONLY -> {
                    if (resolvedTargetId != null && resolvedTargetId != accountToDelete.id) {
                        for (tx in linkedTx) {
                            if (tx.type == TransactionType.TRANSFER) {
                                if (tx.accountId == accountToDelete.id && tx.toAccountId != null && tx.toAccountId != accountToDelete.id) {
                                    val otherId = tx.toAccountId!!
                                    val otherAcc = updatedAccounts[otherId]
                                    if (otherAcc != null) {
                                        updatedAccounts[otherId] = otherAcc.copy(initialBalance = otherAcc.initialBalance + tx.amount)
                                    }
                                } else if (tx.toAccountId == accountToDelete.id && tx.accountId != accountToDelete.id) {
                                    val otherId = tx.accountId
                                    val otherAcc = updatedAccounts[otherId]
                                    if (otherAcc != null) {
                                        updatedAccounts[otherId] = otherAcc.copy(initialBalance = otherAcc.initialBalance - tx.amount)
                                    }
                                }
                            }
                            repository.delete(tx)
                        }
                        
                        val targetAcc = updatedAccounts[resolvedTargetId]!!
                        updatedAccounts[resolvedTargetId] = targetAcc.copy(initialBalance = targetAcc.initialBalance + currentRemainingBalance)
                    }
                }
                WalletDeletionMode.DELETE_EVERYTHING -> {
                    for (tx in linkedTx) {
                        repository.delete(tx)
                    }
                }
            }

            for (acc in updatedAccounts.values) {
                if (acc.id != accountToDelete.id) {
                    repository.updateAccount(acc)
                }
            }

            repository.deleteAccount(accountToDelete)
            
            if (selectedAccountId.value == accountToDelete.id) {
                selectedAccountId.value = null
            }
        }
    }

    fun replaceAllData(accounts: List<AccountEntity>, expenses: List<ExpenseEntity>) {
        viewModelScope.launch {
            repository.replaceAllData(accounts, expenses)
        }
    }

    fun replaceAllBudgets(budgets: Map<String, Double>) {
        budgetManager.replaceAllBudgets(budgets)
        _categoryBudgets.value = budgetManager.getBudgets()
    }
}

class ExpenseViewModelFactory(private val repository: ExpenseRepository, private val currencyManager: CurrencyManager, private val budgetManager: BudgetManager) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExpenseViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ExpenseViewModel(repository, currencyManager, budgetManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
