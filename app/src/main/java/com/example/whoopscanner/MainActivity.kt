package com.example.whoopscanner

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.whoopscanner.ble.WhoopBleManager
import com.example.whoopscanner.data.HistoricalPoint
import com.example.whoopscanner.ui.theme.WhoopScannerTheme

class MainActivity : ComponentActivity() {

    private lateinit var bleManager: WhoopBleManager
    private val foundDevices = mutableStateListOf<BluetoothDevice>()
    private var logsToExport = ""

    private val createDocumentLauncher = 
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            uri?.let {
                contentResolver.openOutputStream(it)?.use { stream ->
                    stream.write(logsToExport.toByteArray())
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bleManager = WhoopBleManager(this)

        val requestPermissionLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
               // Handle permissions
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        } else {
             requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }

        setContent {
            WhoopScannerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf("scan") }
                    val connectionState by bleManager.connectionState.collectAsState()
                    val heartRate by bleManager.heartRate.collectAsState()
                    val batteryLevel by bleManager.batteryLevel.collectAsState()
                    val isWorn by bleManager.isWorn.collectAsState()
                    val isCharging by bleManager.isCharging.collectAsState()
                    val accelX by bleManager.accelX.collectAsState()
                    val accelY by bleManager.accelY.collectAsState()
                    val accelZ by bleManager.accelZ.collectAsState()
                    val diagnosticLog by bleManager.diagnosticLog.collectAsState()
                    val deviceName by bleManager.deviceName.collectAsState()
                    
                    val skinTemp by bleManager.skinTemp.collectAsState()
                    val hrv by bleManager.hrv.collectAsState()
                    val respiratoryRate by bleManager.respiratoryRate.collectAsState()
                    val spo2 by bleManager.spo2.collectAsState()
                    val stressLevel by bleManager.stressLevel.collectAsState()
                    val strain by bleManager.strain.collectAsState()
                    val recovery by bleManager.recovery.collectAsState()

                    // Effect to navigate when connected
                    LaunchedEffect(connectionState) {
                        if (currentScreen == "scan" && (connectionState.startsWith("Connected") || connectionState.startsWith("Streaming") || connectionState.startsWith("Subscribing"))) {
                            currentScreen = "dashboard"
                        } else if (currentScreen == "dashboard" && connectionState == "Disconnected") {
                             // Optional: Auto-back on disconnect? 
                             // Maybe better to stay and show status.
                        }
                    }

                    if (currentScreen == "scan") {
                        ScanScreen(
                            devices = foundDevices,
                            isConnecting = connectionState == "Connecting...",
                            onScan = {
                                foundDevices.clear()
                                bleManager.scan { device ->
                                    if (!foundDevices.contains(device)) {
                                        foundDevices.add(device)
                                    }
                                }
                            },
                            onConnect = { device ->
                                bleManager.connect(device)
                                // Do not set currentScreen here. Wait for effect.
                            }
                        )
                    } else {
                        DashboardScreen(
                            deviceName = deviceName,
                            connectionState = connectionState,
                            heartRate = heartRate,
                            batteryLevel = batteryLevel,
                            isWorn = isWorn,
                            isCharging = isCharging,
                            accelX = accelX,
                            accelY = accelY,
                            accelZ = accelZ,
                            skinTemp = skinTemp,
                            hrv = hrv,
                            respiratoryRate = respiratoryRate,
                            spo2 = spo2,
                            stressLevel = stressLevel,
                            strain = strain,
                            recovery = recovery,
                            diagnosticLog = diagnosticLog,
                            historicalData = bleManager.historicalData.collectAsState().value,
                            onDownloadHistory = { bleManager.downloadHistory() },
                            onExportLogs = { name, logs ->
                                logsToExport = logs
                                createDocumentLauncher.launch(name.replace(" ", "_") + "_logs.txt")
                            },
                            onClearLogs = { bleManager.clearDiagnosticLog() },
                            onBack = { 
                                currentScreen = "scan"
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScanScreen(
    devices: List<BluetoothDevice>,
    isConnecting: Boolean,
    onScan: () -> Unit,
    onConnect: (BluetoothDevice) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("WHOOP Scanner", style = MaterialTheme.typography.displaySmall)
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(onClick = onScan, enabled = !isConnecting) {
                Text("Scan for WHOOP")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            LazyColumn {
                items(devices) { device ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .clickable(enabled = !isConnecting) { onConnect(device) },
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        colors = if (isConnecting) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f)) else CardDefaults.cardColors()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = device.name ?: "Unknown Device", style = MaterialTheme.typography.titleMedium)
                            Text(text = device.address, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        
        if (isConnecting) {
             CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
             )
        }
    }
}

@Composable
fun DashboardScreen(
    deviceName: String,
    connectionState: String,
    heartRate: Int,
    batteryLevel: Float,
    isWorn: Boolean?,
    isCharging: Boolean?,
    accelX: Int,
    accelY: Int,
    accelZ: Int,
    skinTemp: Float,
    hrv: Int,
    respiratoryRate: Float,
    spo2: Int,
    stressLevel: Float,
    strain: Float,
    recovery: Int,
    diagnosticLog: String,
    historicalData: List<HistoricalPoint>,
    onDownloadHistory: () -> Unit,
    onExportLogs: (String, String) -> Unit,
    onClearLogs: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Bar / Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Text("Back") // Placeholder for Icon
            }
            Column {
                Text("WHOOP Dashboard", style = MaterialTheme.typography.titleLarge)
                Text(deviceName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(text = "Status: $connectionState", style = MaterialTheme.typography.bodyMedium)
        
        Spacer(modifier = Modifier.height(16.dp))

        // Multi-Sensor Info Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SensorBox("Battery", "${batteryLevel.toInt()}%")
            SensorBox("Wrist", if (isWorn == true) "YES" else if (isWorn == false) "NO" else "--")
            SensorBox("Charging", if (isCharging == true) "YES" else if (isCharging == false) "NO" else "--")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Health Metrics Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SensorBox("HRV", if (hrv > 0) "$hrv ms" else "--")
            SensorBox("Skin Temp", if (skinTemp > 0) String.format("%.1f°C", skinTemp) else "--")
            SensorBox("SpO2", if (spo2 > 0) "$spo2%" else "--")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SensorBox("Strain", String.format("%.1f", strain), color = MaterialTheme.colorScheme.error)
            SensorBox("Recovery", if (recovery > 0) "$recovery%" else "--", color = MaterialTheme.colorScheme.primary)
            SensorBox("Stress", String.format("%.1f", stressLevel))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Spacer(modifier = Modifier.height(24.dp))
        
        if (heartRate > 0) {
            Text(text = "$heartRate BPM", style = MaterialTheme.typography.displayLarge)
        } else {
            Text(text = "-- BPM", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.secondary)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Acceleration Display
        Text("Acceleration", style = MaterialTheme.typography.labelSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Text("X: $accelX", style = MaterialTheme.typography.bodySmall)
            Text("Y: $accelY", style = MaterialTheme.typography.bodySmall)
            Text("Z: $accelZ", style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Diagnostic Log
        Text("Diagnostic Log", style = MaterialTheme.typography.labelSmall)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            LazyColumn(modifier = Modifier.padding(8.dp)) {
                item {
                    Text(
                        text = diagnosticLog,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // History & Log Export Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onDownloadHistory,
                modifier = Modifier.weight(1f)
            ) {
                Text("Get History", fontSize = 12.sp)
            }
            Button(
                onClick = { onExportLogs(deviceName, diagnosticLog) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Export Logs", fontSize = 12.sp)
            }
            Button(
                onClick = onClearLogs,
                modifier = Modifier.weight(0.8f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
            ) {
                Text("Clear", fontSize = 12.sp)
            }
        }
        
        if (historicalData.isNotEmpty()) {
            Text("History Points: ${historicalData.size}", style = MaterialTheme.typography.labelSmall)
            LazyColumn(modifier = Modifier.height(100.dp)) {
                items(historicalData.takeLast(50).reversed()) { point ->
                     Text(
                        text = "Time: ${point.timestamp} | HR: ${point.hr}",
                        fontSize = 10.sp
                     )
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    connectionState: String,
    heartRate: Int,
    batteryLevel: Float,
    isWorn: Boolean?,
    isCharging: Boolean?,
    accelX: Int,
    accelY: Int,
    accelZ: Int,
    diagnosticLog: String,
    devices: List<BluetoothDevice>,
    onScan: () -> Unit,
    onConnect: (BluetoothDevice) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Status: $connectionState", style = MaterialTheme.typography.bodyLarge)
        
        Spacer(modifier = Modifier.height(16.dp))

        // Multi-Sensor Info Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Battery", style = MaterialTheme.typography.labelSmall)
                Text("${batteryLevel.toInt()}%", style = MaterialTheme.typography.titleMedium)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Wrist", style = MaterialTheme.typography.labelSmall)
                Text(if (isWorn == true) "YES" else if (isWorn == false) "NO" else "--", style = MaterialTheme.typography.titleMedium)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Charging", style = MaterialTheme.typography.labelSmall)
                Text(if (isCharging == true) "YES" else if (isCharging == false) "NO" else "--", style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        if (heartRate > 0) {
            Text(text = "$heartRate BPM", style = MaterialTheme.typography.displayLarge)
        } else {
            Text(text = "-- BPM", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.secondary)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Acceleration Display
        Text("Acceleration", style = MaterialTheme.typography.labelSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Text("X: $accelX", style = MaterialTheme.typography.bodySmall)
            Text("Y: $accelY", style = MaterialTheme.typography.bodySmall)
            Text("Z: $accelZ", style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Diagnostic Log
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            LazyColumn(modifier = Modifier.padding(8.dp)) {
                item {
                    Text(
                        text = diagnosticLog,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onScan) {
            Text("Scan for WHOOP")
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(devices) { device ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .clickable { onConnect(device) }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = device.name ?: "Unknown Device", style = MaterialTheme.typography.titleMedium)
                        Text(text = device.address, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
@Composable
fun SensorBox(label: String, value: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        Text(value, style = MaterialTheme.typography.titleMedium, color = color)
    }
}
