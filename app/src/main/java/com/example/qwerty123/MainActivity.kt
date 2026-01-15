package com.example.qwerty123

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.qwerty123.data.*
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.qwerty123.service.LocationTrackingService
import com.example.qwerty123.ui.OSMMap
import com.example.qwerty123.ui.theme.Qwerty123Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Qwerty123Theme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val savedRole = remember { Prefs.getRole(context) }
    val savedFamilyId = remember { Prefs.getFamilyId(context) }
    
    var currentScreen by remember { 
        mutableStateOf<Screen>(
            when {
                savedRole == "parent" && savedFamilyId != null -> Screen.ParentDashboard
                savedRole == "parent" && savedFamilyId == null -> Screen.ParentSetup
                savedRole == "child" && savedFamilyId != null -> Screen.ChildDashboard
                savedRole == "child" && savedFamilyId == null -> Screen.ChildSetup
                else -> Screen.RoleSelection
            }
        ) 
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentScreen) {
                Screen.RoleSelection -> RoleSelectionScreen(
                    onParentSelected = { currentScreen = Screen.ParentSetup },
                    onChildSelected = { currentScreen = Screen.ChildSetup }
                )
                Screen.ParentSetup -> ParentSetupScreen(onConnected = { currentScreen = Screen.ParentDashboard })
                Screen.ChildSetup -> ChildSetupScreen(onConnected = { currentScreen = Screen.ChildDashboard })
                Screen.ParentDashboard -> ParentDashboard(onBack = { currentScreen = Screen.RoleSelection })
                Screen.ChildDashboard -> ChildDashboard(onBack = { currentScreen = Screen.RoleSelection })
                else -> {}
            }
        }
    }
}

sealed class Screen {
    object RoleSelection : Screen()
    object ParentSetup : Screen()
    object ChildSetup : Screen()
    object ParentDashboard : Screen()
    object ChildDashboard : Screen()
}

@Composable
fun RoleSelectionScreen(onParentSelected: () -> Unit, onChildSelected: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("TimeGuard", style = MaterialTheme.typography.displayLarge)
        Spacer(modifier = Modifier.height(48.dp))
        Button(onClick = { Prefs.saveRole(context, "parent"); onParentSelected() }, modifier = Modifier.fillMaxWidth(0.7f)) {
            Text("Я Родитель")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { Prefs.saveRole(context, "child"); onChildSelected() }, modifier = Modifier.fillMaxWidth(0.7f)) {
            Text("Я Ребёнок")
        }
    }
}

@Composable
fun ParentSetupScreen(onConnected: () -> Unit) {
    val context = LocalContext.current
    var inputId by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Создание семьи", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = inputId, onValueChange = { inputId = it }, label = { Text("Код семьи") })
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = pin, 
            onValueChange = { if (it.length <= 4) pin = it }, 
            label = { Text("Придумайте ПИН (4 цифры)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { 
            if (inputId.isNotBlank() && pin.isNotBlank()) { 
                FirebaseRepository.saveFamilyId(context, inputId)
                Prefs.savePin(context, pin)
                FirebaseRepository.saveParentPin(inputId, pin)
                onConnected() 
            } else {
                Toast.makeText(context, "Заполните все поля", Toast.LENGTH_SHORT).show()
            }
        }) { Text("Начать управление") }
    }
}

