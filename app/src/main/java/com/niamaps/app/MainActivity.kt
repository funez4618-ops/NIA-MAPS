package com.niamaps.app

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : android.app.Activity() {
    private lateinit var mapView: MapView
    private var map: MapLibreMap? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val locationRequest = 41

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        val root = FrameLayout(this)
        mapView = MapView(this)
        mapView.onCreate(savedInstanceState)
        root.addView(mapView, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 14)
            setBackgroundColor(0xEE101827.toInt())
        }
        val heading = TextView(this).apply {
            text = "NIA MAPS"
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
            letterSpacing = 0.12f
            setPadding(4, 2, 4, 10)
        }
        val searchRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val search = EditText(this).apply {
            hint = "Buscar lugar o dirección"
            textSize = 15f
            setSingleLine(true)
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFFB8C3D3.toInt())
            setBackgroundColor(0xFF263449.toInt())
            setPadding(14, 8, 14, 8)
        }
        val find = Button(this).apply { text = "Ir"; setOnClickListener { searchPlace(search.text.toString()) } }
        searchRow.addView(search, LinearLayout.LayoutParams(0, 52, 1f))
        searchRow.addView(find)
        panel.addView(heading)
        panel.addView(searchRow)
        val modes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        listOf("Auto", "Caminar", "Bici").forEach { label ->
            modes.addView(Button(this).apply { text = label; isAllCaps = false; setOnClickListener {
                Toast.makeText(this@MainActivity, "Modo $label: próximamente", Toast.LENGTH_SHORT).show()
            } }, LinearLayout.LayoutParams(0, 48, 1f))
        }
        panel.addView(modes)
        val topParams = FrameLayout.LayoutParams(-1, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP)
        topParams.setMargins(16, 22, 16, 0)
        root.addView(panel, topParams)

        val locate = Button(this).apply {
            text = "◎"
            textSize = 24f
            contentDescription = "Mi ubicación"
            setOnClickListener { locateUser() }
        }
        val locParams = FrameLayout.LayoutParams(58, 58, Gravity.END or Gravity.BOTTOM)
        locParams.setMargins(0, 0, 20, 28)
        root.addView(locate, locParams)

        mapView.getMapAsync { readyMap ->
            map = readyMap
            readyMap.setStyle("""{"version":8,"sources":{"osm":{"type":"raster","tiles":["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],"tileSize":256,"attribution":"© OpenStreetMap contributors"}},"layers":[{"id":"osm","type":"raster","source":"osm"}]}""") {
                readyMap.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(19.4326, -99.1332)).zoom(11.5).build()
                requestLocationPermission()
            }
        }
    }

    private fun searchPlace(query: String) {
        if (query.isBlank()) {
            Toast.makeText(this, "Escribe un lugar para buscar", Toast.LENGTH_SHORT).show()
            return
        }
        executor.execute {
            try {
                @Suppress("DEPRECATION")
                val results = Geocoder(this, Locale.getDefault()).getFromLocationName(query, 1)
                runOnUiThread {
                    val place = results?.firstOrNull()
                    if (place != null) {
                        map?.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                            LatLng(place.latitude, place.longitude), 15.0
                        ))
                    } else Toast.makeText(this, "No se encontró el lugar", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                runOnUiThread { Toast.makeText(this, "No fue posible buscar. Intenta de nuevo.", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), locationRequest)
        } else locateUser()
    }

    private fun locateUser() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission(); return
        }
        try {
            val manager = getSystemService(LOCATION_SERVICE) as LocationManager
            val providers = manager.getProviders(true)
            val last: Location? = providers.mapNotNull { provider ->
                try { manager.getLastKnownLocation(provider) } catch (_: SecurityException) { null }
            }.maxByOrNull { it.time }
            if (last != null) map?.animateCamera(
                org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(LatLng(last.latitude, last.longitude), 16.0)
            ) else Toast.makeText(this, "Esperando ubicación GPS…", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, "Permiso de ubicación no disponible", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationRequest && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) locateUser()
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { mapView.onDestroy(); executor.shutdown(); super.onDestroy() }
}
