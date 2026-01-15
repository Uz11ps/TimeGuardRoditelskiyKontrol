package com.example.qwerty123.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.qwerty123.BlockActivity
import com.example.qwerty123.data.FirebaseRepository
import com.example.qwerty123.data.RulesRepository

class TimeGuardService : AccessibilityService() {

    private var currentApp: String = ""
    private var lastTickTime: Long = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("TimeGuardService", "Service connected")
        FirebaseRepository.listenForRules(this)
        
        // Запускаем поток для отслеживания времени (раз в минуту)
        startUsageTimer()
    }

    private fun startUsageTimer() {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.post(object : Runnable {
            override fun run() {
                if (currentApp.isNotBlank() && currentApp != packageName) {
                    RulesRepository.addUsageMinute(this@TimeGuardService, currentApp)
                    
                    // Проверяем блок сразу после добавления минуты
                    val prefs = getSharedPreferences("last_location", Context.MODE_PRIVATE)
                    val lat = prefs.getFloat("lat", 0f).toDouble()
                    val lon = prefs.getFloat("lon", 0f).toDouble()
                    
                    if (RulesRepository.isAppBlockedWithLocation(this@TimeGuardService, currentApp, lat, lon)) {
                        showBlockScreen()
                    }
                }
                handler.postDelayed(this, 60000) // 1 минута
            }
        })
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: ""
        
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (packageName.isNotBlank()) {
                currentApp = packageName
            }
        }

        // Получаем последнюю локацию для проверки геозон
        val prefs = getSharedPreferences("last_location", Context.MODE_PRIVATE)
        val lat = prefs.getFloat("lat", 0f).toDouble()
        val lon = prefs.getFloat("lon", 0f).toDouble()

        // 1. Блокировка приложений
        if (packageName.isNotBlank()) {
            val isBlocked = RulesRepository.isAppBlockedWithLocation(this, packageName, lat, lon)
            if (isBlocked) {
                Log.w("TimeGuardService", "DETECTED BLOCKED APP: $packageName. Showing block screen.")
                showBlockScreen()
                return
            }
        }

        // 2. Блокировка URL в Chrome и других браузерах
        // Мы проверяем событие только если это Chrome или если в окне изменился контент
        if (packageName == "com.android.chrome" || event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val rootNode = rootInActiveWindow ?: return
            
            // Сначала пробуем найти по ID (самый быстрый способ)
            val urlBarNodes = rootNode.findAccessibilityNodeInfosByViewId("com.android.chrome:id/url_bar")
            if (urlBarNodes.isNotEmpty()) {
                val urlText = urlBarNodes[0].text?.toString() ?: ""
                if (urlText.isNotBlank()) {
                    if (RulesRepository.isUrlBlocked(this, urlText)) {
                        Log.w("TimeGuardService", "DETECTED BLOCKED URL (by ID): $urlText")
                        showBlockScreen()
                    }
                }
            } else {
                // Если по ID не нашли, сканируем дерево узлов (более медленный, но надежный способ)
                checkForBlockedUrls(rootNode)
            }
        // 3. Защита от удаления
        if (packageName == "com.android.settings" || packageName == "com.google.android.packageinstaller" || packageName == "com.android.packageinstaller") {
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                if (findTextRecursive(rootNode, "TimeGuard") && 
                    (findTextRecursive(rootNode, "Uninstall") || 
                     findTextRecursive(rootNode, "Удалить") || 
                     findTextRecursive(rootNode, "Стереть"))) {
                    Log.w("TimeGuardService", "DETECTED UNINSTALL ATTEMPT FOR TIMEGUARD!")
                    showBlockScreen(isUninstallAttempt = true)
                }
            }
        }
    }

    private fun findTextRecursive(node: AccessibilityNodeInfo?, textToFind: String): Boolean {
        if (node == null) return false
        
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        
        if (text.contains(textToFind, ignoreCase = true) || 
            contentDesc.contains(textToFind, ignoreCase = true)) {
            return true
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (findTextRecursive(child, textToFind)) {
                return true
            }
        }
        return false
    }

    private fun checkForBlockedUrls(node: AccessibilityNodeInfo) {
        val text = node.text?.toString() ?: ""
        if (text.contains(".") && RulesRepository.isUrlBlocked(this, text)) {
            showBlockScreen()
            return
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            checkForBlockedUrls(child)
            child.recycle()
        }
    }

    private fun showBlockScreen(isUninstallAttempt: Boolean = false) {
        val intent = Intent(this, BlockActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        intent.putExtra("isUninstallAttempt", isUninstallAttempt)
        startActivity(intent)
    }

    override fun onInterrupt() {}
}
