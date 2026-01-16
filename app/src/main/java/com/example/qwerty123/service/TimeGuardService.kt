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

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("TimeGuardService", "Service connected")
        FirebaseRepository.listenForRules(this)
        startUsageTimer()
    }

    private fun startUsageTimer() {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.post(object : Runnable {
            override fun run() {
                if (currentApp.isNotBlank() && currentApp != packageName) {
                    RulesRepository.addUsageMinute(this@TimeGuardService, currentApp)
                    
                    val prefs = getSharedPreferences("last_location", Context.MODE_PRIVATE)
                    val lat = prefs.getFloat("lat", 0f).toDouble()
                    val lon = prefs.getFloat("lon", 0f).toDouble()
                    
                    if (RulesRepository.isAppBlockedWithLocation(this@TimeGuardService, currentApp, lat, lon)) {
                        showBlockScreen()
                    }
                }
                handler.postDelayed(this, 60000)
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

        val prefs = getSharedPreferences("last_location", Context.MODE_PRIVATE)
        val lat = prefs.getFloat("lat", 0f).toDouble()
        val lon = prefs.getFloat("lon", 0f).toDouble()

        // 1. Блокировка приложений
        if (packageName.isNotBlank() && packageName != "com.android.settings") {
            if (RulesRepository.isAppBlockedWithLocation(this, packageName, lat, lon)) {
                showBlockScreen()
                return
            }
        }

        // 2. Блокировка URL
        if (packageName == "com.android.chrome" || event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            rootInActiveWindow?.let { rootNode ->
                val urlBarNodes = rootNode.findAccessibilityNodeInfosByViewId("com.android.chrome:id/url_bar")
                if (urlBarNodes.isNotEmpty()) {
                    val urlText = urlBarNodes[0].text?.toString() ?: ""
                    if (urlText.isNotBlank() && RulesRepository.isUrlBlocked(this, urlText)) {
                        showBlockScreen()
                    }
                } else {
                    checkForBlockedUrls(rootNode)
                }
            }
        }

        // 3. Защита от удаления (с проверкой временного разрешения)
        if (!RulesRepository.isUninstallAllowed()) {
            if (packageName == "com.android.settings" || 
                packageName == "com.google.android.packageinstaller" || 
                packageName == "com.android.packageinstaller" ||
                packageName.contains("packageinstaller")) {
                
                rootInActiveWindow?.let { rootNode ->
                    if (findTextRecursive(rootNode, "TimeGuard") && 
                        (findTextRecursive(rootNode, "Uninstall") || 
                         findTextRecursive(rootNode, "Удалить") || 
                         findTextRecursive(rootNode, "Стереть") ||
                         findTextRecursive(rootNode, "Остановить"))) {
                        Log.w("TimeGuardService", "DETECTED UNINSTALL ATTEMPT!")
                        showBlockScreen(isUninstallAttempt = true)
                    }
                }
            }
        }
    }

    private fun findTextRecursive(node: AccessibilityNodeInfo?, textToFind: String): Boolean {
        if (node == null) return false
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        if (text.contains(textToFind, ignoreCase = true) || contentDesc.contains(textToFind, ignoreCase = true)) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (findTextRecursive(child, textToFind)) return true
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
