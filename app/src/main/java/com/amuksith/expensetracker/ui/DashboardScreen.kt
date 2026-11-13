package com.amuksith.expensetracker.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amuksith.expensetracker.data.AccountEntity
import com.amuksith.expensetracker.data.ExpenseEntity
import com.amuksith.expensetracker.data.TransactionType
import com.amuksith.expensetracker.data.preferences.CurrencyManager
import com.amuksith.expensetracker.utils.BackupExportManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Dispatchers
import android.content.Context
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.*

private val ExpenseItemDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: ExpenseViewModel) {
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val filteredExpenses by viewModel.filteredExpenses.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategoryFilter by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()
    val selectedDateFilter by viewModel.selectedDateFilter.collectAsStateWithLifecycle()
    val hasActiveFilters by viewModel.hasActiveFilters.collectAsStateWithLifecycle()
    val customDateRange by viewModel.customDateRange.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val accountBalances by viewModel.accountBalances.collectAsStateWithLifecycle()
    val selectedAccountId by viewModel.selectedAccountId.collectAsStateWithLifecycle()
    val totalIncome by viewModel.totalIncome.collectAsStateWithLifecycle()
    val totalExpense by viewModel.totalExpense.collectAsStateWithLifecycle()
    val netBalance by viewModel.netBalance.collectAsStateWithLifecycle()
    val editingExpense by viewModel.editingExpense.collectAsStateWithLifecycle()
    val currentCurrencySymbol by viewModel.currentCurrencySymbol.collectAsStateWithLifecycle()
    
    val selectedPeriod by viewModel.selectedPeriod.collectAsStateWithLifecycle()
    val expenseCategoryStats by viewModel.expenseCategoryStats.collectAsStateWithLifecycle()
    val incomeCategoryStats by viewModel.incomeCategoryStats.collectAsStateWithLifecycle()
    val periodIncome by viewModel.periodIncome.collectAsStateWithLifecycle()
    val periodExpense by viewModel.periodExpense.collectAsStateWithLifecycle()
    
    val categoryBudgets by viewModel.categoryBudgets.collectAsStateWithLifecycle()
    val currentMonthBudgetProgress by viewModel.currentMonthBudgetProgress.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    
    val fluidHeaderState = rememberFluidHeaderState(
        listState = listState,
        collapseDistance = 130.dp
    )
    val collapseFraction = fluidHeaderState.collapseFraction
    
    var showBottomSheet by remember { mutableStateOf(false) }
    var showAddWalletDialog by remember { mutableStateOf(false) }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var showBudgetsDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var deletingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Transactions, 1: Analytics
    var analyticsMode by remember { mutableStateOf(0) } // 0: Expenses, 1: Income

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val csvExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val success = BackupExportManager.exportTransactionsToCsv(context, uri, viewModel.allUnfilteredExpenses.value, accounts)
                snackbarHostState.showSnackbar(if (success) "Exported transactions successfully." else "Failed to export transactions.")
            }
        }
    }

    val jsonBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val currentCurrencyCode = CurrencyManager.currencies.find { it.symbol == currentCurrencySymbol }?.code ?: "USD"
            coroutineScope.launch {
                val success = BackupExportManager.exportFullBackupToJson(context, uri, currentCurrencyCode, accounts, viewModel.allUnfilteredExpenses.value, categoryBudgets)
                snackbarHostState.showSnackbar(if (success) "Backup saved successfully." else "Failed to save backup.")
            }
        }
    }

    val jsonRestoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val result = BackupExportManager.restoreFullBackupFromJson(context, uri)
                if (result.success && result.accounts != null && result.expenses != null) {
                    viewModel.replaceAllData(result.accounts, result.expenses)
                    if (result.currencyCode != null) {
                        viewModel.setCurrency(result.currencyCode)
                    }
                    if (result.budgets != null) {
                        viewModel.replaceAllBudgets(result.budgets)
                    }
                }
                snackbarHostState.showSnackbar(result.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            AnimatedVisibility(
                visible = !listState.isScrollInProgress && selectedTab == 0,
                enter = fadeIn(animationSpec = tween(150)) + scaleIn(animationSpec = tween(150)),
                exit = fadeOut(animationSpec = tween(150)) + scaleOut(animationSpec = tween(150))
            ) {
                FloatingActionButton(onClick = {
                    viewModel.setEditingExpense(null)
                    showBottomSheet = true
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Transaction")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(fluidHeaderState.nestedScrollConnection)
        ) {
            FluidHeaderTitleRow(
                currentCurrencySymbol = currentCurrencySymbol,
                collapseFraction = collapseFraction,
                onCurrencyClick = { showCurrencyDialog = true },
                onBackupClick = { showBackupDialog = true }
            )
            
            FinancialOverviewCard(
                netBalance = netBalance,
                totalIncome = totalIncome,
                totalExpense = totalExpense,
                currencySymbol = currentCurrencySymbol,
                collapseFraction = collapseFraction
            )
            
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = 120.dp
                )
            ) {
                // Wallet Carousel
            item(key = "wallet_carousel", contentType = "wallets") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "all_wallets") {
                        WalletCard(
                            name = "All Wallets",
                            balance = accountBalances.values.sum(),
                            currencySymbol = currentCurrencySymbol,
                            isSelected = selectedAccountId == null,
                            onClick = { viewModel.selectedAccountId.value = null }
                        )
                    }
                    items(items = accounts, key = { "account_${it.id}" }) { account ->
                        WalletCard(
                            name = account.name,
                            balance = accountBalances[account.id] ?: 0.0,
                            currencySymbol = currentCurrencySymbol,
                            isSelected = selectedAccountId == account.id,
                            onClick = { viewModel.selectedAccountId.value = account.id },
                            onEditClick = { editingAccount = account }
                        )
                    }
                    item(key = "add_wallet") {
                        WalletCard(
                            name = "+ Add Wallet",
                            balance = null,
                            currencySymbol = currentCurrencySymbol,
                            isSelected = false,
                            onClick = { showAddWalletDialog = true }
                        )
                    }
                }
            }

            item(key = "spacer_tabs", contentType = "spacer") {
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Tab Switcher
            item(key = "tab_switcher", contentType = "tab_switcher") {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth()) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SegmentedButton(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            icon = {
                                SegmentedButtonDefaults.Icon(selectedTab == 0) {
                                    Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }
                        ) {
                            Text("Transactions")
                        }
                        SegmentedButton(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            icon = {
                                SegmentedButtonDefaults.Icon(selectedTab == 1) {
                                    Icon(Icons.Default.PieChart, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }
                        ) {
                            Text("Analytics")
                        }
                    }
                }
            }

            item(key = "spacer_after_tabs", contentType = "spacer") {
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (selectedTab == 0) {
                item(key = "search_and_filters", contentType = "filters") {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.searchQuery.value = it },
                            placeholder = { Text("Search title, notes, category...") },
                            leadingIcon = { Icon(Icons.Default.Search, "Search") },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                        Icon(Icons.Default.Clear, "Clear search")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (hasActiveFilters) {
                                item {
                                    FilterChip(
                                        selected = true,
                                        onClick = { viewModel.clearFilters() },
                                        label = { Text("Clear Filters") },
                                        leadingIcon = { Icon(Icons.Default.Clear, "Clear") }
                                    )
                                }
                            }
                            
                            item {
                                var expanded by remember { mutableStateOf(false) }
                                var showCustomDatePicker by remember { mutableStateOf(false) }
                                
                                val dateLabel = if (selectedDateFilter == TransactionDateFilter.CUSTOM && customDateRange.first != null && customDateRange.second != null) {
                                    val startStr = SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(customDateRange.first!!))
                                    val endStr = SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(customDateRange.second!!))
                                    "$startStr - $endStr"
                                } else {
                                    selectedDateFilter.label
                                }

                                Box {
                                    FilterChip(
                                        selected = selectedDateFilter != TransactionDateFilter.ALL_TIME,
                                        onClick = { expanded = true },
                                        label = { Text(dateLabel) },
                                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, "Dropdown") }
                                    )
                                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                        TransactionDateFilter.entries.forEach { filter ->
                                            DropdownMenuItem(
                                                text = { Text(filter.label) },
                                                onClick = { 
                                                    viewModel.selectedDateFilter.value = filter
                                                    if (filter == TransactionDateFilter.CUSTOM) {
                                                        showCustomDatePicker = true
                                                    }
                                                    expanded = false 
                                                }
                                            )
                                        }
                                    }
                                }

                                if (showCustomDatePicker) {
                                    val datePickerState = rememberDateRangePickerState(
                                        initialSelectedStartDateMillis = customDateRange.first,
                                        initialSelectedEndDateMillis = customDateRange.second
                                    )
                                    DatePickerDialog(
                                        onDismissRequest = { 
                                            showCustomDatePicker = false 
                                            if (customDateRange.first == null) {
                                                viewModel.selectedDateFilter.value = TransactionDateFilter.ALL_TIME
                                            }
                                        },
                                        confirmButton = {
                                            TextButton(onClick = {
                                                viewModel.customDateRange.value = datePickerState.selectedStartDateMillis to datePickerState.selectedEndDateMillis
                                                showCustomDatePicker = false
                                            }) {
                                                Text("Confirm")
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { 
                                                showCustomDatePicker = false 
                                                if (customDateRange.first == null) {
                                                    viewModel.selectedDateFilter.value = TransactionDateFilter.ALL_TIME
                                                }
                                            }) {
                                                Text("Cancel")
                                            }
                                        }
                                    ) {
                                        DateRangePicker(
                                            state = datePickerState,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                            
                            item {
                                var expanded by remember { mutableStateOf(false) }
                                val selectedColor = selectedCategoryFilter?.let { if (it == "Transfer") Color(0xFF009688) else CategoryUtils.getCategoryColor(it) }
                                
                                Box {
                                    FilterChip(
                                        selected = selectedCategoryFilter != null,
                                        onClick = { expanded = true },
                                        label = { Text(selectedCategoryFilter ?: "Category") },
                                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, "Dropdown") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = selectedColor?.copy(alpha = 0.2f) ?: Color.Transparent,
                                            selectedLabelColor = selectedColor ?: MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                        DropdownMenuItem(
                                            text = { Text("All Categories") },
                                            onClick = { 
                                                viewModel.selectedCategoryFilter.value = null
                                                expanded = false 
                                            }
                                        )
                                        CategoryUtils.allFilterCategories.forEach { cat ->
                                            DropdownMenuItem(
                                                text = { 
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(if (cat == "Transfer") Color(0xFF009688) else CategoryUtils.getCategoryColor(cat)))
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(cat)
                                                    }
                                                },
                                                onClick = { 
                                                    viewModel.selectedCategoryFilter.value = cat
                                                    expanded = false 
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                if (filteredExpenses.isEmpty()) {
                    item(key = "empty_transactions_state", contentType = "empty_state") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (hasActiveFilters || expenses.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Default.SearchOff,
                                    contentDescription = "No matching transactions",
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No matching transactions",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(onClick = { viewModel.clearFilters() }) {
                                    Text("Clear Filters")
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.ReceiptLong,
                                    contentDescription = "No transactions yet",
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No transactions yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap + to add your first transaction.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = filteredExpenses,
                        key = { "tx_${it.id}" },
                        contentType = { "transaction" }
                    ) { expense ->
                        val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = {
                            if (it == SwipeToDismissBoxValue.EndToStart) {
                                viewModel.deleteExpense(expense)
                                true
                            } else {
                                false
                            }
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        backgroundContent = {
                            val color = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                                MaterialTheme.colorScheme.error
                            } else {
                                Color.Transparent
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(color, RoundedCornerShape(12.dp))
                                    .padding(end = 16.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.onError
                                    )
                                }
                            }
                        },
                        content = {
                            ExpenseItem(
                                expense = expense,
                                accounts = accounts,
                                selectedAccountId = selectedAccountId,
                                currencySymbol = currentCurrencySymbol,
                                onClick = {
                                    viewModel.setEditingExpense(expense)
                                    showBottomSheet = true
                                }
                            )
                        }
                    )
                }
                } // Closes the 'else' of 'if (filteredExpenses.isEmpty())'
            } else {
                analyticsItems(
                    selectedPeriod = selectedPeriod,
                    onPeriodChanged = { viewModel.selectedPeriod.value = it },
                    periodIncome = periodIncome,
                    periodExpense = periodExpense,
                    currencySymbol = currentCurrencySymbol,
                    expenseCategoryStats = expenseCategoryStats,
                    incomeCategoryStats = incomeCategoryStats,
                    selectedMode = analyticsMode,
                    onModeChanged = { analyticsMode = it },
                    categoryBudgets = categoryBudgets,
                    budgetProgressList = currentMonthBudgetProgress,
                    onManageBudgetsClick = { showBudgetsDialog = true }
                )
            }
        }
        }
    }

    if (showAddWalletDialog) {
        var walletName by remember { mutableStateOf("") }
        var initialBalance by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddWalletDialog = false },
            title = { Text("Add Wallet") },
            text = {
                Column {
                    OutlinedTextField(
                        value = walletName,
                        onValueChange = { walletName = it },
                        label = { Text("Wallet Name") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = initialBalance,
                        onValueChange = { initialBalance = it },
                        label = { Text("Initial Balance") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val bal = initialBalance.toDoubleOrNull() ?: 0.0
                    if (walletName.isNotBlank()) {
                        viewModel.addAccount(walletName, bal)
                        showAddWalletDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddWalletDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showCurrencyDialog) {
        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text("Select Currency") },
            text = {
                Column(Modifier.selectableGroup()) {
                    CurrencyManager.currencies.forEach { currency ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .selectable(
                                    selected = (currency.symbol == currentCurrencySymbol),
                                    onClick = {
                                        viewModel.setCurrency(currency.code)
                                        showCurrencyDialog = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (currency.symbol == currentCurrencySymbol),
                                onClick = null // null recommended for accessibility with selectable
                            )
                            Text(
                                text = currency.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCurrencyDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (editingAccount != null) {
        val acc = editingAccount!!
        var walletName by remember(acc) { mutableStateOf(acc.name) }
        var initialBalance by remember(acc) {
            mutableStateOf(
                if (acc.initialBalance % 1.0 == 0.0) acc.initialBalance.toLong().toString() else acc.initialBalance.toString()
            )
        }
        AlertDialog(
            onDismissRequest = { editingAccount = null },
            title = { Text("Edit Wallet") },
            text = {
                Column {
                    OutlinedTextField(
                        value = walletName,
                        onValueChange = { walletName = it },
                        label = { Text("Wallet Name") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = initialBalance,
                        onValueChange = { initialBalance = it },
                        label = { Text("Initial Balance") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    
                    if (accounts.size > 1) {
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { 
                                deletingAccount = acc
                                editingAccount = null 
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Delete Wallet")
                        }
                    } else {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "This is your only wallet. You must have at least one wallet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val bal = initialBalance.toDoubleOrNull() ?: acc.initialBalance
                    if (walletName.isNotBlank()) {
                        viewModel.updateAccount(acc.copy(name = walletName, initialBalance = bal))
                        editingAccount = null
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingAccount = null }) { Text("Cancel") }
            }
        )
    }

    if (deletingAccount != null) {
        val acc = deletingAccount!!
        val linkedCount = remember(acc, expenses) { viewModel.getLinkedTransactionCount(acc.id) }
        val remainingBalance = remember(acc, accountBalances) { accountBalances[acc.id] ?: acc.initialBalance }
        val formattedRemaining = formatCurrency(remainingBalance, currentCurrencySymbol)
        val otherAccounts = remember(acc, accounts) { accounts.filter { it.id != acc.id } }

        if (linkedCount == 0 && remainingBalance == 0.0) {
            AlertDialog(
                onDismissRequest = { deletingAccount = null },
                title = { Text("Delete '${acc.name}'?") },
                text = { Text("This wallet has a 0.00 balance and no linked transactions. Are you sure you want to delete it?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteAccountSafely(acc, WalletDeletionMode.DELETE_EVERYTHING)
                            deletingAccount = null
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deletingAccount = null }) { Text("Cancel") }
                }
            )
        } else {
            var selectedMode by remember(acc) {
                mutableStateOf(
                    if (linkedCount > 0) WalletDeletionMode.MOVE_TRANSACTIONS_AND_BALANCE 
                    else if (remainingBalance != 0.0) WalletDeletionMode.MOVE_REMAINING_BALANCE_ONLY 
                    else WalletDeletionMode.DELETE_EVERYTHING
                )
            }
            var targetAccountId by remember(acc, otherAccounts) { mutableStateOf(otherAccounts.firstOrNull()?.id ?: -1) }
            var newWalletName by remember { mutableStateOf("") }
            val isNewWalletSelected = targetAccountId == -1

            AlertDialog(
                onDismissRequest = { deletingAccount = null },
                title = { Text("Delete '${acc.name}'?") },
                text = {
                    Column {
                        Text(
                            text = "Current balance: $formattedRemaining • Linked transactions: $linkedCount",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Option 1: Move Transactions & Balance
                        if (linkedCount > 0) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = selectedMode == WalletDeletionMode.MOVE_TRANSACTIONS_AND_BALANCE,
                                        onClick = { selectedMode = WalletDeletionMode.MOVE_TRANSACTIONS_AND_BALANCE },
                                        role = Role.RadioButton
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedMode == WalletDeletionMode.MOVE_TRANSACTIONS_AND_BALANCE,
                                    onClick = { selectedMode = WalletDeletionMode.MOVE_TRANSACTIONS_AND_BALANCE }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Move transactions & balance ($formattedRemaining)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            if (selectedMode == WalletDeletionMode.MOVE_TRANSACTIONS_AND_BALANCE) {
                                Column(modifier = Modifier.padding(start = 32.dp, top = 4.dp, bottom = 8.dp)) {
                                    Text(
                                        text = "Merges all $linkedCount transaction(s) and the remaining balance into the selected wallet.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Option 2: Move Remaining Balance Only
                        if (remainingBalance != 0.0 || linkedCount == 0) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = selectedMode == WalletDeletionMode.MOVE_REMAINING_BALANCE_ONLY,
                                        onClick = { selectedMode = WalletDeletionMode.MOVE_REMAINING_BALANCE_ONLY },
                                        role = Role.RadioButton
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedMode == WalletDeletionMode.MOVE_REMAINING_BALANCE_ONLY,
                                    onClick = { selectedMode = WalletDeletionMode.MOVE_REMAINING_BALANCE_ONLY }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Move only remaining balance ($formattedRemaining)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            if (selectedMode == WalletDeletionMode.MOVE_REMAINING_BALANCE_ONLY) {
                                Column(modifier = Modifier.padding(start = 32.dp, top = 4.dp, bottom = 8.dp)) {
                                    Text(
                                        text = "Transfers the balance to a wallet and removes old transaction history for '${acc.name}'.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Target Wallet Selector (Shared for Mode 1 & 2)
                        if (selectedMode == WalletDeletionMode.MOVE_TRANSACTIONS_AND_BALANCE || selectedMode == WalletDeletionMode.MOVE_REMAINING_BALANCE_ONLY) {
                            Column(modifier = Modifier.padding(start = 32.dp, top = 4.dp, bottom = 12.dp)) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    otherAccounts.forEach { otherAcc ->
                                        FilterChip(
                                            selected = targetAccountId == otherAcc.id,
                                            onClick = { targetAccountId = otherAcc.id },
                                            label = { Text(otherAcc.name) }
                                        )
                                    }
                                    FilterChip(
                                        selected = targetAccountId == -1,
                                        onClick = { targetAccountId = -1 },
                                        label = { Text("+ New Wallet") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                }
                                if (isNewWalletSelected) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = newWalletName,
                                        onValueChange = { newWalletName = it },
                                        label = { Text("New Wallet Name") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // Option 3: Delete Everything
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selectedMode == WalletDeletionMode.DELETE_EVERYTHING,
                                    onClick = { selectedMode = WalletDeletionMode.DELETE_EVERYTHING },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedMode == WalletDeletionMode.DELETE_EVERYTHING,
                                onClick = { selectedMode = WalletDeletionMode.DELETE_EVERYTHING }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Delete wallet, balance, and transactions",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (selectedMode == WalletDeletionMode.DELETE_EVERYTHING) {
                            Column(modifier = Modifier.padding(start = 32.dp, top = 4.dp)) {
                                Text(
                                    text = "Warning: Permanently deletes '${acc.name}', its balance, and $linkedCount linked transaction(s).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    val isConfirmEnabled = selectedMode == WalletDeletionMode.DELETE_EVERYTHING || 
                                           (!isNewWalletSelected && targetAccountId != -1) || 
                                           (isNewWalletSelected && newWalletName.isNotBlank())
                    TextButton(
                        onClick = {
                            val targetId = if (isNewWalletSelected) null else targetAccountId
                            val targetName = if (isNewWalletSelected) newWalletName else null
                            viewModel.deleteAccountSafely(acc, selectedMode, targetId, targetName)
                            deletingAccount = null
                        },
                        enabled = isConfirmEnabled
                    ) {
                        Text("Confirm Delete", color = if (isConfirmEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deletingAccount = null }) { Text("Cancel") }
                }
            )
        }
    }

    if (showBackupDialog) {
        ModalBottomSheet(onDismissRequest = { showBackupDialog = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Backup & Export",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                ListItem(
                    headlineContent = { Text("Export Transactions (CSV)") },
                    supportingContent = { Text("Save your transaction history as a spreadsheet-compatible file.") },
                    modifier = Modifier.clickable {
                        showBackupDialog = false
                        val date = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
                        csvExportLauncher.launch("expenses_export_$date.csv")
                    }
                )

                ListItem(
                    headlineContent = { Text("Backup App Data (JSON)") },
                    supportingContent = { Text("Save all wallets, balances, and transactions to a local backup file.") },
                    modifier = Modifier.clickable {
                        showBackupDialog = false
                        val date = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
                        jsonBackupLauncher.launch("expense_tracker_backup_$date.json")
                    }
                )

                ListItem(
                    headlineContent = { Text("Restore Backup (JSON)") },
                    supportingContent = { Text("Restore wallets and transactions from a backup file.") },
                    modifier = Modifier.clickable {
                        showBackupDialog = false
                        showRestoreConfirmDialog = true
                    }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showRestoreConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirmDialog = false },
            title = { Text("Restore Backup") },
            text = { Text("Restoring a backup will replace your current wallets and transactions with the backup file. Continue?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestoreConfirmDialog = false
                        jsonRestoreLauncher.launch(arrayOf("application/json", "*/*"))
                    }
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showBudgetsDialog) {
        ModalBottomSheet(
            onDismissRequest = { showBudgetsDialog = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Manage Monthly Budgets",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                val budgetInputs = remember(categoryBudgets) {
                    mutableStateMapOf<String, String>().apply {
                        CategoryUtils.expenseCategories.forEach { category ->
                            val existing = categoryBudgets[category]
                            put(
                                category,
                                if (existing != null && existing > 0.0) {
                                    if (existing % 1.0 == 0.0) existing.toLong().toString() else existing.toString()
                                } else ""
                            )
                        }
                    }
                }

                CategoryUtils.expenseCategories.forEach { category ->
                    val limitInput = budgetInputs[category] ?: ""
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(CategoryUtils.getCategoryColor(category).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = CategoryUtils.getCategoryIcon(category),
                                    contentDescription = category,
                                    tint = CategoryUtils.getCategoryColor(category),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(category, style = MaterialTheme.typography.bodyLarge)
                        }

                        OutlinedTextField(
                            value = limitInput,
                            onValueChange = { input -> 
                                val filtered = input.filterIndexed { index, char ->
                                    char.isDigit() || (char == '.' && input.indexOf('.') == index)
                                }
                                budgetInputs[category] = filtered
                            },
                            modifier = Modifier.width(140.dp),
                            placeholder = { Text("No Limit") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            trailingIcon = {
                                if (limitInput.isNotEmpty()) {
                                    IconButton(onClick = { budgetInputs[category] = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { 
                        CategoryUtils.expenseCategories.forEach { category ->
                            val limit = budgetInputs[category]?.toDoubleOrNull() ?: 0.0
                            viewModel.setCategoryBudget(category, limit)
                        }
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Monthly budgets saved")
                        }
                        showBudgetsDialog = false 
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Budgets")
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showBottomSheet) {
        AddExpenseBottomSheet(
            editingExpense = editingExpense,
            accounts = accounts,
            dashboardSelectedAccountId = selectedAccountId,
            onDismiss = {
                showBottomSheet = false
                viewModel.setEditingExpense(null)
            },
            onSave = { type, title, amount, category, accId, toAccId ->
                val currentEditing = editingExpense
                if (currentEditing != null) {
                    viewModel.updateExpense(
                        currentEditing.copy(
                            type = type,
                            title = title,
                            amount = amount,
                            category = category,
                            accountId = accId,
                            toAccountId = if (type == TransactionType.TRANSFER) toAccId else null
                        )
                    )
                } else {
                    viewModel.addExpense(
                        title = title,
                        amount = amount,
                        category = category,
                        date = System.currentTimeMillis(),
                        type = type,
                        accountId = accId,
                        toAccountId = if (type == TransactionType.TRANSFER) toAccId else null
                    )
                }
                showBottomSheet = false
                viewModel.setEditingExpense(null)
            }
        )
    }
}

@Composable
fun WalletCard(
    name: String,
    balance: Double?,
    currencySymbol: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onEditClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .height(80.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (onEditClick != null) {
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(28.dp)
                        .padding(2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Wallet",
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (balance != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatCurrency(balance, currencySymbol),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpenseItem(
    expense: ExpenseEntity,
    accounts: List<AccountEntity>,
    selectedAccountId: Int?,
    currencySymbol: String,
    onClick: () -> Unit = {}
) {
    val dateString = remember(expense.date) { ExpenseItemDateFormat.format(Date(expense.date)) }
    val isTransfer = expense.type == TransactionType.TRANSFER

    val categoryColor = if (isTransfer) Color(0xFF009688) else CategoryUtils.getCategoryColor(expense.category)
    val categoryIcon = if (isTransfer) Icons.Default.SwapHoriz else CategoryUtils.getCategoryIcon(expense.category)

    val amountText: String
    val amountColor: Color

    when (expense.type) {
        TransactionType.INCOME -> {
            amountText = formatCurrency(expense.amount, currencySymbol, "+")
            amountColor = Color(0xFF4CAF50)
        }
        TransactionType.EXPENSE -> {
            amountText = formatCurrency(expense.amount, currencySymbol, "-")
            amountColor = MaterialTheme.colorScheme.error
        }
        TransactionType.TRANSFER -> {
            if (selectedAccountId == null) {
                amountText = formatCurrency(expense.amount, currencySymbol)
                amountColor = MaterialTheme.colorScheme.tertiary
            } else if (selectedAccountId == expense.accountId) {
                // Sender
                amountText = formatCurrency(expense.amount, currencySymbol, "-")
                amountColor = Color(0xFFFF5722)
            } else if (selectedAccountId == expense.toAccountId) {
                // Receiver
                amountText = formatCurrency(expense.amount, currencySymbol, "+")
                amountColor = Color(0xFF4CAF50)
            } else {
                amountText = formatCurrency(expense.amount, currencySymbol)
                amountColor = MaterialTheme.colorScheme.tertiary
            }
        }
    }

    val fromAccount = accounts.find { it.id == expense.accountId }?.name ?: "Unknown"
    val toAccount = accounts.find { it.id == expense.toAccountId }?.name ?: "Unknown"
    val badgeText = if (isTransfer) "$fromAccount → $toAccount" else fromAccount

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(categoryColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIcon,
                    contentDescription = expense.category,
                    tint = categoryColor
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = expense.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dateString,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Text(
                text = amountText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                color = amountColor,
                textAlign = TextAlign.End
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddExpenseBottomSheet(
    editingExpense: ExpenseEntity? = null,
    accounts: List<AccountEntity>,
    dashboardSelectedAccountId: Int? = null,
    onDismiss: () -> Unit,
    onSave: (TransactionType, String, Double, String, Int, Int?) -> Unit
) {
    var selectedType by remember(editingExpense) { mutableStateOf(editingExpense?.type ?: TransactionType.EXPENSE) }
    var title by remember(editingExpense) { mutableStateOf(editingExpense?.title ?: "") }
    var amount by remember(editingExpense) {
        mutableStateOf(
            editingExpense?.amount?.let { valAmt ->
                if (valAmt % 1.0 == 0.0) valAmt.toLong().toString() else valAmt.toString()
            } ?: ""
        )
    }

    val categories = CategoryUtils.getCategories(if (selectedType == TransactionType.TRANSFER) TransactionType.EXPENSE else selectedType)
    var selectedCategory by remember(editingExpense, selectedType) {
        mutableStateOf(
            editingExpense?.category?.takeIf { categories.contains(it) && editingExpense.type == selectedType }
            ?: if (selectedType == TransactionType.TRANSFER) "Transfer" else categories.first()
        )
    }

    val defaultFromId = editingExpense?.accountId ?: dashboardSelectedAccountId ?: (accounts.firstOrNull()?.id ?: 1)
    var selectedAccountId by remember(editingExpense, dashboardSelectedAccountId, accounts) {
        mutableStateOf(defaultFromId)
    }

    val defaultToId = editingExpense?.toAccountId ?: (accounts.firstOrNull { it.id != defaultFromId }?.id ?: defaultFromId)
    var selectedToAccountId by remember(editingExpense, accounts) {
        mutableStateOf(defaultToId)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = if (editingExpense != null) "Edit Transaction" else "Add Transaction",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                textAlign = TextAlign.Center
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = selectedType == TransactionType.EXPENSE,
                        onClick = { selectedType = TransactionType.EXPENSE },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                    ) {
                        Text("Expense")
                    }
                    SegmentedButton(
                        selected = selectedType == TransactionType.INCOME,
                        onClick = { selectedType = TransactionType.INCOME },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                    ) {
                        Text("Income")
                    }
                    SegmentedButton(
                        selected = selectedType == TransactionType.TRANSFER,
                        onClick = { selectedType = TransactionType.TRANSFER },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                    ) {
                        Text("Transfer")
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(if (selectedType == TransactionType.TRANSFER) "Title (Optional)" else "Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = amount,
                onValueChange = { input ->
                    val filtered = input.filterIndexed { index, char ->
                        char.isDigit() || 
                        (char == '-' && index == 0) || 
                        (char == '.' && input.indexOf('.') == index)
                    }
                    amount = filtered
                },
                label = { Text("Amount") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Wallets selection
            if (selectedType == TransactionType.TRANSFER) {
                Text("From Wallet", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                WalletSelector(accounts, selectedAccountId) { newFromId ->
                    selectedAccountId = newFromId
                    if (selectedToAccountId == newFromId) {
                        selectedToAccountId = accounts.firstOrNull { it.id != newFromId }?.id ?: newFromId
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("To Wallet", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                WalletSelector(accounts, selectedToAccountId) { newToId ->
                    selectedToAccountId = newToId
                    if (selectedAccountId == newToId) {
                        selectedAccountId = accounts.firstOrNull { it.id != newToId }?.id ?: newToId
                    }
                }
            } else {
                Text("Wallet", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                WalletSelector(accounts, selectedAccountId) { selectedAccountId = it }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Category", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    categories.forEach { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(category) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CategoryUtils.getCategoryColor(category).copy(alpha = 0.2f),
                                selectedLabelColor = CategoryUtils.getCategoryColor(category)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            val isTransferInvalid = selectedType == TransactionType.TRANSFER && selectedAccountId == selectedToAccountId
            val isTitleValid = if (selectedType == TransactionType.TRANSFER) true else title.isNotBlank()
            val isAmountValid = amount.toDoubleOrNull() != null

            Button(
                onClick = {
                    val amountValue = amount.toDoubleOrNull()
                    if (isTitleValid && amountValue != null && !isTransferInvalid) {
                        val finalTitle = if (title.isBlank() && selectedType == TransactionType.TRANSFER) "Transfer" else title
                        val finalCategory = if (selectedType == TransactionType.TRANSFER) "Transfer" else selectedCategory
                        val finalToAccountId = if (selectedType == TransactionType.TRANSFER) selectedToAccountId else null
                        onSave(selectedType, finalTitle, amountValue, finalCategory, selectedAccountId, finalToAccountId)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isTitleValid && isAmountValid && !isTransferInvalid
            ) {
                Text(if (isTransferInvalid) "Select different wallets" else "Save")
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WalletSelector(accounts: List<AccountEntity>, selectedId: Int, onSelected: (Int) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        accounts.forEach { acc ->
            FilterChip(
                selected = selectedId == acc.id,
                onClick = { onSelected(acc.id) },
                label = { Text(acc.name) }
            )
        }
    }
}
