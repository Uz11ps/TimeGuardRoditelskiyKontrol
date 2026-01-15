package com.example.qwerty123.data

import android.content.Context
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

data class GeofenceModel(
    val id: String = "",
    val name: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val radius: Double = 500.0,
    val blockedApps: List<String> = emptyList()
)

object FirebaseRepository {
    private const val TAG = "FirebaseRepository"
    
    // ВСТАВЬТЕ ВАШ URL ИЗ КОНСОЛИ FIREBASE ЗДЕСЬ если ошибка повторится
    // private const val DB_URL = "https://ваш-проект.firebaseio.com/"

    private fun getDb(): FirebaseDatabase {
        return FirebaseDatabase.getInstance("https://comexampleqwerty123-default-rtdb.firebaseio.com/")
    }

    fun getFamilyId(context: Context): String? = Prefs.getFamilyId(context)
    fun saveFamilyId(context: Context, id: String) = Prefs.saveFamilyId(context, id)

    private fun Any?.toDoubleSafe(): Double? {
        return when (this) {
            is Double -> this
            is Long -> this.toDouble()
            is Float -> this.toDouble()
            is String -> this.toDoubleOrNull()
            else -> null
        }
    }

    private fun safeKey(key: String): String {
        return key.replace(".", "_")
            .replace("$", "_")
            .replace("#", "_")
            .replace("[", "_")
            .replace("]", "_")
            .replace("/", "_")
    }

    // --- CHILD ---
    fun uploadChildApps(context: Context, apps: List<AppItem>) {
        val id = getFamilyId(context) ?: return
        getDb().getReference("families/$id/installed_apps")
            .setValue(apps.map { mapOf("name" to it.name, "packageName" to it.packageName) })
            .addOnFailureListener { Log.e(TAG, "Failed to upload apps: ${it.message}") }
    }

    fun syncInstalledApps(context: Context, apps: List<AppItem>) {
        val id = getFamilyId(context) ?: return
        getDb().getReference("families/$id/child_apps")
            .setValue(apps.associate { safeKey(it.packageName) to it.name })
    }

