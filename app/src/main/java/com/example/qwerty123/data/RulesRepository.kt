package com.example.qwerty123.data

import android.content.Context
import android.location.Location

object RulesRepository {
    private const val PREFS_NAME = "timeguard_rules"
    private const val KEY_BLOCKED_APPS = "blocked_apps"
    private const val KEY_BLOCKED_URLS = "blocked_urls"
    private const val KEY_TIME_LIMITS = "time_limits"
    
    // Храним геозоны в памяти для быстрого доступа из сервиса
    private var activeGeofences: List<GeofenceModel> = emptyList()

    fun isAppBlockedWithLocation(context: Context, packageName: String, currentLat: Double, currentLon: Double): Boolean {
        if (packageName == context.packageName) return false
        
        // 1. Проверка лимита времени
        if (isTimeLimitExceeded(context, packageName)) return true

        // 2. Глобальный блок
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if ((prefs.getStringSet(KEY_BLOCKED_APPS, emptySet()) ?: emptySet()).contains(packageName)) return true
        
        // 3. Блок по зонам
        for (fence in activeGeofences) {
            val results = FloatArray(1)
            Location.distanceBetween(currentLat, currentLon, fence.lat, fence.lon, results)
            if (results[0] <= fence.radius) {
                if (fence.blockedApps.contains(packageName)) return true
            }
        }
        return false
    }

    private fun isTimeLimitExceeded(context: Context, packageName: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val limit = prefs.getInt("limit_$packageName", 0)
        if (limit <= 0) return false
        
        val used = getUsedTimeToday(context, packageName)
        return used >= limit
    }

    fun getUsedTimeToday(context: Context, packageName: String): Int {
        val prefs = context.getSharedPreferences("usage_stats", Context.MODE_PRIVATE)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val lastDate = prefs.getString("last_date_$packageName", "")
        if (lastDate != today) return 0
        return prefs.getInt("used_$packageName", 0)
    }

    fun addUsageMinute(context: Context, packageName: String) {
        val prefs = context.getSharedPreferences("usage_stats", Context.MODE_PRIVATE)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val lastDate = prefs.getString("last_date_$packageName", "")
        
        var used = if (lastDate == today) prefs.getInt("used_$packageName", 0) else 0
        used += 1
        
        prefs.edit()
            .putString("last_date_$packageName", today)
            .putInt("used_$packageName", used)
            .apply()
    }

    fun isUrlBlocked(context: Context, url: String): Boolean {
        if (url.isBlank()) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val blockedUrls = prefs.getStringSet(KEY_BLOCKED_URLS, emptySet()) ?: emptySet()
        
        // Нормализуем текущий URL из браузера
        val cleanUrl = url.lowercase()
            .replace("https://", "")
            .replace("http://", "")
            .replace("www.", "")
            .trim('/')

        return blockedUrls.any { blockedRule ->
            // Нормализуем правило из черного списка
            val cleanRule = blockedRule.lowercase()
                .replace("https://", "")
                .replace("http://", "")
                .replace("www.", "")
                .trim('/')
            
            // Проверяем, содержит ли текущий URL правило (или наоборот)
            cleanUrl.contains(cleanRule) || cleanRule.contains(cleanUrl)
        }
    }

    fun updateRules(context: Context, apps: Set<String>, urls: Set<String>) {
        android.util.Log.d("RulesRepository", "Updating rules: Apps=${apps.size}, URLs=${urls.size}")
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY_BLOCKED_APPS, apps)
            .putStringSet(KEY_BLOCKED_URLS, urls)
            .apply()
    }

    fun updateTimeLimits(context: Context, limits: Map<String, Int>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        // Очистим старые лимиты (или можно просто перезаписывать)
        limits.forEach { (pkg, min) ->
            prefs.putInt("limit_$pkg", min)
        }
        prefs.apply()
    }

    fun updateGeofences(context: Context, fences: List<GeofenceModel>) {
        activeGeofences = fences
        // Можно также сохранить в JSON в префы для выживания после перезагрузки
    }
    
    fun getActiveGeofences() = activeGeofences
}
