package com.example.qwerty123.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.qwerty123.R
import com.example.qwerty123.data.FirebaseRepository
import com.example.qwerty123.data.RulesRepository
import com.google.android.gms.location.*

class LocationTrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    updateLocation(location)
                }
            }
        }

        startForegroundService()
        requestLocationUpdates()
        getLastLocation()
    }

    private fun getLastLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let { updateLocation(it) }
            }
        }
    }

    private fun startForegroundService() {
        val channelId = "LocationServiceChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Location Tracking Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("TimeGuard")
            .setContentText("Отслеживание местоположения активно")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()

        startForeground(1, notification)
    }

    private fun requestLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateIntervalMillis(5000)
            .build()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun updateLocation(location: Location) {
        FirebaseRepository.uploadLocation(this, location.latitude, location.longitude)
        
        // Сохраняем локально для TimeGuardService
        getSharedPreferences("last_location", Context.MODE_PRIVATE).edit()
            .putFloat("lat", location.latitude.toFloat())
            .putFloat("lon", location.longitude.toFloat())
            .apply()

        checkGeofences(location)
    }
    
    private fun checkGeofences(location: Location) {
        val fences = RulesRepository.getActiveGeofences()
        for (fence in fences) {
            val results = FloatArray(1)
            Location.distanceBetween(location.latitude, location.longitude, fence.lat, fence.lon, results)
            // Здесь можно добавить уведомление родителю, если ребенок вышел
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
