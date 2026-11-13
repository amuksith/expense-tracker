package com.amuksith.expensetracker.utils

import android.content.Context
import android.net.Uri
import com.amuksith.expensetracker.data.AccountEntity
import com.amuksith.expensetracker.data.ExpenseEntity
import com.amuksith.expensetracker.data.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupExportManager {

    suspend fun exportTransactionsToCsv(
        context: Context,
        uri: Uri,
        expenses: List<ExpenseEntity>,
        accounts: List<AccountEntity>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write("ID,Date,Title,Type,Category,Amount,From Wallet,To Wallet,Invoice Number,Notes\n")
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

                    for (expense in expenses) {
                        val dateStr = dateFormat.format(Date(expense.date))
                        val fromWallet = accounts.find { it.id == expense.accountId }?.name ?: "Unknown"
                        val toWallet = expense.toAccountId?.let { toId -> accounts.find { it.id == toId }?.name ?: "Unknown" } ?: ""

                        val row = listOf(
                            expense.id.toString(),
                            dateStr,
                            escapeCsv(expense.title),
                            expense.type.name,
                            escapeCsv(expense.category),
                            expense.amount.toString(),
                            escapeCsv(fromWallet),
                            escapeCsv(toWallet),
                            escapeCsv(expense.invoiceNumber ?: ""),
                            escapeCsv(expense.notes ?: "")
                        )
                        writer.write(row.joinToString(",") + "\n")
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun escapeCsv(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\""
        }
        return value
    }

    suspend fun exportFullBackupToJson(
        context: Context,
        uri: Uri,
        currencyCode: String,
        accounts: List<AccountEntity>,
        expenses: List<ExpenseEntity>,
        budgets: Map<String, Double> = emptyMap()
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject()
            root.put("backupVersion", 1)
            root.put("exportedAt", System.currentTimeMillis())
            root.put("currencyCode", currencyCode)

            val accountsArray = JSONArray()
            for (acc in accounts) {
                val accObj = JSONObject()
                accObj.put("id", acc.id)
                accObj.put("name", acc.name)
                accObj.put("initialBalance", acc.initialBalance)
                accObj.put("colorHex", acc.colorHex)
                accountsArray.put(accObj)
            }
            root.put("accounts", accountsArray)

            val expensesArray = JSONArray()
            for (exp in expenses) {
                val expObj = JSONObject()
                expObj.put("id", exp.id)
                expObj.put("title", exp.title)
                expObj.put("amount", exp.amount)
                expObj.put("category", exp.category)
                expObj.put("date", exp.date)
                expObj.put("type", exp.type.name)
                if (exp.invoiceNumber != null) expObj.put("invoiceNumber", exp.invoiceNumber)
                if (exp.notes != null) expObj.put("notes", exp.notes)
                expObj.put("accountId", exp.accountId)
                if (exp.toAccountId != null) expObj.put("toAccountId", exp.toAccountId)
                expensesArray.put(expObj)
            }
            root.put("expenses", expensesArray)

            val budgetsObj = JSONObject()
            for ((cat, limit) in budgets) {
                budgetsObj.put(cat, limit)
            }
            root.put("budgets", budgetsObj)

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(root.toString(2))
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    data class RestoreResult(val success: Boolean, val message: String, val accounts: List<AccountEntity>? = null, val expenses: List<ExpenseEntity>? = null, val currencyCode: String? = null, val budgets: Map<String, Double>? = null)

    suspend fun restoreFullBackupFromJson(context: Context, uri: Uri): RestoreResult = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            } ?: return@withContext RestoreResult(false, "Could not open file.")

            val root = JSONObject(jsonString)
            if (!root.has("accounts") || !root.has("expenses")) {
                return@withContext RestoreResult(false, "Invalid backup format: Missing accounts or expenses.")
            }

            val currencyCode = if (root.has("currencyCode")) root.getString("currencyCode") else null

            val accountsArray = root.getJSONArray("accounts")
            val parsedAccounts = mutableListOf<AccountEntity>()
            for (i in 0 until accountsArray.length()) {
                val accObj = accountsArray.getJSONObject(i)
                parsedAccounts.add(
                    AccountEntity(
                        id = accObj.getInt("id"),
                        name = accObj.getString("name"),
                        initialBalance = accObj.getDouble("initialBalance"),
                        colorHex = if (accObj.has("colorHex")) accObj.getLong("colorHex") else 0xFF1E88E5
                    )
                )
            }

            if (parsedAccounts.isEmpty()) {
                parsedAccounts.add(AccountEntity(id = 1, name = "Default Wallet", initialBalance = 0.0))
            }

            val validAccountIds = parsedAccounts.map { it.id }.toSet()

            val expensesArray = root.getJSONArray("expenses")
            var parsedExpenses = mutableListOf<ExpenseEntity>()
            for (i in 0 until expensesArray.length()) {
                val expObj = expensesArray.getJSONObject(i)

                val typeStr = expObj.getString("type")
                val type = try { TransactionType.valueOf(typeStr) } catch(_: Exception) { TransactionType.EXPENSE }

                val accountId = expObj.getInt("accountId")
                if (!validAccountIds.contains(accountId)) {
                    return@withContext RestoreResult(false, "Corrupted backup: Expense references non-existent account.")
                }

                var toAccountId: Int? = null
                if (expObj.has("toAccountId") && !expObj.isNull("toAccountId")) {
                    toAccountId = expObj.getInt("toAccountId")
                    if (!validAccountIds.contains(toAccountId) || accountId == toAccountId) {
                        return@withContext RestoreResult(false, "Corrupted backup: Transfer references invalid destination account.")
                    }
                }

                parsedExpenses.add(
                    ExpenseEntity(
                        id = expObj.getInt("id"),
                        title = expObj.getString("title"),
                        amount = expObj.getDouble("amount"),
                        category = expObj.getString("category"),
                        date = expObj.getLong("date"),
                        type = type,
                        invoiceNumber = if (expObj.has("invoiceNumber")) expObj.getString("invoiceNumber") else null,
                        notes = if (expObj.has("notes")) expObj.getString("notes") else null,
                        accountId = accountId,
                        toAccountId = toAccountId
                    )
                )
            }

            parsedExpenses = parsedExpenses.distinctBy { it.id }.toMutableList()

            val parsedBudgets = mutableMapOf<String, Double>()
            if (root.has("budgets")) {
                val budgetsObj = root.getJSONObject("budgets")
                for (key in budgetsObj.keys()) {
                    val limit = budgetsObj.getDouble(key)
                    if (limit > 0.0) {
                        parsedBudgets[key] = limit
                    }
                }
            }

            RestoreResult(true, "Restored ${parsedAccounts.size} wallets and ${parsedExpenses.size} transactions successfully.", parsedAccounts, parsedExpenses, currencyCode, parsedBudgets)
        } catch (e: Exception) {
            e.printStackTrace()
            RestoreResult(false, "Failed to restore backup: ${e.message}")
        }
    }
}