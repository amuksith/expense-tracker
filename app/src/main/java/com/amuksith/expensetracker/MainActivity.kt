package com.amuksith.expensetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.amuksith.expensetracker.data.AppDatabase
import com.amuksith.expensetracker.data.ExpenseRepository
import com.amuksith.expensetracker.ui.DashboardScreen
import com.amuksith.expensetracker.ui.ExpenseViewModel
import com.amuksith.expensetracker.ui.ExpenseViewModelFactory
import com.amuksith.expensetracker.ui.theme.ExpenseTrackerTheme

class MainActivity : ComponentActivity() {

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { ExpenseRepository(database.expenseDao(), database.accountDao()) }
    private val currencyManager by lazy { com.amuksith.expensetracker.data.preferences.CurrencyManager(this) }
    private val budgetManager by lazy { com.amuksith.expensetracker.data.preferences.BudgetManager(this) }
    private val viewModel: ExpenseViewModel by viewModels {
        ExpenseViewModelFactory(repository, currencyManager, budgetManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ExpenseTrackerTheme {
                DashboardScreen(viewModel = viewModel)
            }
        }
    }
}