@Composable
fun ChildSetupScreen(onConnected: () -> Unit) {
    val context = LocalContext.current
    var inputId by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Подключение", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = inputId, onValueChange = { inputId = it }, label = { Text("Код семьи") })
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { 
            if (inputId.isNotBlank()) { 
                FirebaseRepository.saveFamilyId(context, inputId)
                onConnected() 
            } 
        }) { Text("Подключиться") }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChildDashboard(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val familyId = FirebaseRepository.getFamilyId(context) ?: ""
    var showPinDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var enteredPin by remember { mutableStateOf("") }
    val correctPin = Prefs.getPin(context) ?: "0000"

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val intent = Intent(context, LocationTrackingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
    }

    LaunchedEffect(Unit) {
        scope.launch {
            val apps = getInstalledApps(context)
            FirebaseRepository.uploadChildApps(context, apps)
            FirebaseRepository.syncInstalledApps(context, apps)
        }
        FirebaseRepository.listenForRules(context)
        launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false; enteredPin = "" },
            title = { Text("Введите ПИН родителя") },
            text = {
                OutlinedTextField(
                    value = enteredPin, 
                    onValueChange = { if (it.length <= 4) enteredPin = it }, 
                    label = { Text("ПИН") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = PasswordVisualTransformation()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (enteredPin == correctPin) {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        showPinDialog = false
                        enteredPin = ""
                    } else {
                        Toast.makeText(context, "Неверный ПИН", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("OK") }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false; enteredPin = "" },
            title = { Text("Выход из режима ребенка") },
            text = {
                Column {
                    Text("Введите ПИН родителя для сброса роли:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = enteredPin, 
                        onValueChange = { if (it.length <= 4) enteredPin = it }, 
                        label = { Text("ПИН") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (enteredPin == correctPin) {
                        Prefs.saveRole(context, "")
                        Prefs.saveFamilyId(context, "")
                        showResetDialog = false
                        onBack()
                    } else {
                        Toast.makeText(context, "Неверный ПИН", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Сбросить роль") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Режим ребёнка", 
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp).combinedClickable(
                onClick = {},
                onLongClick = { 
                    enteredPin = ""
                    showResetDialog = true 
                }
            )
        )
        Text("Код семьи: $familyId", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { 
            enteredPin = ""
            showPinDialog = true 
        }) { Text("Настройки доступности") }
    }
}

@Composable
fun ParentDashboard(onBack: () -> Unit) {
    val context = LocalContext.current
    var tabIndex by remember { mutableStateOf(0) }
    val familyId = FirebaseRepository.getFamilyId(context) ?: ""

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Код вашей семьи:", style = MaterialTheme.typography.labelMedium)
                    Text(familyId, style = MaterialTheme.typography.headlineSmall)
                }
                OutlinedButton(onClick = { Prefs.saveRole(context, ""); Prefs.saveFamilyId(context, ""); onBack() }) { 
                    Text("Выход") 
                }
            }
        }

        TabRow(selectedTabIndex = tabIndex) {
            Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("Приложения") })
            Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("Черный список") })
            Tab(selected = tabIndex == 2, onClick = { tabIndex = 2 }, text = { Text("Навигация") })
        }
        
        when(tabIndex) {
            0 -> ParentAppsTab(familyId)
            1 -> ParentBlacklistTab(familyId)
            2 -> ParentNavigationTab(familyId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentAppsTab(familyId: String) {
    var childApps by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    var blockedPackages by remember { mutableStateOf<Set<String>>(emptySet()) }
    var appLimits by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var showLimitDialog by remember { mutableStateOf<String?>(null) }
    var limitMinutes by remember { mutableStateOf("") }
    
    LaunchedEffect(familyId) { 
        FirebaseRepository.observeChildApps(familyId) { childApps = it }
        FirebaseRepository.observeBlockedApps(familyId) { blockedPackages = it }
        FirebaseRepository.observeTimeLimits(familyId) { appLimits = it }
    }

    if (showLimitDialog != null) {
        AlertDialog(
            onDismissRequest = { showLimitDialog = null },
            title = { Text("Лимит времени (мин/день)") },
            text = {
                OutlinedTextField(value = limitMinutes, onValueChange = { limitMinutes = it }, label = { Text("Минуты (0 для сброса)") })
            },
            confirmButton = {
                Button(onClick = {
                    val mins = limitMinutes.toIntOrNull() ?: 0
                    FirebaseRepository.setAppTimeLimit(familyId, showLimitDialog!!, mins)
                    showLimitDialog = null
                }) { Text("Сохранить") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Приложения на телефоне ребёнка:", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(childApps) { app ->
                val pkg = app["packageName"] ?: ""
                val name = app["name"] ?: ""
                val currentLimit = appLimits[pkg] ?: 0
                
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(name)
                        if (currentLimit > 0) Text("Лимит: $currentLimit мин.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { showLimitDialog = pkg; limitMinutes = currentLimit.toString() }) { Icon(Icons.Default.Settings, null) }
                    Switch(checked = blockedPackages.contains(pkg), onCheckedChange = { FirebaseRepository.setAppBlocked(familyId, pkg, it) })
                }
            }
        }
    }
}

@Composable
fun ParentBlacklistTab(familyId: String) {
    var blockedUrls by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var newUrl by remember { mutableStateOf("") }

    LaunchedEffect(familyId) {
        FirebaseRepository.observeBlockedUrls(familyId) { blockedUrls = it }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(value = newUrl, onValueChange = { newUrl = it }, label = { Text("Заблокировать URL (напр. reddit)") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { 
            if (newUrl.isNotBlank()) {
                FirebaseRepository.addBlockedUrl(familyId, newUrl)
                newUrl = ""
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Добавить в черный список") }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text("Список заблокированных сайтов:", style = MaterialTheme.typography.titleMedium)
        LazyColumn {
            items(blockedUrls.toList()) { (key, url) ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(url, modifier = Modifier.weight(1f))
                    IconButton(onClick = { FirebaseRepository.removeBlockedUrl(familyId, key) }) { Icon(Icons.Default.Delete, null) }
                }
            }
        }
    }
}

@Composable
fun ParentNavigationTab(familyId: String) {
    var childLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var lastUpdated by remember { mutableStateOf(0L) }
    var allGeofences by remember { mutableStateOf<List<GeofenceModel>>(emptyList()) }
    var childApps by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    var selectedPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var fenceName by remember { mutableStateOf("") }
    var showAppsDialog by remember { mutableStateOf<GeofenceModel?>(null) }
    var showRadiusDialog by remember { mutableStateOf<GeofenceModel?>(null) }
    var newRadius by remember { mutableStateOf("") }
    var mapCenterTrigger by remember { mutableStateOf<GeoPoint?>(null) }

    LaunchedEffect(familyId) {
        FirebaseRepository.observeChildLocation(familyId) { lat, lon, time -> 
            childLocation = GeoPoint(lat, lon)
            lastUpdated = time
        }
        FirebaseRepository.observeGeofences(familyId) { allGeofences = it }
        FirebaseRepository.observeChildApps(familyId) { childApps = it }
    }

    if (showAppsDialog != null) {
        AlertDialog(
            onDismissRequest = { showAppsDialog = null },
            title = { Text("Блок в зоне: ${showAppsDialog?.name}") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(childApps) { app ->
                        val pkg = app["packageName"] ?: ""
                        val isBlocked = showAppsDialog!!.blockedApps.contains(pkg)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                            val newList = showAppsDialog!!.blockedApps.toMutableList()
                            if (isBlocked) newList.remove(pkg) else newList.add(pkg)
                            FirebaseRepository.setGeofenceApps(familyId, showAppsDialog!!.name, newList)
                        }.padding(8.dp)) {
                            Checkbox(checked = isBlocked, onCheckedChange = null)
                            Text(app["name"] ?: pkg)
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { showAppsDialog = null }) { Text("Готово") } }
        )
    }

    if (showRadiusDialog != null) {
        AlertDialog(
            onDismissRequest = { showRadiusDialog = null },
            title = { Text("Масштаб зоны (метры)") },
            text = {
                OutlinedTextField(
                    value = newRadius, 
                    onValueChange = { newRadius = it.filter { char -> char.isDigit() } }, 
                    label = { Text("Радиус") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            },
            confirmButton = {
                Button(onClick = {
                    val r = newRadius.toDoubleOrNull() ?: 500.0
                    FirebaseRepository.updateGeofenceRadius(familyId, showRadiusDialog!!.name, r)
                    showRadiusDialog = null
                }) { Text("Сохранить") }
            }
        )
    }

    // Используем одну LazyColumn для всей страницы, чтобы всё прокручивалось
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                Card(modifier = Modifier.fillMaxSize()) {
                    OSMMap(
                        modifier = Modifier.fillMaxSize(), 
                        childLocation = childLocation, 
                        childTimestamp = lastUpdated,
                        allGeofences = allGeofences, 
                        selectedPoint = selectedPoint,
                        centerOnRequest = mapCenterTrigger,
                        onGeofenceClick = { selectedPoint = it }
                    )
                }
                
                if (childLocation != null) {
                    SmallFloatingActionButton(
                        onClick = { mapCenterTrigger = childLocation; mapCenterTrigger = null },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(Icons.Default.Add, "Center")
                    }
                }
            }
            
            if (lastUpdated > 0) {
                val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                Text(
                    "Обновлено: ${sdf.format(java.util.Date(lastUpdated))}", 
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Создать новую зону:", style = MaterialTheme.typography.titleMedium)
            Text("Нажмите на карту, чтобы выбрать точку", style = MaterialTheme.typography.bodySmall)
            
            OutlinedTextField(
                value = fenceName, 
                onValueChange = { fenceName = it }, 
                label = { Text("Название зоны (напр. Школа)") }, 
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
            
            Button(
                onClick = {
                    selectedPoint?.let { 
                        if (fenceName.isNotBlank()) {
                            FirebaseRepository.saveGeofence(familyId, GeofenceModel(name = fenceName, lat = it.latitude, lon = it.longitude))
                            fenceName = ""; selectedPoint = null
                        }
                    }
                }, 
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedPoint != null && fenceName.isNotBlank()
            ) { 
                Text("Сохранить зону") 
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Text("Ваши зоны:", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Список зон идет элементами LazyColumn
        items(allGeofences) { fence ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp), 
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(fence.name, style = MaterialTheme.typography.titleSmall)
                        Text("Радиус: ${fence.radius.toInt()}м", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { 
                        showRadiusDialog = fence
                        newRadius = fence.radius.toInt().toString() 
                    }) { 
                        Icon(Icons.Default.Add, "Радиус") 
                    }
                    IconButton(onClick = { showAppsDialog = fence }) { 
                        Icon(Icons.Default.Settings, "Приложения") 
                    }
                    IconButton(onClick = { FirebaseRepository.deleteGeofence(familyId, fence.name) }) { 
                        Icon(Icons.Default.Delete, "Удалить") 
                    }
                }
            }
        }
        
        // Отступ снизу, чтобы последняя карточка не прилипала к краю
        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

suspend fun getInstalledApps(context: Context): List<AppItem> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    pm.getInstalledPackages(PackageManager.GET_META_DATA).mapNotNull { pkg ->
        val appInfo = pkg.applicationInfo ?: return@mapNotNull null
        val name = appInfo.loadLabel(pm).toString()
        if (name.isEmpty()) return@mapNotNull null
        AppItem(name, pkg.packageName, null)
    }
}