    fun listenForRules(context: Context) {
        val id = getFamilyId(context) ?: return
        val db = getDb()
        
        db.getReference("families/$id/rules/blocked_packages").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val apps = snapshot.children.mapNotNull { it.getValue(String::class.java) }.toSet()
                updateRulesSilently(context, apps = apps)
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        db.getReference("families/$id/rules/blocked_urls").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val urls = snapshot.children.mapNotNull { it.getValue(String::class.java) }.toSet()
                updateRulesSilently(context, urls = urls)
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        db.getReference("families/$id/rules/time_limits").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val limits = snapshot.children.mapNotNull { 
                    val key = it.key?.replace("_", ".") ?: return@mapNotNull null
                    val value = it.getValue(Int::class.java) ?: 0
                    key to value
                }.toMap()
                RulesRepository.updateTimeLimits(context, limits)
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        db.getReference("families/$id/geofences").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val fences = snapshot.children.mapNotNull { child ->
                    val name = child.key ?: ""
                    val lat = child.child("lat").value.toDoubleSafe() ?: return@mapNotNull null
                    val lon = child.child("lon").value.toDoubleSafe() ?: return@mapNotNull null
                    val radius = child.child("radius").value.toDoubleSafe() ?: 500.0
                    val blocked = child.child("blocked_apps").children.mapNotNull { it.getValue(String::class.java) }
                    GeofenceModel(name, name, lat, lon, radius, blocked)
                }
                RulesRepository.updateGeofences(context, fences)
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        db.getReference("families/$id/pin").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val pin = snapshot.getValue(String::class.java)
                if (pin != null) {
                    Prefs.savePin(context, pin)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun updateRulesSilently(context: Context, apps: Set<String>? = null, urls: Set<String>? = null) {
        val prefs = context.getSharedPreferences("timeguard_rules", Context.MODE_PRIVATE)
        val currentApps = apps ?: prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()
        val currentUrls = urls ?: prefs.getStringSet("blocked_urls", emptySet()) ?: emptySet()
        RulesRepository.updateRules(context, currentApps, currentUrls)
    }

    fun uploadLocation(context: Context, lat: Double, lon: Double) {
        val id = getFamilyId(context) ?: return
        getDb().getReference("families/$id/location/current")
            .setValue(mapOf("latitude" to lat, "longitude" to lon, "timestamp" to System.currentTimeMillis()))
    }

    fun saveParentPin(familyId: String, pin: String) {
        getDb().getReference("families/$familyId/pin").setValue(pin)
    }

    // --- PARENT ---
    fun observeChildApps(familyId: String, onUpdate: (List<Map<String, String>>) -> Unit) {
        getDb().getReference("families/$familyId/installed_apps")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val apps = snapshot.children.map { 
                        mapOf("name" to (it.child("name").getValue(String::class.java) ?: ""), 
                              "packageName" to (it.child("packageName").getValue(String::class.java) ?: ""))
                    }
                    onUpdate(apps)
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Observe apps error: ${error.message}")
                }
            })
    }

    fun observeChildLocation(familyId: String, onUpdate: (Double, Double, Long) -> Unit) {
        getDb().getReference("families/$familyId/location/current")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val lat = snapshot.child("latitude").value.toDoubleSafe() ?: 0.0
                    val lon = snapshot.child("longitude").value.toDoubleSafe() ?: 0.0
                    val time = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                    onUpdate(lat, lon, time)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    fun observeGeofences(familyId: String, onUpdate: (List<GeofenceModel>) -> Unit) {
        Log.d(TAG, "Starting to observe geofences for family: $familyId")
        getDb().getReference("families/$familyId/geofences")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val fences = snapshot.children.mapNotNull { child ->
                        val name = child.key ?: ""
                        val lat = child.child("lat").value.toDoubleSafe() ?: return@mapNotNull null
                        val lon = child.child("lon").value.toDoubleSafe() ?: return@mapNotNull null
                        val radius = child.child("radius").value.toDoubleSafe() ?: 500.0
                        val blocked = child.child("blocked_apps").children.mapNotNull { it.getValue(String::class.java) }
                        GeofenceModel(name, name, lat, lon, radius, blocked)
                    }
                    Log.d(TAG, "Received ${fences.size} geofences from Firebase")
                    onUpdate(fences)
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Geofences observe error: ${error.message}. Code: ${error.code}")
                }
            })
    }

    fun setAppBlocked(familyId: String, packageName: String, isBlocked: Boolean) {
        val ref = getDb().getReference("families/$familyId/rules/blocked_packages")
        ref.get().addOnSuccessListener { snapshot ->
            val list = snapshot.children.mapNotNull { it.getValue(String::class.java) }.toMutableList()
            if (isBlocked && !list.contains(packageName)) list.add(packageName)
            else if (!isBlocked) list.remove(packageName)
            ref.setValue(list)
        }
    }

    fun addBlockedUrl(familyId: String, url: String) {
        if (familyId.isBlank() || url.isBlank()) return
        val ref = getDb().getReference("families/$familyId/rules/blocked_urls")
        ref.child(safeKey(url)).setValue(url)
    }

    fun removeBlockedUrl(familyId: String, urlKey: String) {
        if (familyId.isBlank() || urlKey.isBlank()) return
        getDb().getReference("families/$familyId/rules/blocked_urls/${safeKey(urlKey)}").removeValue()
    }

    fun observeBlockedUrls(familyId: String, onUpdate: (Map<String, String>) -> Unit) {
        getDb().getReference("families/$familyId/rules/blocked_urls")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val urls = snapshot.children.mapNotNull { 
                        val key = it.key ?: return@mapNotNull null
                        val value = it.getValue(String::class.java) ?: return@mapNotNull null
                        key to value
                    }.toMap()
                    onUpdate(urls)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    fun setAppTimeLimit(familyId: String, packageName: String, minutes: Int) {
        val ref = getDb().getReference("families/$familyId/rules/time_limits/${safeKey(packageName)}")
        if (minutes > 0) {
            ref.setValue(minutes)
        } else {
            ref.removeValue()
        }
    }

    fun observeTimeLimits(familyId: String, onUpdate: (Map<String, Int>) -> Unit) {
        getDb().getReference("families/$familyId/rules/time_limits")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val limits = snapshot.children.mapNotNull { 
                        val key = it.key?.replace("_", ".") ?: return@mapNotNull null
                        val value = it.getValue(Int::class.java) ?: return@mapNotNull null
                        key to value
                    }.toMap()
                    onUpdate(limits)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    fun updateGeofenceRadius(familyId: String, fenceName: String, radius: Double) {
        getDb().getReference("families/$familyId/geofences/${safeKey(fenceName)}/radius").setValue(radius)
    }

    fun saveGeofence(familyId: String, fence: GeofenceModel) {
        if (familyId.isBlank() || fence.name.isBlank()) {
            Log.e(TAG, "Cannot save geofence: familyId or name is blank")
            return
        }
        val ref = getDb().getReference("families/$familyId/geofences/${safeKey(fence.name)}")
        ref.setValue(mapOf(
            "lat" to fence.lat,
            "lon" to fence.lon,
            "radius" to fence.radius,
            "blocked_apps" to fence.blockedApps
        )).addOnSuccessListener {
            Log.d(TAG, "SUCCESS: Geofence ${fence.name} saved to Firebase")
        }.addOnFailureListener {
            Log.e(TAG, "ERROR: Firebase rejected save: ${it.message}")
        }
    }
    
    fun observeBlockedApps(familyId: String, onUpdate: (Set<String>) -> Unit) {
        getDb().getReference("families/$familyId/rules/blocked_packages")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val apps = snapshot.children.mapNotNull { it.getValue(String::class.java) }.toSet()
                    onUpdate(apps)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    fun setGeofenceApps(familyId: String, fenceName: String, apps: List<String>) {
        getDb().getReference("families/$familyId/geofences/${safeKey(fenceName)}/blocked_apps").setValue(apps)
    }

    fun deleteGeofence(familyId: String, name: String) {
        getDb().getReference("families/$familyId/geofences/${safeKey(name)}").removeValue()
    }
}
