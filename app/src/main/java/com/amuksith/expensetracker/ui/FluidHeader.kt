package com.amuksith.expensetracker.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert

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

    var collapseOffsetPx by remember { mutableFloatStateOf(0f) }

    val collapseFraction = (collapseOffsetPx / maxCollapsePx).coerceIn(0f, 1f)

    val nestedScrollConnection = remember(maxCollapsePx, listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val delta = available.y

                // Collapse header before LazyColumn scrolls (scrolling up)
                if (delta < 0f && collapseOffsetPx < maxCollapsePx) {
                    val newOffset = (collapseOffsetPx - delta).coerceIn(0f, maxCollapsePx)
                    val consumed = collapseOffsetPx - newOffset
                    collapseOffsetPx = newOffset
                    return Offset(0f, consumed)
                }

                // Expand header only when LazyColumn is at the top (scrolling down)
                if (delta > 0f &&
                    listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0 &&
                    collapseOffsetPx > 0f) {
                    val newOffset = (collapseOffsetPx - delta).coerceIn(0f, maxCollapsePx)
                    val consumed = collapseOffsetPx - newOffset
                    collapseOffsetPx = newOffset
                    return Offset(0f, consumed)
                }

                return Offset.Zero
            }
        }
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
    onBackupClick: () -> Unit = {}
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
            modifier = Modifier.graphicsLayer {
                val scale = 1f - (collapseFraction * 0.18f)
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
        )

        val pillAlpha = (1f - collapseFraction * 2.2f).coerceIn(0f, 1f)

        if (pillAlpha > 0.01f) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.graphicsLayer { alpha = pillAlpha }
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .clickable(enabled = collapseFraction < 0.3f) { onCurrencyClick() }
                ) {
                    Text(
                        text = "[ $currentCurrencySymbol ▼ ]",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onBackupClick,
                    enabled = collapseFraction < 0.3f,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Backup & Export options"
                    )
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
