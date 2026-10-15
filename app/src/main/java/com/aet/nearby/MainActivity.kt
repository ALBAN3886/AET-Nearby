package com.aet.nearby

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var results: LinearLayout
    private lateinit var status: TextView
    private val seen = mutableSetOf<String>()
    private var receiverRegistered = false

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter
    }

    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            showWifiResults()
        }
    }

    private val btCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            runOnUiThread {
                val device = result.device
                val address = try {
                    device.address ?: "Adresse masquée"
                } catch (_: SecurityException) {
                    "Adresse protégée"
                }
                val name = try {
                    device.name ?: result.scanRecord?.deviceName ?: "Appareil Bluetooth"
                } catch (_: SecurityException) {
                    "Appareil Bluetooth"
                }
                if (seen.add(address)) {
                    addResult("BLUETOOTH", name, address, result.rssi)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            runOnUiThread {
                status.text = "Échec du scan Bluetooth : $errorCode"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildInterface()
        requestNeededPermissions()
    }

    private fun buildInterface() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 20)
            setBackgroundColor(0xFFF4F7FB.toInt())
        }

        val title = TextView(this).apply {
            text = "AET NEARBY"
            textSize = 28f
            setTextColor(0xFF123B70.toInt())
            gravity = Gravity.CENTER
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "AET TECHNOLOGY • Scanner de proximité"
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 22)
        }
        root.addView(subtitle)

        status = TextView(this).apply {
            text = "Prêt à scanner"
            textSize = 15f
            setPadding(0, 8, 0, 16)
        }
        root.addView(status)

        val btButton = Button(this).apply {
            text = "Scanner les appareils Bluetooth"
            setOnClickListener { startBluetoothScan() }
        }
        root.addView(btButton, matchWidth())

        val wifiButton = Button(this).apply {
            text = "Rechercher les réseaux Wi-Fi"
            setOnClickListener { startWifiScan() }
        }
        root.addView(wifiButton, matchWidth())

        val clearButton = Button(this).apply {
            text = "Effacer les résultats"
            setOnClickListener {
                results.removeAllViews()
                seen.clear()
                status.text = "Résultats effacés"
            }
        }
        root.addView(clearButton, matchWidth())

        val heading = TextView(this).apply {
            text = "RÉSULTATS"
            textSize = 18f
            setTextColor(0xFF123B70.toInt())
            setPadding(0, 18, 0, 8)
        }
        root.addView(heading)

        val scroll = ScrollView(this)
        results = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(results)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val footer = TextView(this).apply {
            text = "AET Technology • Version 1.0"
            gravity = Gravity.CENTER
            textSize = 12f
            setPadding(0, 12, 0, 0)
        }
        root.addView(footer)

        setContentView(root)
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun requestNeededPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED)
                permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)

        if (permissions.isNotEmpty()) {
            requestPermissions(permissions.toTypedArray(), 100)
        }
    }

    private fun hasBluetoothPermission(): Boolean {
        return Build.VERSION.SDK_INT < 31 ||
            (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
             checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
    }

    private fun startBluetoothScan() {
        if (!hasBluetoothPermission()) {
            requestNeededPermissions()
            Toast.makeText(this, "Autorise Bluetooth puis recommence", Toast.LENGTH_LONG).show()
            return
        }

        val adapter = bluetoothAdapter
        if (adapter == null) {
            status.text = "Bluetooth non disponible sur ce téléphone"
            return
        }

        try {
            if (!adapter.isEnabled) {
                startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                status.text = "Active Bluetooth puis relance le scan"
                return
            }

            seen.clear()
            status.text = "Recherche Bluetooth en cours..."
            adapter.bluetoothLeScanner?.startScan(btCallback)
                ?: run { status.text = "Scanner Bluetooth indisponible" }

            root.postDelayed({
                try {
                    adapter.bluetoothLeScanner?.stopScan(btCallback)
                    status.text = "Scan Bluetooth terminé"
                } catch (_: SecurityException) {
                    status.text = "Autorisation Bluetooth manquante"
                }
            }, 12000)
        } catch (_: SecurityException) {
            status.text = "Autorisation Bluetooth refusée"
        }
    }

    private fun startWifiScan() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES), 101)
            status.text = "Autorise l'accès aux appareils Wi-Fi proches puis recommence"
            return
        }

        try {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (!wifi.isWifiEnabled) {
                status.text = "Active le Wi-Fi puis recommence"
                return
            }

            if (!receiverRegistered) {
                registerReceiver(wifiReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
                receiverRegistered = true
            }

            status.text = "Recherche des réseaux Wi-Fi..."
            val started = wifi.startScan()
            if (!started) showWifiResults()
        } catch (_: SecurityException) {
            status.text = "Permission Wi-Fi ou localisation nécessaire"
        } catch (e: Exception) {
            status.text = "Scan Wi-Fi indisponible"
        }
    }

    private fun showWifiResults() {
        try {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val networks = wifi.scanResults
            status.text = "Réseaux Wi-Fi visibles : ${networks.size}"
            if (networks.isEmpty()) {
                addResult("WI-FI", "Aucun réseau affiché", "Vérifie les autorisations et la localisation", 0)
            } else {
                networks.forEach {
                    val name = it.SSID.takeIf { s -> s.isNotBlank() } ?: "Réseau masqué"
                    addResult("WI-FI", name, "Sécurité : ${it.capabilities} • Canal ${it.frequency} MHz", it.level)
                }
            }
        } catch (_: SecurityException) {
            status.text = "Autorisation requise pour lire les résultats Wi-Fi"
        }
    }

    private fun addResult(type: String, name: String, detail: String, signal: Int) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(0xFFFFFFFF.toInt())
        }

        val heading = TextView(this).apply {
            text = "[$type] $name"
            textSize = 16f
            setTextColor(0xFF123B70.toInt())
        }
        card.addView(heading)

        val info = TextView(this).apply {
            text = "$detail\nSignal : ${signal} dBm"
            textSize = 12f
            setPadding(0, 5, 0, 0)
        }
        card.addView(info)

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 0, 10)
        results.addView(card, params)
    }

    override fun onDestroy() {
        try {
            if (hasBluetoothPermission()) bluetoothAdapter?.bluetoothLeScanner?.stopScan(btCallback)
        } catch (_: Exception) { }
        if (receiverRegistered) {
            try { unregisterReceiver(wifiReceiver) } catch (_: Exception) { }
        }
        super.onDestroy()
    }
}
