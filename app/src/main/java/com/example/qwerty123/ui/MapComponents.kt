package com.example.qwerty123.ui

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.qwerty123.data.GeofenceModel
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun OSMMap(
    modifier: Modifier = Modifier,
    childLocation: GeoPoint?,
    childTimestamp: Long = 0, // Добавили время
    allGeofences: List<GeofenceModel> = emptyList(),
    selectedPoint: GeoPoint? = null,
    centerOnRequest: GeoPoint? = null,
    onGeofenceClick: (GeoPoint) -> Unit
) {
    val context = LocalContext.current
    
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
        }
    }

    // Автоматически центрируем на ребенке при первом получении координат
    var hasCenteredOnChild by remember { mutableStateOf(false) }
    LaunchedEffect(childLocation) {
        if (childLocation != null && !hasCenteredOnChild) {
            mapView.controller.animateTo(childLocation)
            hasCenteredOnChild = true
        }
    }

    // Реакция на запрос центрирования
    LaunchedEffect(centerOnRequest) {
        centerOnRequest?.let {
            mapView.controller.animateTo(it)
        }
    }

    val myLocationOverlay = remember {
        MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
            enableMyLocation()
            runOnFirstFix {
                val myLocation = myLocation
                // Центрируем на родителе только если ребенка еще нет на карте
                if (myLocation != null && childLocation == null && !hasCenteredOnChild) {
                    mapView.post { mapView.controller.animateTo(myLocation) }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose { mapView.onPause() }
    }

    AndroidView(
        factory = { 
            mapView.overlays.add(myLocationOverlay)
            mapView 
        },
        modifier = modifier,
        update = { map ->
            val locationOverlay = map.overlays.filterIsInstance<MyLocationNewOverlay>().firstOrNull()
            map.overlays.clear()
            locationOverlay?.let { map.overlays.add(it) }

            // 1. Отрисовка ВСЕХ сохраненных геозон
            allGeofences.forEach { fence ->
                val center = GeoPoint(fence.lat, fence.lon)
                val circlePoints = Polygon.pointsAsCircle(center, fence.radius)
                val circle = Polygon(map)
                circle.points = circlePoints
                circle.fillPaint.color = 0x2200FF00 // Более прозрачный
                circle.outlinePaint.color = 0x8800FF00.toInt()
                circle.title = fence.name
                map.overlays.add(circle)
                
                val marker = Marker(map)
                marker.position = center
                marker.title = fence.name
                map.overlays.add(marker)
            }

            // 2. Отрисовка текущей выбранной (но еще не сохраненной) точки
            if (selectedPoint != null) {
                val tempMarker = Marker(map)
                tempMarker.position = selectedPoint
                tempMarker.title = "Новая зона"
                map.overlays.add(tempMarker)
            }

            // 3. Улучшенный маркер ребёнка
            if (childLocation != null) {
                // Синий ореол (радар) под ребенком
                val radar = Polygon(map)
                radar.points = Polygon.pointsAsCircle(childLocation, 40.0)
                radar.fillPaint.color = 0x33007BFF.toInt()
                radar.outlinePaint.color = 0x66007BFF.toInt()
                radar.outlinePaint.strokeWidth = 2f
                map.overlays.add(radar)

                val childMarker = Marker(map)
                childMarker.position = childLocation
                childMarker.title = "Ребёнок"
                if (childTimestamp > 0) {
                    val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                    childMarker.snippet = "Был здесь в ${sdf.format(java.util.Date(childTimestamp))}"
                    childMarker.showInfoWindow() // Показываем время сразу
                }
                map.overlays.add(childMarker)
            }

            map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                    p?.let { onGeofenceClick(it) }
                    return true
                }
                override fun longPressHelper(p: GeoPoint?): Boolean = false
            }))

            map.invalidate()
        }
    )
}
