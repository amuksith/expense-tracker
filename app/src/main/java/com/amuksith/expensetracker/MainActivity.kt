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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.amuksith.expensetracker.ui.AboutRoute
import com.amuksith.expensetracker.ui.AboutScreen
import kotlinx.serialization.Serializable

@Serializable
object DashboardRoute

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
        
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val crashFile = File(filesDir, "last_crash.txt")
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                crashFile.writeText("Crash at $time\n" + throwable.stackTraceToString())
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        enableEdgeToEdge()
        setContent {
            ExpenseTrackerTheme {
                val backStack = remember { mutableStateListOf<Any>(DashboardRoute) }

                NavDisplay(
                    backStack = backStack,
                    onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
                    entryProvider = { key ->
                        when (key) {
                            is DashboardRoute -> NavEntry(key) {
                                DashboardScreen(
                                    viewModel = viewModel,
                                    onNavigateToAbout = { backStack.add(AboutRoute) }
                                )
                            }
                            is AboutRoute -> NavEntry(key) {
                                AboutScreen(
                                    onNavigateBack = { if (backStack.size > 1) backStack.removeLastOrNull() }
                                )
                            }
                            else -> NavEntry(Unit) { androidx.compose.material3.Text("Unknown route") }
                        }
                    }
                )
            }
        }
    }
}
