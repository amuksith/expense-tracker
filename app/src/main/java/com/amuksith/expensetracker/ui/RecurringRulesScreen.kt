package com.amuksith.expensetracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amuksith.expensetracker.data.AccountEntity
import com.amuksith.expensetracker.data.ConfirmResult
import com.amuksith.expensetracker.data.MISSING_WALLET_ID
import com.amuksith.expensetracker.data.RecurrenceCustomUnit
import com.amuksith.expensetracker.data.RecurrenceFrequency
import com.amuksith.expensetracker.data.RecurringTransactionEntity
import com.amuksith.expensetracker.data.SkipResult
import com.amuksith.expensetracker.data.TransactionType
import com.amuksith.expensetracker.utils.RecurringScheduleManager
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

@Serializable
object RecurringRulesRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringRulesScreen(
    viewModel: ExpenseViewModel,
    onNavigateBack: () -> Unit
) {
    val recurringRules by viewModel.recurringRules.collectAsStateWithLifecycle()
    val dueOccurrences by viewModel.dueOccurrences.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val currencySymbol by viewModel.currentCurrencySymbol.collectAsStateWithLifecycle()

    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<RecurringTransactionEntity?>(null) }
    var ruleToDelete by remember { mutableStateOf<RecurringTransactionEntity?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    BackHandler {
        onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scheduled") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingRule = null
                    showAddEditDialog = true
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Scheduled"
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (recurringRules.isEmpty() && dueOccurrences.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
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
                                    imageVector = Icons.Default.EventRepeat,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "No scheduled transactions yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Tap + to schedule one.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Due Occurrences Section (at top of screen)
                    if (dueOccurrences.isNotEmpty()) {
                        item(key = "due_header") {
                            Text(
                                text = "Pending Review (${dueOccurrences.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                            )
                        }

                        items(
                            items = dueOccurrences,
                            key = { "due_${it.id}" }
                        ) { rule ->
                            DueOccurrenceCard(
                                rule = rule,
                                accounts = accounts,
                                currencySymbol = currencySymbol,
                                onConfirm = { r, fromWalletId, toWalletId ->
                                    viewModel.confirmRecurringOccurrence(r, fromWalletId, toWalletId) { result ->
                                        val msg = when (result) {
                                            ConfirmResult.SUCCESS -> "Confirmed transaction '${r.title}'"
                                            ConfirmResult.STALE_REVIEW -> "Review was stale and refreshed"
                                            ConfirmResult.MISSING_WALLET -> "Please select valid wallet(s)"
                                            ConfirmResult.FAILED_INSERT -> "Failed to post transaction"
                                        }
                                        coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                },
                                onSkip = { r ->
                                    viewModel.skipRecurringOccurrence(r) { result ->
                                        val msg = when (result) {
                                            SkipResult.SUCCESS -> "Skipped occurrence for '${r.title}'"
                                            SkipResult.STALE_REVIEW -> "Review was stale and refreshed"
                                        }
                                        coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                }
                            )
                        }

                        item(key = "divider_between_due_and_rules") {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    // 2. Active Scheduled Rules Section (below due occurrences)
                    item(key = "rules_header") {
                        Text(
                            text = "Active Scheduled",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                        )
                    }

                    if (recurringRules.isEmpty()) {
                        item(key = "empty_active_rules") {
                            Text(
                                text = "No active scheduled items.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp)
                            )
                        }
                    } else {
                        items(
                            items = recurringRules,
                            key = { "rule_${it.id}" }
                        ) { rule ->
                            RecurringRuleItem(
                                rule = rule,
                                accounts = accounts,
                                currencySymbol = currencySymbol,
                                onEdit = {
                                    editingRule = rule
                                    showAddEditDialog = true
                                },
                                onDelete = {
                                    ruleToDelete = rule
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddEditDialog) {
        AddEditRecurringRuleDialog(
            editingRule = editingRule,
            accounts = accounts,
            onDismiss = { showAddEditDialog = false },
            onAddNewWallet = { name, initialBalance, onCreated ->
                viewModel.addAccount(name, initialBalance, onCreated)
            },
            onSave = { title, amount, category, type, accountId, toAccountId, frequency, customIntervalCount, customIntervalUnit, startDateEpochDay, dueTimestamp, notes ->
                if (editingRule == null) {
                    viewModel.addRecurringRule(
                        title = title,
                        amount = amount,
                        category = category,
                        type = type,
                        accountId = accountId,
                        toAccountId = toAccountId,
                        frequency = frequency,
                        customIntervalCount = customIntervalCount,
                        customIntervalUnit = customIntervalUnit,
                        startDateEpochDay = startDateEpochDay,
                        dueTimestamp = dueTimestamp,
                        notes = notes
                    )
                } else {
                    viewModel.updateRecurringRule(
                        editingRule!!.copy(
                            title = title,
                            amount = amount,
                            category = category,
                            type = type,
                            accountId = accountId,
                            toAccountId = toAccountId,
                            frequency = frequency,
                            customIntervalCount = customIntervalCount,
                            customIntervalUnit = customIntervalUnit,
                            nextDueEpochDay = startDateEpochDay,
                            originalMonthlyDay = LocalDate.ofEpochDay(startDateEpochDay).dayOfMonth,
                            dueTimestamp = dueTimestamp,
                            notes = notes
                        )
                    )
                }
                showAddEditDialog = false
            }
        )
    }

    if (ruleToDelete != null) {
        AlertDialog(
            onDismissRequest = { ruleToDelete = null },
            title = { Text("Delete Scheduled") },
            text = { Text("Are you sure you want to delete '${ruleToDelete!!.title}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        ruleToDelete?.let { viewModel.deleteRecurringRule(it) }
                        ruleToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { ruleToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DueOccurrenceCard(
    rule: RecurringTransactionEntity,
    accounts: List<AccountEntity>,
    currencySymbol: String,
    onConfirm: (rule: RecurringTransactionEntity, fromWalletId: Int, toWalletId: Int?) -> Unit,
    onSkip: (rule: RecurringTransactionEntity) -> Unit
) {
    val initialFromWallet = accounts.find { it.id == rule.accountId }?.id ?: accounts.firstOrNull()?.id ?: MISSING_WALLET_ID
    val initialToWallet = accounts.find { it.id == rule.toAccountId }?.id ?: accounts.find { it.id != initialFromWallet }?.id ?: MISSING_WALLET_ID

    var selectedFromWalletId by remember(rule.id, rule.accountId, accounts) { mutableIntStateOf(initialFromWallet) }
    var selectedToWalletId by remember(rule.id, rule.toAccountId, accounts) { mutableIntStateOf(initialToWallet) }

    var fromDropdownExpanded by remember { mutableStateOf(false) }
    var toDropdownExpanded by remember { mutableStateOf(false) }

    val isTransfer = rule.type == TransactionType.TRANSFER
    val isMissingFromWallet = selectedFromWalletId == MISSING_WALLET_ID || accounts.none { it.id == selectedFromWalletId }
    val isMissingToWallet = isTransfer && (selectedToWalletId == MISSING_WALLET_ID || accounts.none { it.id == selectedToWalletId } || selectedFromWalletId == selectedToWalletId)

    val fromWalletName = accounts.find { it.id == selectedFromWalletId }?.name ?: "Select Wallet"
    val toWalletName = accounts.find { it.id == selectedToWalletId }?.name ?: "Select Wallet"

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Title & Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = rule.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${if (isTransfer) "Transfer" else rule.category} • Scheduled: ${RecurringScheduleManager.formatDateTime(rule.getEffectiveDueTimestamp())}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "${if (rule.type == TransactionType.INCOME) "+" else if (rule.type == TransactionType.EXPENSE) "-" else ""}$currencySymbol${String.format(Locale.US, "%.2f", rule.amount)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = when (rule.type) {
                        TransactionType.INCOME -> Color(0xFF4CAF50)
                        TransactionType.EXPENSE -> MaterialTheme.colorScheme.error
                        TransactionType.TRANSFER -> MaterialTheme.colorScheme.primary
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (isTransfer) {
                Text("From Wallet", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { fromDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isMissingFromWallet) "Select From Wallet" else fromWalletName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isMissingFromWallet) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }
                    DropdownMenu(
                        expanded = fromDropdownExpanded,
                        onDismissRequest = { fromDropdownExpanded = false }
                    ) {
                        accounts.forEach { acc ->
                            DropdownMenuItem(
                                text = { Text(acc.name) },
                                onClick = {
                                    selectedFromWalletId = acc.id
                                    if (selectedToWalletId == acc.id) {
                                        selectedToWalletId = accounts.find { it.id != acc.id }?.id ?: MISSING_WALLET_ID
                                    }
                                    fromDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("To Wallet", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { toDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isMissingToWallet) "Select To Wallet" else toWalletName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isMissingToWallet) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }
                    DropdownMenu(
                        expanded = toDropdownExpanded,
                        onDismissRequest = { toDropdownExpanded = false }
                    ) {
                        accounts.forEach { acc ->
                            DropdownMenuItem(
                                text = { Text(acc.name) },
                                onClick = {
                                    selectedToWalletId = acc.id
                                    if (selectedFromWalletId == acc.id) {
                                        selectedFromWalletId = accounts.find { it.id != acc.id }?.id ?: MISSING_WALLET_ID
                                    }
                                    toDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { fromDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isMissingFromWallet) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Wallet Missing",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    Text(
                                        text = "Wallet: $fromWalletName",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select Wallet"
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = fromDropdownExpanded,
                        onDismissRequest = { fromDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        accounts.forEach { acc ->
                            DropdownMenuItem(
                                text = { Text(acc.name) },
                                onClick = {
                                    selectedFromWalletId = acc.id
                                    fromDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { onSkip(rule) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Skip",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Skip", fontWeight = FontWeight.SemiBold)
                }

                val isConfirmEnabled = if (isTransfer) {
                    !isMissingFromWallet && !isMissingToWallet && selectedFromWalletId != selectedToWalletId && selectedFromWalletId != MISSING_WALLET_ID && selectedToWalletId != MISSING_WALLET_ID
                } else {
                    !isMissingFromWallet && selectedFromWalletId != MISSING_WALLET_ID
                }

                Button(
                    onClick = { onConfirm(rule, selectedFromWalletId, if (isTransfer) selectedToWalletId else null) },
                    enabled = isConfirmEnabled,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Confirm",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Confirm", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun RecurringRuleItem(
    rule: RecurringTransactionEntity,
    accounts: List<AccountEntity>,
    currencySymbol: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val fromWalletName = accounts.find { it.id == rule.accountId }?.name ?: "Missing Wallet"
    val walletText = if (rule.type == TransactionType.TRANSFER) {
        val toWalletName = accounts.find { it.id == rule.toAccountId }?.name ?: "Missing Wallet"
        "$fromWalletName → $toWalletName"
    } else {
        fromWalletName
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${rule.formatFrequencyLabel()} • ${if (rule.type == TransactionType.INCOME) "+" else if (rule.type == TransactionType.EXPENSE) "-" else ""}$currencySymbol${String.format(Locale.US, "%.2f", rule.amount)} • $walletText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Next due: ${RecurringScheduleManager.formatDateTime(rule.getEffectiveDueTimestamp())}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Scheduled"
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Scheduled",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditRecurringRuleDialog(
    editingRule: RecurringTransactionEntity?,
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onAddNewWallet: (name: String, initialBalance: Double, onCreated: (Int) -> Unit) -> Unit,
    onSave: (
        title: String,
        amount: Double,
        category: String,
        type: TransactionType,
        accountId: Int,
        toAccountId: Int?,
        frequency: RecurrenceFrequency,
        customIntervalCount: Int?,
        customIntervalUnit: RecurrenceCustomUnit?,
        startDateEpochDay: Long,
        dueTimestamp: Long?,
        notes: String?
    ) -> Unit
) {
    var title by remember { mutableStateOf(editingRule?.title ?: "") }
    var amount by remember { mutableStateOf(editingRule?.amount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    var selectedType by remember { mutableStateOf(editingRule?.type ?: TransactionType.EXPENSE) }
    var selectedCategory by remember { mutableStateOf(editingRule?.category ?: CategoryUtils.expenseCategories.first()) }

    val defaultFromId = editingRule?.accountId?.takeIf { id -> accounts.any { it.id == id } } ?: accounts.firstOrNull()?.id ?: MISSING_WALLET_ID
    val defaultToId = editingRule?.toAccountId?.takeIf { id -> accounts.any { it.id == id } } ?: accounts.find { it.id != defaultFromId }?.id ?: MISSING_WALLET_ID

    var selectedAccountId by remember { mutableIntStateOf(defaultFromId) }
    var selectedToAccountId by remember { mutableIntStateOf(defaultToId) }

    var selectedFrequency by remember { mutableStateOf(editingRule?.frequency ?: RecurrenceFrequency.MONTHLY) }
    var customIntervalCountText by remember { mutableStateOf(editingRule?.customIntervalCount?.toString() ?: "1") }
    var selectedCustomUnit by remember { mutableStateOf(editingRule?.customIntervalUnit ?: RecurrenceCustomUnit.DAYS) }

    var selectedTimestamp by remember {
        mutableLongStateOf(
            editingRule?.getEffectiveDueTimestamp() ?: System.currentTimeMillis()
        )
    }

    var notes by remember { mutableStateOf(editingRule?.notes ?: "") }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    var showAddWalletDialog by remember { mutableStateOf(false) }
    var walletTargetField by remember { mutableStateOf("SINGLE") } // "SINGLE", "FROM", "TO"

    val categories = if (selectedType == TransactionType.INCOME) CategoryUtils.incomeCategories else CategoryUtils.expenseCategories

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editingRule == null) "Add Scheduled" else "Edit Scheduled") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                // Top Segmented Button Row: [Expense, Income, Transfer]
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
                        onClick = {
                            selectedType = TransactionType.TRANSFER
                            if (selectedAccountId == selectedToAccountId) {
                                val alt = accounts.find { it.id != selectedAccountId }?.id
                                if (alt != null) selectedToAccountId = alt
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                    ) {
                        Text("Transfer")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(if (selectedType == TransactionType.TRANSFER) "Title (Optional)" else "Title") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

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

                Spacer(modifier = Modifier.height(12.dp))
                Text("Frequency", style = MaterialTheme.typography.titleSmall)
                OptInFlowRow {
                    RecurrenceFrequency.entries.forEach { freq ->
                        FilterChip(
                            selected = selectedFrequency == freq,
                            onClick = { selectedFrequency = freq },
                            label = {
                                Text(
                                    when (freq) {
                                        RecurrenceFrequency.DAILY -> "Daily"
                                        RecurrenceFrequency.WEEKLY -> "Weekly"
                                        RecurrenceFrequency.MONTHLY -> "Monthly"
                                        RecurrenceFrequency.CUSTOM -> "Custom"
                                    }
                                )
                            }
                        )
                    }
                }

                if (selectedFrequency == RecurrenceFrequency.CUSTOM) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Every", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)

                        OutlinedTextField(
                            value = customIntervalCountText,
                            onValueChange = { input ->
                                val filtered = input.filter { char -> char.isDigit() }
                                if (filtered.length <= 3) {
                                    customIntervalCountText = filtered
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.width(72.dp)
                        )

                        var unitDropdownExpanded by remember { mutableStateOf(false) }

                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { unitDropdownExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val countNum = customIntervalCountText.toIntOrNull() ?: 1
                                    Text(
                                        text = when (selectedCustomUnit) {
                                            RecurrenceCustomUnit.DAYS -> if (countNum == 1) "Day" else "Days"
                                            RecurrenceCustomUnit.WEEKS -> if (countNum == 1) "Week" else "Weeks"
                                            RecurrenceCustomUnit.MONTHS -> if (countNum == 1) "Month" else "Months"
                                        },
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }

                            DropdownMenu(
                                expanded = unitDropdownExpanded,
                                onDismissRequest = { unitDropdownExpanded = false }
                            ) {
                                RecurrenceCustomUnit.entries.forEach { unit ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                when (unit) {
                                                    RecurrenceCustomUnit.DAYS -> "Days"
                                                    RecurrenceCustomUnit.WEEKS -> "Weeks"
                                                    RecurrenceCustomUnit.MONTHS -> "Months"
                                                }
                                            )
                                        },
                                        onClick = {
                                            selectedCustomUnit = unit
                                            unitDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (selectedType == TransactionType.TRANSFER) {
                    Text("From Wallet", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    WalletSelector(
                        accounts = accounts,
                        selectedId = selectedAccountId,
                        onAddNewWallet = {
                            walletTargetField = "FROM"
                            showAddWalletDialog = true
                        }
                    ) { newFromId ->
                        selectedAccountId = newFromId
                        if (selectedToAccountId == newFromId) {
                            val alt = accounts.find { it.id != newFromId }?.id
                            if (alt != null) selectedToAccountId = alt
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("To Wallet", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    WalletSelector(
                        accounts = accounts,
                        selectedId = selectedToAccountId,
                        onAddNewWallet = {
                            walletTargetField = "TO"
                            showAddWalletDialog = true
                        }
                    ) { newToId ->
                        selectedToAccountId = newToId
                        if (selectedAccountId == newToId) {
                            val alt = accounts.find { it.id != newToId }?.id
                            if (alt != null) selectedAccountId = alt
                        }
                    }
                } else {
                    Text("Wallet", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    WalletSelector(
                        accounts = accounts,
                        selectedId = selectedAccountId,
                        onAddNewWallet = {
                            walletTargetField = "SINGLE"
                            showAddWalletDialog = true
                        }
                    ) { selectedAccountId = it }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Category", style = MaterialTheme.typography.titleSmall)
                    OptInFlowRow {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Scheduled Due Date & Time", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = { showDatePicker = true },
                        label = {
                            Text(
                                text = RecurringScheduleManager.formatDate(selectedTimestamp),
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = "Select Date",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )

                    AssistChip(
                        onClick = { showTimePicker = true },
                        label = {
                            Text(
                                text = RecurringScheduleManager.formatTime(selectedTimestamp),
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = "Select Time",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val amountValue = amount.toDoubleOrNull()
            val isTransfer = selectedType == TransactionType.TRANSFER
            val isTransferInvalid = isTransfer && (selectedAccountId == selectedToAccountId || selectedAccountId == MISSING_WALLET_ID || selectedToAccountId == MISSING_WALLET_ID)
            val isTitleValid = isTransfer || title.isNotBlank()
            val isAmountValid = amountValue != null && amountValue > 0.0 && amountValue.isFinite()
            val isWalletValid = if (isTransfer) !isTransferInvalid else (selectedAccountId != MISSING_WALLET_ID && accounts.any { it.id == selectedAccountId })
            val parsedCustomCount = customIntervalCountText.toIntOrNull()
            val isFrequencyValid = selectedFrequency != RecurrenceFrequency.CUSTOM || (parsedCustomCount != null && parsedCustomCount > 0)

            Button(
                onClick = {
                    if (isTitleValid && isAmountValid && isWalletValid && isFrequencyValid) {
                        val finalTitle = if (title.isBlank() && isTransfer) "Transfer" else title.trim()
                        val finalCategory = if (isTransfer) "Transfer" else selectedCategory
                        val selectedEpochDay = Instant.ofEpochMilli(selectedTimestamp).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

                        onSave(
                            finalTitle,
                            amountValue!!,
                            finalCategory,
                            selectedType,
                            selectedAccountId,
                            if (isTransfer) selectedToAccountId else null,
                            selectedFrequency,
                            if (selectedFrequency == RecurrenceFrequency.CUSTOM) (parsedCustomCount ?: 1) else null,
                            if (selectedFrequency == RecurrenceFrequency.CUSTOM) selectedCustomUnit else null,
                            selectedEpochDay,
                            selectedTimestamp,
                            notes.trim().ifBlank { null }
                        )
                    }
                },
                enabled = isTitleValid && isAmountValid && isWalletValid && isFrequencyValid
            ) {
                Text(if (isTransferInvalid) "Select different wallets" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showDatePicker) {
        val initialUtcMillis = Instant.ofEpochMilli(selectedTimestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .atStartOfDay(ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli()

        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialUtcMillis
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { utcMillis ->
                            val selectedLocalDate = Instant.ofEpochMilli(utcMillis)
                                .atZone(ZoneId.of("UTC"))
                                .toLocalDate()
                            selectedTimestamp = RecurringScheduleManager.updateDateInTimestamp(
                                selectedTimestamp,
                                selectedLocalDate
                            )
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val zdt = Instant.ofEpochMilli(selectedTimestamp).atZone(ZoneId.systemDefault())
        val timePickerState = rememberTimePickerState(
            initialHour = zdt.hour,
            initialMinute = zdt.minute,
            is24Hour = false
        )

        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedTimestamp = RecurringScheduleManager.updateTimeInTimestamp(
                            selectedTimestamp,
                            timePickerState.hour,
                            timePickerState.minute
                        )
                        showTimePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(state = timePickerState)
                }
            }
        )
    }

    if (showAddWalletDialog) {
        AddWalletDialog(
            onDismiss = { showAddWalletDialog = false },
            onSave = { name, initialBalance ->
                onAddNewWallet(name, initialBalance) { newAccountId ->
                    when (walletTargetField) {
                        "FROM" -> {
                            selectedAccountId = newAccountId
                            if (selectedToAccountId == newAccountId) {
                                val alt = accounts.find { it.id != newAccountId }?.id
                                if (alt != null) selectedToAccountId = alt
                            }
                        }
                        "TO" -> {
                            selectedToAccountId = newAccountId
                            if (selectedAccountId == newAccountId) {
                                val alt = accounts.find { it.id != newAccountId }?.id
                                if (alt != null) selectedToAccountId = alt
                            }
                        }
                        else -> selectedAccountId = newAccountId
                    }
                }
                showAddWalletDialog = false
            }
        )
    }
}
