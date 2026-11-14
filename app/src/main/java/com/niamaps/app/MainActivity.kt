package com.niamaps.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
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
        mapView.setBackgroundColor(Color.rgb(232, 237, 242))
        mapView.onCreate(savedInstanceState)
        root.addView(mapView, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        mapStatus = TextView(this).apply {
            text = "Conectando con el mapa…"
            textSize = 14f
            setTextColor(Color.rgb(32, 45, 62))
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = rounded(Color.WHITE, 16)
        }
        root.addView(mapStatus, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))

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
        searchRow.addView(search, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginEnd = dp(8) })
        searchRow.addView(find, LinearLayout.LayoutParams(dp(66), dp(50)))
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
                Toast.makeText(this@MainActivity, "Modo " + label + " seleccionado. El cálculo de rutas aún está en desarrollo.", Toast.LENGTH_LONG).show()
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

        val rail = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(16, 24, 39), 20)
            elevation = dp(8).toFloat()
            setPadding(dp(5), dp(8), dp(5), dp(8))
        }
        val tabs = listOf(Triple("⌖", "Mapa", "Mapa"), Triple("♡", "Guardados", "Guardados"), Triple("●", "Perfil", "Perfil"))
        tabs.forEachIndexed { index, tab ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = rounded(if (index == 0) Color.rgb(55, 132, 220) else Color.TRANSPARENT, 12)
                setPadding(dp(5), dp(9), dp(5), dp(9))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (tab.second == "Mapa") {
                        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(19.4326, -99.1332), 11.5))
                    } else {
                        Toast.makeText(this@MainActivity, tab.second + ": esta sección estará disponible en una próxima actualización.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            item.addView(TextView(this).apply {
                text = tab.first
                textSize = 23f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
            })
            item.addView(TextView(this).apply {
                text = tab.second
                textSize = 9f
                gravity = Gravity.CENTER
                setTextColor(if (index == 0) Color.WHITE else Color.rgb(190, 202, 218))
            })
            rail.addView(item, LinearLayout.LayoutParams(dp(68), dp(66)).apply {
                if (index != 2) bottomMargin = dp(4)
            })
        }
        val railParams = FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL)
        railParams.setMargins(0, dp(150), dp(10), dp(24))
        root.addView(rail, railParams)

        val locate = actionButton("◎", Color.WHITE) { locateUser() }.apply {
            setTextColor(Color.rgb(24, 38, 56))
            textSize = 27f
            elevation = dp(8).toFloat()
            contentDescription = "Mi ubicación"
        }
        val locParams = FrameLayout.LayoutParams(dp(56), dp(56), Gravity.START or Gravity.BOTTOM)
        locParams.setMargins(dp(16), 0, 0, dp(62))
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
                readyMap.cameraPosition = CameraPosition.Builder().target(LatLng(19.4326, -99.1332)).zoom(11.5).build()
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
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun hideKeyboard(view: View) {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun searchPlace(query: String) {
        if (query.isBlank()) {
            Toast.makeText(this, "Escribe un lugar para buscar", Toast.LENGTH_SHORT).show()
            return
        }
        mapStatus.text = "Buscando: " + query
        mapStatus.visibility = View.VISIBLE
        executor.execute {
            var connection: HttpURLConnection? = null
            try {
                val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
                val url = URL("https://nominatim.openstreetmap.org/search?q=" + encoded + "&format=jsonv2&limit=1")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 12000
                    readTimeout = 12000
                    setRequestProperty("User-Agent", "NIAMapsAndroid/1.1 (map search)")
                    setRequestProperty("Accept", "application/json")
                }
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val results = JSONArray(response)
                runOnUiThread {
                    if (results.length() > 0) {
                        val place = results.getJSONObject(0)
                        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(place.getDouble("lat"), place.getDouble("lon")), 15.0))
                        mapStatus.visibility = View.GONE
                    } else {
                        mapStatus.visibility = View.GONE
                        Toast.makeText(this, "No se encontró ese lugar. Prueba con otra dirección.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    mapStatus.visibility = View.GONE
                    Toast.makeText(this, "No se pudo buscar. Revisa tu conexión e inténtalo otra vez.", Toast.LENGTH_LONG).show()
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
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
            val manager = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
            val last = manager.getProviders(true).mapNotNull { provider ->
                try { manager.getLastKnownLocation(provider) } catch (_: SecurityException) { null }
            }.maxByOrNull { it.time }
            if (last != null) map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(last.latitude, last.longitude), 16.0))
            else Toast.makeText(this, "No hay ubicación reciente. Activa el GPS y vuelve a intentar.", Toast.LENGTH_LONG).show()
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
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { mapView.onDestroy(); executor.shutdown(); super.onDestroy() }
}

private fun LinearLayout.childrenButtons(): List<Button> =
    (0 until childCount).mapNotNull { getChildAt(it) as? Button }
