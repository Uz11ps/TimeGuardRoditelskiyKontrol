package com.example.qwerty123.data

import android.graphics.Bitmap

data class AppItem(
    val name: String,
    val packageName: String,
    val icon: Bitmap? = null
)

data class AppLimit(
    val packageName: String = "",
    val limitMinutes: Int = 0,
    val usedTodayMinutes: Int = 0,
    val lastResetDate: String = "" // "yyyy-MM-dd"
)
