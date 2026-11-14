package com.niamaps.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context
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
    private lateinit var mapStatus: TextView
    private var map: MapLibreMap? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val locationRequest = 41
    private var selectedMode = "Auto"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(16, 24, 39)
        window.navigationBarColor = Color.rgb(16, 24, 39)
        MapLibre.getInstance(this)

        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(232, 237, 242)) }
        mapView = MapView(this)
        mapView.onCreate(savedInstanceState)
        root.addView(mapView, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        mapStatus = TextView(this).apply {
            text = "Cargando mapa…"
            textSize = 14f
            setTextColor(Color.rgb(32, 45, 62))
            gravity = Gravity.CENTER
            setPadding(18, 12, 18, 12)
            background = rounded(Color.WHITE, 18)
        }
        val statusParams = FrameLayout.LayoutParams(-2, -2, Gravity.CENTER)
        root.addView(mapStatus, statusParams)

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(12))
            background = rounded(Color.rgb(16, 24, 39), 20)
            elevation = dp(8).toFloat()
        }
        val headingRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = TextView(this).apply {
            text = "NIA MAPS"
            textSize = 25f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.WHITE)
            letterSpacing = 0.08f
        }
        val gpsLabel = TextView(this).apply {
            text = "● GPS"
            textSize = 12f
            setTextColor(Color.rgb(125, 220, 165))
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        headingRow.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        headingRow.addView(gpsLabel)
        panel.addView(headingRow)

        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(10))
        }
        val search = EditText(this).apply {
            hint = "Buscar lugar o dirección"
            textSize = 15f
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(184, 195, 211))
            background = rounded(Color.rgb(38, 52, 73), 12)
            setPadding(dp(14), 0, dp(14), 0)
        }
        val find = actionButton("IR", Color.rgb(55, 132, 220)) {
            hideKeyboard(search)
            searchPlace(search.text.toString())
        }
        searchRow.addView(search, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) })
        searchRow.addView(find, LinearLayout.LayoutParams(dp(64), dp(48)))
        search.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard(search)
                searchPlace(search.text.toString())
                true
            } else false
        }
        panel.addView(searchRow)

        val modes = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        listOf("Auto", "Caminar", "Bici").forEach { label ->
            val modeButton = actionButton(label, if (label == selectedMode) Color.rgb(55, 132, 220) else Color.rgb(43, 57, 77)) {
                selectedMode = label
                Toast.makeText(this@MainActivity, "Modo $label seleccionado", Toast.LENGTH_SHORT).show()
                // Actualiza el resaltado visual; el cálculo de rutas se incorporará en una siguiente versión.
                modes.childrenButtons().forEach { button ->
                    button.background = rounded(if (button.text == selectedMode) Color.rgb(55, 132, 220) else Color.rgb(43, 57, 77), 12)
                }
            }
            modes.addView(modeButton, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                marginEnd = if (label == "Bici") 0 else dp(8)
            })
        }
        panel.addView(modes)

        val topParams = FrameLayout.LayoutParams(-1, -2, Gravity.TOP)
        topParams.setMargins(dp(12), dp(12), dp(12), 0)
        root.addView(panel, topParams)

        val locate = actionButton("◎", Color.WHITE) { locateUser() }.apply {
            setTextColor(Color.rgb(24, 38, 56))
            textSize = 27f
            elevation = dp(8).toFloat()
            contentDescription = "Mi ubicación"
        }
        val locParams = FrameLayout.LayoutParams(dp(56), dp(56), Gravity.END or Gravity.BOTTOM)
        locParams.setMargins(0, 0, dp(18), dp(76))
        root.addView(locate, locParams)

        val attribution = TextView(this).apply {
            text = "© OpenStreetMap contributors"
            textSize = 10f
            setTextColor(Color.rgb(50, 60, 70))
            setPadding(dp(6), dp(3), dp(6), dp(3))
            background = rounded(0xDDFFFFFF.toInt(), 6)
        }
        val attrParams = FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START)
        attrParams.setMargins(dp(8), 0, 0, dp(8))
        root.addView(attribution, attrParams)

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(16, 24, 39), 18)
            elevation = dp(8).toFloat()
            setPadding(dp(8), dp(7), dp(8), dp(7))
        }
        listOf("Mapa", "Guardados", "Perfil").forEachIndexed { index, label ->
            val item = TextView(this).apply {
                text = if (index == 0) "⌖  $label" else label
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(if (index == 0) Color.WHITE else Color.rgb(177, 190, 208))
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }
            bottom.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
        }
        val bottomParams = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)
        bottomParams.setMargins(dp(12), 0, dp(12), dp(12))
        root.addView(bottom, bottomParams)

        mapView.getMapAsync { readyMap ->
            map = readyMap
            val styleJson = """{
              "version": 8,
              "sources": {
                "osm": {
                  "type": "raster",
                  "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
                  "tileSize": 256,
                  "attribution": "© OpenStreetMap contributors"
                }
              },
              "layers": [
                {"id":"background","type":"background","paint":{"background-color":"#e8edf2"}},
                {"id":"osm-raster","type":"raster","source":"osm","minzoom":0,"maxzoom":19}
              ]
            }"""
            readyMap.setStyle(styleJson) {
                readyMap.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(19.4326, -99.1332)).zoom(11.5).build()
                mapStatus.visibility = View.GONE
                requestLocationPermission()
            }
        }
    }

    private fun actionButton(label: String, color: Int, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = rounded(color, 12)
            setPadding(dp(8), 0, dp(8), 0)
            stateListAnimator = null
            setOnClickListener { action() }
        }

    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun hideKeyboard(view: View) {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun searchPlace(query: String) {
        if (query.isBlank()) {
            Toast.makeText(this, "Escribe un lugar para buscar", Toast.LENGTH_SHORT).show()
            return
        }
        mapStatus.text = "Buscando lugar…"
        mapStatus.visibility = View.VISIBLE
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
                        mapStatus.visibility = View.GONE
                    } else {
                        mapStatus.text = "No se encontró el lugar"
                        Toast.makeText(this, "No se encontró el lugar", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    mapStatus.text = "No fue posible buscar. Intenta de nuevo."
                    Toast.makeText(this, "No fue posible buscar. Intenta de nuevo.", Toast.LENGTH_SHORT).show()
                }
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
            requestLocationPermission()
            return
        }
        try {
            val manager = getSystemService(LOCATION_SERVICE) as LocationManager
            val last: Location? = manager.getProviders(true).mapNotNull { provider ->
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

private fun LinearLayout.childrenButtons(): List<Button> =
    (0 until childCount).mapNotNull { getChildAt(it) as? Button }
