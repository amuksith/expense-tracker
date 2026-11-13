package com.amuksith.expensetracker.utils

import android.content.Context

class TextClassificationHelper(private val context: Context) {

    /**
     * Predicts the Category from the merchant title.
     * Offline receipt parser has been removed for Phase 1.
     */
    fun classifyMerchant(merchantTitle: String): String? {
        return null
    }

    fun close() {
    }
}
