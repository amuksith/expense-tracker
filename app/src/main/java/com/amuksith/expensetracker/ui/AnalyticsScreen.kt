package com.amuksith.expensetracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
fun LazyListScope.analyticsItems(
    selectedPeriod: AnalyticsPeriod,
    onPeriodChanged: (AnalyticsPeriod) -> Unit,
    periodIncome: Double,
    periodExpense: Double,
    currencySymbol: String,
    expenseCategoryStats: List<CategoryStat>,
    incomeCategoryStats: List<CategoryStat>,
    selectedMode: Int,
    onModeChanged: (Int) -> Unit,
    categoryBudgets: Map<String, Double> = emptyMap(),
    budgetProgressList: List<CategoryBudgetProgress> = emptyList(),
    onManageBudgetsClick: () -> Unit = {}
) {
    item(key = "analytics_period_filter", contentType = "analytics_header") {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            FilterChip(
                selected = selectedPeriod == AnalyticsPeriod.THIS_MONTH,
                onClick = { onPeriodChanged(AnalyticsPeriod.THIS_MONTH) },
                label = { Text("This Month") }
            )
            FilterChip(
                selected = selectedPeriod == AnalyticsPeriod.LAST_3_MONTHS,
                onClick = { onPeriodChanged(AnalyticsPeriod.LAST_3_MONTHS) },
                label = { Text("Last 3 Months") }
            )
            FilterChip(
                selected = selectedPeriod == AnalyticsPeriod.ALL_TIME,
                onClick = { onPeriodChanged(AnalyticsPeriod.ALL_TIME) },
                label = { Text("All Time") }
            )
        }
    }

    item(key = "analytics_metrics_card", contentType = "analytics_card") {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxWidth()
            ) {
                val netCashFlow = periodIncome - periodExpense
                val profitMargin = if (periodIncome > 0) ((netCashFlow) / periodIncome * 100) else null

                Text(
                    text = "Key Metrics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Net Cash Flow", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = formatCurrency(netCashFlow, currencySymbol),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (netCashFlow >= 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Savings Margin", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = if (profitMargin != null) String.format("%.1f%%", profitMargin) else "N/A",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val total = periodIncome + periodExpense
                if (total > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(if (periodIncome > 0) periodIncome.toFloat() else 0.01f)
                                .fillMaxHeight()
                                .background(Color(0xFF4CAF50))
                        )
                        Box(
                            modifier = Modifier
                                .weight(if (periodExpense > 0) periodExpense.toFloat() else 0.01f)
                                .fillMaxHeight()
                                .background(Color(0xFFFF5252))
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Income: ${formatCurrency(periodIncome, currencySymbol)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF4CAF50)
                        )
                        Text(
                            text = "Expense: ${formatCurrency(periodExpense, currencySymbol)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFF5252)
                        )
                    }
                }
            }
        }
    }

    item(key = "analytics_budgets_card", contentType = "analytics_card") {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Monthly Budgets (This Month)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    TextButton(
                        onClick = onManageBudgetsClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (budgetProgressList.isEmpty()) "Set Budgets" else "Edit",
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                if (budgetProgressList.isEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No category budgets set yet. Set monthly spending limits to track your targets.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val totalBudgeted = budgetProgressList.sumOf { it.budgetLimit }
                    val totalSpentInBudgeted = budgetProgressList.sumOf { it.spentThisMonth }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total Budgeted:", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = "${formatCurrency(totalSpentInBudgeted, currencySymbol)} / ${formatCurrency(totalBudgeted, currencySymbol)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    budgetProgressList.forEachIndexed { index, progress ->
                        val isExceeded = progress.status == BudgetStatus.EXCEEDED
                        val isWarning = progress.status == BudgetStatus.WARNING

                        val barColor = when {
                            isExceeded -> MaterialTheme.colorScheme.error
                            isWarning -> Color(0xFFFF9800) // Amber
                            else -> CategoryUtils.getCategoryColor(progress.category)
                        }

                        val badgeColor = when {
                            isExceeded -> MaterialTheme.colorScheme.error
                            isWarning -> Color(0xFFFF9800)
                            else -> Color.Transparent
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = CategoryUtils.getCategoryIcon(progress.category),
                                contentDescription = null,
                                tint = CategoryUtils.getCategoryColor(progress.category),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = progress.category,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${formatCurrency(progress.spentThisMonth, currencySymbol)} / ${formatCurrency(progress.budgetLimit, currencySymbol)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        softWrap = false,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { (progress.usagePercentage / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = barColor,
                                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val subText = buildString {
                                        if (isExceeded) {
                                            append("${formatCurrency(kotlin.math.abs(progress.remainingAmount), currencySymbol)} over limit")
                                            append(" • Over Budget")
                                        } else if (isWarning) {
                                            append("${formatCurrency(progress.remainingAmount, currencySymbol)} left")
                                            append(" • Near Limit")
                                        } else {
                                            append("${formatCurrency(progress.remainingAmount, currencySymbol)} left")
                                        }
                                    }
                                    
                                    val subTextColor = when {
                                        isExceeded -> MaterialTheme.colorScheme.error
                                        isWarning -> Color(0xFFFF9800)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    val subTextWeight = if (isWarning || isExceeded) FontWeight.Medium else FontWeight.Normal

                                    Text(
                                        text = subText,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = subTextWeight,
                                        color = subTextColor
                                    )
                                    Text(
                                        text = String.format("%.1f%%", progress.usagePercentage),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = subTextColor
                                    )
                                }
                            }
                        }
                        if (index < budgetProgressList.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 40.dp, top = 4.dp, bottom = 4.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                            )
                        }
                    }
                }
            }
        }
    }

    item(key = "analytics_donut_card", contentType = "analytics_card") {
        val currentStats = if (selectedMode == 0) expenseCategoryStats else incomeCategoryStats
        val currentTotal = if (selectedMode == 0) periodExpense else periodIncome

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = if (currentStats.isEmpty()) RoundedCornerShape(12.dp) else RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedMode == 0,
                        onClick = { onModeChanged(0) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("Expenses")
                    }
                    SegmentedButton(
                        selected = selectedMode == 1,
                        onClick = { onModeChanged(1) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("Income")
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                if (currentStats.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No data for this period",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(200.dp)
                    ) {
                        DonutChart(stats = currentStats)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Total",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatCurrency(currentTotal, currencySymbol),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    val currentStats = if (selectedMode == 0) expenseCategoryStats else incomeCategoryStats
    if (currentStats.isNotEmpty()) {
        itemsIndexed(
            items = currentStats,
            key = { _, stat -> "stat_${stat.category}" },
            contentType = { _, _ -> "analytics_stat_row" }
        ) { index, stat ->
            val isLast = index == currentStats.lastIndex
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = if (isLast) RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 12.dp, bottomEnd = 12.dp) else RoundedCornerShape(0.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    CategoryStatRow(
                        stat = stat,
                        currencySymbol = currencySymbol,
                        budgetProgress = if (selectedMode == 0 && selectedPeriod == AnalyticsPeriod.THIS_MONTH) {
                            budgetProgressList.find { it.category == stat.category }
                        } else null
                    )
                    if (!isLast) {
                        Spacer(modifier = Modifier.height(12.dp))
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }

    item(key = "analytics_footer_spacer", contentType = "analytics_spacer") {
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
fun DonutChart(stats: List<CategoryStat>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        var startAngle = -90f
        val strokeWidth = 32.dp.toPx()
        stats.forEach { stat ->
            val sweepAngle = (stat.percentage / 100f) * 360f
            drawArc(
                color = CategoryUtils.getCategoryColor(stat.category),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            startAngle += sweepAngle
        }
    }
}

@Composable
fun CategoryStatRow(stat: CategoryStat, currencySymbol: String, budgetProgress: CategoryBudgetProgress? = null) {
    val categoryColor = CategoryUtils.getCategoryColor(stat.category)
    val categoryIcon = CategoryUtils.getCategoryIcon(stat.category)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(categoryColor.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = categoryIcon,
                contentDescription = stat.category,
                tint = categoryColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stat.category,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (budgetProgress != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        val badgeColor = when (budgetProgress.status) {
                            BudgetStatus.EXCEEDED -> MaterialTheme.colorScheme.error
                            BudgetStatus.WARNING -> Color(0xFFFF9800)
                            else -> CategoryUtils.getCategoryColor(stat.category)
                        }
                        Surface(
                            color = badgeColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "${String.format("%.0f", budgetProgress.usagePercentage)}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = formatCurrency(stat.amount, currencySymbol),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${stat.count} transaction${if (stat.count > 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = String.format("%.1f%%", stat.percentage),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { stat.percentage / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = categoryColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                gapSize = 0.dp,
                drawStopIndicator = {}
            )
        }
    }
}
