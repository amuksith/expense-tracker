# 💰 Expense Tracker v1.4.3

A modern, **100% offline-first** personal finance and expense tracking Android application built with **Kotlin** and **Jetpack Compose (Material 3)**. Designed with a fluid, ColorOS-style morphing header, multi-wallet double-entry accounting, and native Compose `Canvas` visual analytics—with zero network permissions, zero telemetry, and no third-party bloat.

---

## ✨ Key Features

- **100% Offline & Privacy-First:** Zero network permissions, zero telemetry, and no account sign-ups. All financial records stay strictly local on your device via Room (SQLite) Database.
- **Multi-Wallet Double-Entry Ledger:** Create and manage multiple accounts (e.g., *Cash*, *Bank Account*, *Business Wallet*, *Credit Card*) with independent real-time balances.
- **Internal Wallet Transfers:** Move funds between internal wallets seamlessly (e.g., `Bank Account → Cash`). Transfers automatically update both wallet balances while staying strictly excluded from Income, Expense, and Analytics calculations so cash flow metrics remain accurate.
- **Safe Wallet Deletion (3-Modes):** Prevent accidental balance or history loss when removing an account:
  - **Mode 1:** Move transactions and remaining balance to another wallet.
  - **Mode 2:** Move remaining balance only (historical transfers baked in).
  - **Mode 3:** Delete everything (wallet + all linked transactions).
- **Monthly Category Budgets:** Set optional monthly spending limits per category and track progress visually with threshold indicators.
- **Fluid & Adaptive UI (ColorOS-Style):** Built completely with Jetpack Compose, featuring a GPU-accelerated (`graphicsLayer` & `NestedScrollConnection`) collapsing top bar that shrinks the Net Balance card into a compact horizontal bar as you scroll.
- **Interactive Visual Analytics:** Custom-drawn Jetpack Compose `Canvas` Donut Charts, Income vs. Expense cash flow bars, Savings Margin KPIs, and ranked category breakdowns across *This Month*, *Last 3 Months*, and *All Time*.
- **Dynamic Multi-Currency Support:** Switch live between `Rs.`, `$`, `€`, `£`, and `₹` across the entire app without reloading your data.
- **Fast Transaction Management:** Swipe-to-delete gestures, one-tap editing, and instant search & category/date filtering.
- **Local JSON & CSV Backups:** Export transactions to a spreadsheet-ready `.csv` file, or perform full database backups and restores securely using Android's Storage Access Framework (SAF) with zero storage permissions.

---

## 🛠️ Tech Stack & Architecture

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose, Material 3 (`graphicsLayer`, `NestedScrollConnection`)
- **Architecture:** MVVM (`ExpenseViewModel`, `ExpenseRepository`, `StateFlow`)
- **Asynchronous Processing:** Kotlin Coroutines & Flow
- **Local Persistence:** Room Database (SQLite) & `SharedPreferences`
- **Graphics:** Native Jetpack Compose `Canvas` (zero external charting libraries)
- **Serialization:** Native `JSONObject` / `JSONArray` (for offline SAF backups)

---

## 🚀 Getting Started

You can install the pre-built APK directly on your Android device.

### 📦 Install the APK

1. Go to the [**Releases**](../../releases) page of this repository.
2. Download the latest `v1.4.3` `.apk` file from the **Assets** section.
3. Open the downloaded file on your Android phone and tap **Install** (if prompted, allow installation from unknown sources).
4. Launch **Expense Tracker**—no account sign-up, internet connection, or device permissions are required.


## 💡 Quick Start Guide

1. **Select Your Currency:** Tap the currency pill (`[ Rs. ▼ ]`) in the top-right corner of the dashboard to switch between `Rs.`, `$`, `€`, `£`, or `₹`.

2. **Create Your Wallets:** Scroll the horizontal wallet carousel and tap **`+ Add Wallet`** to set up your accounts with their initial balances.

3. **Log Transactions & Transfers:** Tap the **`+`** button at the bottom right to record an **Expense**, **Income**, or internal **Transfer** between wallets. Swipe left on any row to delete it, or tap to edit.

4. **Inspect Analytics & Budgets:** Switch to the **Analytics** tab to monitor Net Cash Flow, Savings Margin, category spending shares, and monthly budget thresholds.

5. **Back Up Your Data:** Use the built-in backup/export tools to save a local `.json` snapshot or export your history to `.csv` anytime.