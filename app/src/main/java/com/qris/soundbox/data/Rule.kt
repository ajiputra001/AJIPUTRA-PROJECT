package com.qris.soundbox.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rules")
data class Rule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appName: String,
    val packageName: String,
    val regexAmount: String,
    val regexPayer: String,
    val speakTemplate: String,
    val isActive: Boolean = true
)
