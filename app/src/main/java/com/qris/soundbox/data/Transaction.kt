package com.qris.soundbox.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val appName: String,
    val packageName: String,
    val amount: Double,
    val payerName: String,
    val rawText: String,
    val isParsedSuccessfully: Boolean
)
