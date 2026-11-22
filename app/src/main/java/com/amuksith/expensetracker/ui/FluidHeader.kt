package com.amuksith.expensetracker.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.draw.clip

@Stable
class FluidHeaderState(
    val collapseFraction: Float,
    val nestedScrollConnection: NestedScrollConnection
)

@Composable
fun rememberFluidHeaderState(
    listState: LazyListState,
    collapseDistance: Dp = 130.dp
): FluidHeaderState {
    val density = LocalDensity.current
    val maxCollapsePx = remember(density, collapseDistance) {
        with(density) { collapseDistance.toPx() }
    }

    val rawCollapseFraction by remember(listState, maxCollapsePx) {
        derivedStateOf {
            if (maxCollapsePx <= 0f) {
                0f
            } else if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset / maxCollapsePx).coerceIn(0f, 1f)
            }
        }
    }

    val collapseFraction by animateFloatAsState(
        targetValue = rawCollapseFraction,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "collapseFraction"
    )

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {}
    }

    return remember(collapseFraction, nestedScrollConnection) {
        FluidHeaderState(collapseFraction, nestedScrollConnection)
    }
}

@Composable
fun FluidHeaderTitleRow(
    currentCurrencySymbol: String,
    collapseFraction: Float,
    onCurrencyClick: () -> Unit,
    onBackupClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    onRecurringRulesClick: () -> Unit = {}
) {
    val headerHeight = lerp(56.dp, 38.dp, collapseFraction)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(headerHeight)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Expense Tracker",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .graphicsLayer {
                    val scale = 1f - (collapseFraction * 0.18f)
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
        )

        Spacer(modifier = Modifier.width(8.dp))

        val pillAlpha = (1f - collapseFraction * 2.2f).coerceIn(0f, 1f)

        if (pillAlpha > 0.01f) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.graphicsLayer { alpha = pillAlpha }
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(enabled = collapseFraction < 0.3f) { onCurrencyClick() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = currentCurrencySymbol.trim(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Select Currency",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                var expanded by remember { mutableStateOf(false) }

                Box(contentAlignment = Alignment.Center) {
                    IconButton(
                        onClick = { expanded = true },
                        enabled = collapseFraction < 0.3f
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options"
                        )
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Scheduled") },
                            leadingIcon = { Icon(Icons.Default.Repeat, contentDescription = null) },
                            onClick = {
                                expanded = false
                                onRecurringRulesClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Backup & Restore") },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            onClick = {
                                expanded = false
                                onBackupClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("About") },
                            leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                            onClick = {
                                expanded = false
                                onAboutClick()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FinancialOverviewCard(
    netBalance: Double,
    totalIncome: Double,
    totalExpense: Double,
    currencySymbol: String,
    collapseFraction: Float
) {
    val cardHeight = lerp(174.dp, 56.dp, collapseFraction)
    val horizontalBias = collapseFraction
    val detailsAlpha = (1f - collapseFraction * 2.2f).coerceIn(0f, 1f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 16.dp,
                vertical = lerp(8.dp, 4.dp, collapseFraction)
            )
            .height(cardHeight),
        shape = RoundedCornerShape(lerp(16.dp, 12.dp, collapseFraction)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 20.dp,
                    vertical = lerp(16.dp, 0.dp, collapseFraction)
                )
        ) {
            // Net Balance label
            Text(
                text = "Net Balance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.align(
                    BiasAlignment(
                        horizontalBias = -horizontalBias,
                        verticalBias = -1f + collapseFraction
                    )
                )
            )

            // Net Balance amount
            val amountScale = 1f - (collapseFraction * 0.32f)
            Text(
                text = formatCurrency(netBalance, currencySymbol),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .align(
                        BiasAlignment(
                            horizontalBias = horizontalBias,
                            verticalBias = -0.15f * (1f - collapseFraction)
                        )
                    )
                    .graphicsLayer {
                        scaleX = amountScale
                        scaleY = amountScale
                        transformOrigin = TransformOrigin(
                            pivotFractionX = 0.5f + (0.5f * collapseFraction),
                            pivotFractionY = 0.5f
                        )
                    }
            )

            // Income and Expense details
            if (detailsAlpha > 0.01f) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .graphicsLayer {
                            alpha = detailsAlpha
                            translationY = collapseFraction * 20f
                        },
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Total Income", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = formatCurrency(totalIncome, currencySymbol, "+"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Total Expense", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = formatCurrency(totalExpense, currencySymbol, "-"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
