package com.amuksith.expensetracker.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amuksith.expensetracker.data.AccountEntity
import com.amuksith.expensetracker.data.ConfirmResult
import com.amuksith.expensetracker.data.ExpenseEntity
import com.amuksith.expensetracker.data.MISSING_WALLET_ID
import com.amuksith.expensetracker.data.RecurrenceFrequency
import com.amuksith.expensetracker.data.RecurringTransactionEntity
import com.amuksith.expensetracker.data.SkipResult
import com.amuksith.expensetracker.data.TransactionType
import com.amuksith.expensetracker.data.preferences.CurrencyManager
import com.amuksith.expensetracker.utils.BackupExportManager
import com.amuksith.expensetracker.utils.RecurringScheduleManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: ExpenseViewModel,
    onNavigateToAbout: () -> Unit = {},
    onNavigateToRecurringRules: () -> Unit = {}
) {
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
    var showCustomDateRangePicker by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var deletingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var transactionPendingDelete by remember { mutableStateOf<ExpenseEntity?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Transactions, 1: Analytics
    var analyticsMode by remember { mutableIntStateOf(0) } // 0: Expenses, 1: Income

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshTodayEpochDay()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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
                val success = BackupExportManager.exportFullBackupToJson(
                    context = context,
                    uri = uri,
                    currencyCode = currentCurrencyCode,
                    accounts = accounts,
                    expenses = viewModel.allUnfilteredExpenses.value,
                    budgets = categoryBudgets,
                    recurringRules = viewModel.recurringRules.value
                )
                snackbarHostState.showSnackbar(if (success) "Backup saved successfully." else "Failed to save backup.")
            }
        }
    }

    val jsonRestoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val result = BackupExportManager.restoreFullBackupFromJson(context, uri)
                if (result.success && result.accounts != null && result.expenses != null) {
                    viewModel.replaceAllData(result.accounts, result.expenses, result.recurringRules ?: emptyList())
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
                onBackupClick = { showBackupDialog = true },
                onAboutClick = onNavigateToAbout,
                onRecurringRulesClick = onNavigateToRecurringRules
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
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Tab Switcher
                item(key = "tab_switcher", contentType = "tab_switcher") {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SegmentedButton(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                icon = {
                                    SegmentedButtonDefaults.Icon(selectedTab == 0) {
                                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(18.dp))
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
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OptInFlowRow {
                                var dateMenuExpanded by remember { mutableStateOf(false) }
                                Box {
                                    FilterChip(
                                        selected = selectedDateFilter != TransactionDateFilter.ALL_TIME,
                                        onClick = { dateMenuExpanded = true },
                                        label = { Text(selectedDateFilter.label) },
                                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                                        leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    )

                                    DropdownMenu(
                                        expanded = dateMenuExpanded,
                                        onDismissRequest = { dateMenuExpanded = false }
                                    ) {
                                        TransactionDateFilter.entries.forEach { filter ->
                                            DropdownMenuItem(
                                                text = { Text(filter.label) },
                                                onClick = {
                                                    dateMenuExpanded = false
                                                    if (filter == TransactionDateFilter.CUSTOM) {
                                                        showCustomDateRangePicker = true
                                                    } else {
                                                        viewModel.selectedDateFilter.value = filter
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }

                                var categoryMenuExpanded by remember { mutableStateOf(false) }
                                val allCategories = remember { listOf("All Categories", "Transfer") + CategoryUtils.expenseCategories + CategoryUtils.incomeCategories }

                                Box {
                                    FilterChip(
                                        selected = selectedCategoryFilter != null,
                                        onClick = { categoryMenuExpanded = true },
                                        label = { Text(selectedCategoryFilter ?: "Category") },
                                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                                        leadingIcon = { Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    )

                                    DropdownMenu(
                                        expanded = categoryMenuExpanded,
                                        onDismissRequest = { categoryMenuExpanded = false }
                                    ) {
                                        allCategories.forEach { cat ->
                                            DropdownMenuItem(
                                                text = { Text(cat) },
                                                onClick = {
                                                    categoryMenuExpanded = false
                                                    viewModel.selectedCategoryFilter.value = if (cat == "All Categories") null else cat
                                                }
                                            )
                                        }
                                    }
                                }

                                if (hasActiveFilters) {
                                    AssistChip(
                                        onClick = { viewModel.clearFilters() },
                                        label = { Text("Clear") },
                                        leadingIcon = { Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    )
                                }
                            }
                        }
                    }

                    if (filteredExpenses.isEmpty()) {
                        item(key = "empty_transactions") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp, horizontal = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(24.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                        modifier = Modifier.size(80.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.ReceiptLong,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(40.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Text(
                                        text = if (hasActiveFilters) "No matching transactions" else "No transactions yet",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = if (hasActiveFilters) "Try adjusting your search query or filters." else "Tap + to add your first transaction.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )

                                    if (hasActiveFilters) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        OutlinedButton(onClick = { viewModel.clearFilters() }) {
                                            Text("Clear Filters")
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        items(items = filteredExpenses, key = { it.id }) { expense ->
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { dismissValue ->
                                    if (dismissValue == SwipeToDismissBoxValue.EndToStart || dismissValue == SwipeToDismissBoxValue.StartToEnd) {
                                        transactionPendingDelete = expense
                                        false
                                    } else false
                                }
                            )

                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = true,
                                enableDismissFromEndToStart = true,
                                backgroundContent = {
                                    val direction = dismissState.dismissDirection
                                    val color = if (direction == SwipeToDismissBoxValue.EndToStart || direction == SwipeToDismissBoxValue.StartToEnd) {
                                        MaterialTheme.colorScheme.errorContainer
                                    } else {
                                        Color.Transparent
                                    }
                                    val alignment = if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 16.dp, vertical = 4.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(color)
                                            .padding(horizontal = 20.dp),
                                        contentAlignment = alignment
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
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
                    }
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

    if (showBottomSheet) {
        AddExpenseBottomSheet(
            editingExpense = editingExpense,
            accounts = accounts,
            selectedAccountId = selectedAccountId,
            onDismiss = { showBottomSheet = false },
            onSave = { type, title, amount, category, accountId, toAccountId ->
                if (editingExpense == null) {
                    viewModel.addExpense(title, amount, category, System.currentTimeMillis(), type, accountId, toAccountId)
                } else {
                    viewModel.updateExpense(
                        editingExpense!!.copy(
                            title = title,
                            amount = amount,
                            category = category,
                            type = type,
                            accountId = accountId,
                            toAccountId = toAccountId
                        )
                    )
                }
                showBottomSheet = false
            }
        )
    }

    if (showCustomDateRangePicker) {
        val dateRangePickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showCustomDateRangePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val start = dateRangePickerState.selectedStartDateMillis
                        val end = dateRangePickerState.selectedEndDateMillis
                        if (start != null && end != null) {
                            viewModel.selectedDateFilter.value = TransactionDateFilter.CUSTOM
                            viewModel.customDateRange.value = start to end
                        }
                        showCustomDateRangePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDateRangePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                title = { Text("Select Date Range", modifier = Modifier.padding(16.dp)) },
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (showBackupDialog) {
        ModalBottomSheet(
            onDismissRequest = { showBackupDialog = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Data Management",
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
                    supportingContent = { Text("Save all wallets, balances, transactions, and recurring rules to a local backup file.") },
                    modifier = Modifier.clickable {
                        showBackupDialog = false
                        val date = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
                        jsonBackupLauncher.launch("expense_tracker_backup_$date.json")
                    }
                )

                ListItem(
                    headlineContent = { Text("Restore Backup (JSON)") },
                    supportingContent = { Text("Restore wallets, transactions, and recurring rules from a backup file.") },
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
            text = { Text("Restoring a backup will replace your current wallets, transactions, and recurring rules with the backup file. Continue?") },
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

    if (showAddWalletDialog) {
        AddWalletDialog(
            onDismiss = { showAddWalletDialog = false },
            onSave = { name, initialBalance ->
                viewModel.addAccount(name, initialBalance)
                showAddWalletDialog = false
            }
        )
    }

    if (editingAccount != null) {
        EditWalletDialog(
            account = editingAccount!!,
            onDismiss = { editingAccount = null },
            onSave = { updated ->
                viewModel.updateAccount(updated)
                editingAccount = null
            },
            onDelete = { accountToDelete ->
                deletingAccount = accountToDelete
                editingAccount = null
            }
        )
    }

    if (deletingAccount != null) {
        DeleteWalletDialog(
            account = deletingAccount!!,
            accounts = accounts,
            onDismiss = { deletingAccount = null },
            onConfirmDelete = { mode, targetId, newName ->
                viewModel.deleteAccountSafely(deletingAccount!!, mode, targetId, newName)
                deletingAccount = null
            }
        )
    }

    if (transactionPendingDelete != null) {
        AlertDialog(
            onDismissRequest = { transactionPendingDelete = null },
            title = { Text("Delete Transaction") },
            text = { Text("Are you sure you want to delete '${transactionPendingDelete!!.title}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteExpense(transactionPendingDelete!!)
                        transactionPendingDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { transactionPendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCurrencyDialog) {
        CurrencyDialog(
            currentCurrencySymbol = currentCurrencySymbol,
            onDismiss = { showCurrencyDialog = false },
            onSelectCurrency = { code ->
                viewModel.setCurrency(code)
                showCurrencyDialog = false
            }
        )
    }

    if (showBudgetsDialog) {
        ManageBudgetsDialog(
            categoryBudgets = categoryBudgets,
            currencySymbol = currentCurrencySymbol,
            onDismiss = { showBudgetsDialog = false },
            onSetBudget = { cat, limit ->
                viewModel.setCategoryBudget(cat, limit)
            },
            onRemoveBudget = { cat ->
                viewModel.removeCategoryBudget(cat)
            }
        )
    }
}



@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OptInFlowRow(content: @Composable RowScope.() -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        content = content
    )
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
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.width(150.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (onEditClick != null) {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Wallet",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (balance != null) {
                Text(
                    text = "$currencySymbol${String.format(Locale.US, "%.2f", balance)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = "Add",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun ExpenseItem(
    expense: ExpenseEntity,
    accounts: List<AccountEntity>,
    selectedAccountId: Int?,
    currencySymbol: String,
    onClick: () -> Unit
) {
    val walletName = remember(expense.accountId, accounts) {
        accounts.find { it.id == expense.accountId }?.name ?: "Unknown Wallet"
    }
    val toWalletName = remember(expense.toAccountId, accounts) {
        expense.toAccountId?.let { toId -> accounts.find { it.id == toId }?.name }
    }
    val dateString = remember(expense.date) {
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(expense.date))
    }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconBg = when (expense.type) {
                TransactionType.INCOME -> Color(0xFFE8F5E9)
                TransactionType.EXPENSE -> Color(0xFFFFEBEE)
                TransactionType.TRANSFER -> Color(0xFFE3F2FD)
            }
            val iconTint = when (expense.type) {
                TransactionType.INCOME -> Color(0xFF2E7D32)
                TransactionType.EXPENSE -> Color(0xFFC62828)
                TransactionType.TRANSFER -> Color(0xFF1565C0)
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (expense.type) {
                        TransactionType.TRANSFER -> Icons.Default.SwapHoriz
                        else -> CategoryUtils.getCategoryIcon(expense.category)
                    },
                    contentDescription = expense.category,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (expense.type == TransactionType.TRANSFER && toWalletName != null) {
                        "$walletName ➔ $toWalletName • $dateString"
                    } else {
                        "$walletName • ${expense.category} • $dateString"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "${if (expense.type == TransactionType.INCOME) "+" else if (expense.type == TransactionType.EXPENSE) "-" else ""}$currencySymbol${String.format(Locale.US, "%.2f", expense.amount)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = when (expense.type) {
                    TransactionType.INCOME -> Color(0xFF2E7D32)
                    TransactionType.EXPENSE -> MaterialTheme.colorScheme.error
                    TransactionType.TRANSFER -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddExpenseBottomSheet(
    editingExpense: ExpenseEntity?,
    accounts: List<AccountEntity>,
    selectedAccountId: Int?,
    onDismiss: () -> Unit,
    onSave: (TransactionType, String, Double, String, Int, Int?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedType by remember { mutableStateOf(editingExpense?.type ?: TransactionType.EXPENSE) }
    var title by remember { mutableStateOf(editingExpense?.title ?: "") }
    var amount by remember { mutableStateOf(editingExpense?.amount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    var selectedCategory by remember { mutableStateOf(editingExpense?.category ?: CategoryUtils.expenseCategories.first()) }
    var selectedFromAccountId by remember { mutableIntStateOf(editingExpense?.accountId ?: selectedAccountId ?: accounts.firstOrNull()?.id ?: 1) }
    var selectedToAccountId by remember { mutableIntStateOf(editingExpense?.toAccountId ?: accounts.firstOrNull { it.id != selectedFromAccountId }?.id ?: 2) }

    val categories = if (selectedType == TransactionType.INCOME) CategoryUtils.incomeCategories else CategoryUtils.expenseCategories

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = if (editingExpense == null) "Add Transaction" else "Edit Transaction",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedType == TransactionType.EXPENSE,
                    onClick = {
                        selectedType = TransactionType.EXPENSE
                        if (!CategoryUtils.expenseCategories.contains(selectedCategory)) {
                            selectedCategory = CategoryUtils.expenseCategories.first()
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                ) {
                    Text("Expense")
                }
                SegmentedButton(
                    selected = selectedType == TransactionType.INCOME,
                    onClick = {
                        selectedType = TransactionType.INCOME
                        if (!CategoryUtils.incomeCategories.contains(selectedCategory)) {
                            selectedCategory = CategoryUtils.incomeCategories.first()
                        }
                    },
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

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedType != TransactionType.TRANSFER) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = amount,
                onValueChange = { input ->
                    val filtered = input.filterIndexed { index, char ->
                        char.isDigit() || (char == '.' && input.indexOf('.') == index)
                    }
                    amount = filtered
                },
                label = { Text("Amount") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedType == TransactionType.TRANSFER) {
                Text("From Wallet", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                WalletSelector(accounts, selectedFromAccountId) { selectedFromAccountId = it }

                Spacer(modifier = Modifier.height(16.dp))
                Text("To Wallet", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                WalletSelector(accounts, selectedToAccountId) { selectedToAccountId = it }
            } else {
                Text("Wallet", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                WalletSelector(accounts, selectedFromAccountId) { selectedFromAccountId = it }

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

            val isTransferInvalid = selectedType == TransactionType.TRANSFER && selectedFromAccountId == selectedToAccountId
            val isTitleValid = selectedType == TransactionType.TRANSFER || title.isNotBlank()
            val isAmountValid = amount.toDoubleOrNull() != null

            Button(
                onClick = {
                    val amountValue = amount.toDoubleOrNull()
                    if (isTitleValid && amountValue != null && !isTransferInvalid) {
                        val finalTitle = if (title.isBlank() && selectedType == TransactionType.TRANSFER) "Transfer" else title
                        val finalCategory = if (selectedType == TransactionType.TRANSFER) "Transfer" else selectedCategory
                        val finalToAccountId = if (selectedType == TransactionType.TRANSFER) selectedToAccountId else null
                        onSave(selectedType, finalTitle, amountValue, finalCategory, selectedFromAccountId, finalToAccountId)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WalletSelector(
    accounts: List<AccountEntity>,
    selectedId: Int,
    onAddNewWallet: (() -> Unit)? = null,
    onSelected: (Int) -> Unit
) {
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
        if (onAddNewWallet != null) {
            FilterChip(
                selected = false,
                onClick = onAddNewWallet,
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Wallet")
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )
        }
    }
}

@Composable
fun AddWalletDialog(
    onDismiss: () -> Unit,
    onSave: (String, Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var initialBalanceText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Wallet") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Wallet Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = initialBalanceText,
                    onValueChange = { input ->
                        val filtered = input.filterIndexed { index, char ->
                            char.isDigit() || (char == '.' && input.indexOf('.') == index) || (char == '-' && index == 0)
                        }
                        initialBalanceText = filtered
                    },
                    label = { Text("Initial Balance") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val balance = initialBalanceText.toDoubleOrNull() ?: 0.0
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name.trim(), balance)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditWalletDialog(
    account: AccountEntity,
    onDismiss: () -> Unit,
    onSave: (AccountEntity) -> Unit,
    onDelete: (AccountEntity) -> Unit
) {
    var name by remember { mutableStateOf(account.name) }
    var initialBalanceText by remember { mutableStateOf(if (account.initialBalance % 1.0 == 0.0) account.initialBalance.toLong().toString() else account.initialBalance.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Wallet") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Wallet Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = initialBalanceText,
                    onValueChange = { input ->
                        val filtered = input.filterIndexed { index, char ->
                            char.isDigit() || (char == '.' && input.indexOf('.') == index) || (char == '-' && index == 0)
                        }
                        initialBalanceText = filtered
                    },
                    label = { Text("Initial Balance") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val balance = initialBalanceText.toDoubleOrNull() ?: account.initialBalance
            Row {
                TextButton(
                    onClick = { onDelete(account) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            onSave(account.copy(name = name.trim(), initialBalance = balance))
                        }
                    },
                    enabled = name.isNotBlank()
                ) {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DeleteWalletDialog(
    account: AccountEntity,
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onConfirmDelete: (WalletDeletionMode, Int?, String?) -> Unit
) {
    val remainingAccounts = accounts.filter { it.id != account.id }
    var selectedMode by remember { mutableStateOf(WalletDeletionMode.DELETE_EVERYTHING) }
    var selectedTargetId by remember { mutableIntStateOf(remainingAccounts.firstOrNull()?.id ?: 0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete '${account.name}'?") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Choose what happens to transactions and balance in this wallet:")
                Spacer(modifier = Modifier.height(12.dp))

                if (remainingAccounts.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMode = WalletDeletionMode.MOVE_TRANSACTIONS_AND_BALANCE }
                    ) {
                        RadioButton(
                            selected = selectedMode == WalletDeletionMode.MOVE_TRANSACTIONS_AND_BALANCE,
                            onClick = { selectedMode = WalletDeletionMode.MOVE_TRANSACTIONS_AND_BALANCE }
                        )
                        Text("Move transactions & balance to another wallet", style = MaterialTheme.typography.bodyMedium)
                    }

                    if (selectedMode == WalletDeletionMode.MOVE_TRANSACTIONS_AND_BALANCE) {
                        Spacer(modifier = Modifier.height(4.dp))
                        WalletSelector(accounts = remainingAccounts, selectedId = selectedTargetId) {
                            selectedTargetId = it
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMode = WalletDeletionMode.MOVE_REMAINING_BALANCE_ONLY }
                    ) {
                        RadioButton(
                            selected = selectedMode == WalletDeletionMode.MOVE_REMAINING_BALANCE_ONLY,
                            onClick = { selectedMode = WalletDeletionMode.MOVE_REMAINING_BALANCE_ONLY }
                        )
                        Text("Move balance only, retain transactions", style = MaterialTheme.typography.bodyMedium)
                    }

                    if (selectedMode == WalletDeletionMode.MOVE_REMAINING_BALANCE_ONLY) {
                        Spacer(modifier = Modifier.height(4.dp))
                        WalletSelector(accounts = remainingAccounts, selectedId = selectedTargetId) {
                            selectedTargetId = it
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedMode = WalletDeletionMode.DELETE_EVERYTHING }
                    ) {
                    RadioButton(
                        selected = selectedMode == WalletDeletionMode.DELETE_EVERYTHING,
                        onClick = { selectedMode = WalletDeletionMode.DELETE_EVERYTHING }
                    )
                    Text("Delete wallet & all its transactions", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val targetId = if (selectedMode != WalletDeletionMode.DELETE_EVERYTHING) selectedTargetId else null
                    onConfirmDelete(selectedMode, targetId, null)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Confirm Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CurrencyDialog(
    currentCurrencySymbol: String,
    onDismiss: () -> Unit,
    onSelectCurrency: (String) -> Unit
) {
    val selectedCode = CurrencyManager.currencies.find { it.symbol == currentCurrencySymbol }?.code

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Currency") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(
                    items = CurrencyManager.currencies,
                    key = { it.code }
                ) { item ->
                    val isSelected = (selectedCode != null && item.code == selectedCode) || item.symbol == currentCurrencySymbol
                    Surface(
                        onClick = { onSelectCurrency(item.code) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelectCurrency(item.code) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = item.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ManageBudgetsDialog(
    categoryBudgets: Map<String, Double>,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSetBudget: (String, Double) -> Unit,
    onRemoveBudget: (String) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(CategoryUtils.expenseCategories.first()) }
    var limitText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Monthly Budgets") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Set Monthly Budget Limit", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                OptInFlowRow {
                    CategoryUtils.expenseCategories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = {
                                selectedCategory = cat
                                limitText = categoryBudgets[cat]?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: ""
                            },
                            label = { Text(cat) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = limitText,
                    onValueChange = { input ->
                        val filtered = input.filterIndexed { index, char ->
                            char.isDigit() || (char == '.' && input.indexOf('.') == index)
                        }
                        limitText = filtered
                    },
                    label = { Text("Monthly Limit ($currencySymbol)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (categoryBudgets.containsKey(selectedCategory)) {
                        TextButton(
                            onClick = {
                                onRemoveBudget(selectedCategory)
                                limitText = ""
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Remove")
                        }
                    }
                    Button(
                        onClick = {
                            val limit = limitText.toDoubleOrNull()
                            if (limit != null && limit > 0) {
                                onSetBudget(selectedCategory, limit)
                            }
                        },
                        enabled = limitText.toDoubleOrNull()?.let { it > 0 } == true
                    ) {
                        Text("Set Budget")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                Text("Active Category Budgets", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                if (categoryBudgets.isEmpty()) {
                    Text("No category budgets set.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    categoryBudgets.forEach { (cat, limit) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(cat, fontWeight = FontWeight.SemiBold)
                            Text("$currencySymbol${String.format(Locale.US, "%.2f", limit)}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
