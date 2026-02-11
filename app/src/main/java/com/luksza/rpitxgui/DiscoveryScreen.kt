package com.luksza.rpitxgui

import android.content.Context
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DiscoveryScreen(onDeviceSelected: (String) -> Unit) {
    val context = LocalContext.current
    val discovery = remember { NmapDiscovery(context) }
    var scanning by remember { mutableStateOf(false) }
    var autoScan by remember { mutableStateOf(false) }  // Toggle continuous
    val devices by discovery.devices.collectAsState()
    val progress by discovery.progress.collectAsState()
    val prefs = remember { context.getSharedPreferences("rpitx", Context.MODE_PRIVATE) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Spacer(modifier = Modifier.height(24.dp))

        // ✅ SCAN STATUS - Always visible
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (scanning || autoScan) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column {
                    Text(
                        if (scanning || autoScan) "🔍 Scanning... ${devices.size} found"
                        else "Ready - ${devices.size} devices",
                        fontWeight = FontWeight.Bold
                    )
                    if (scanning || autoScan && progress > 0) { // Show progress only during scan
                        LinearProgressIndicator(
                            progress = progress / 254f,
                            modifier = Modifier.fillMaxWidth().height(4.dp).padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        Row {
            Button(
                onClick = {
                    scanning = true
                    autoScan = true  // Enable continuous
                    CoroutineScope(Dispatchers.IO).launch {
                        while (autoScan) {
                            try {
                                val subnet = getLocalSubnet(context).substringBeforeLast(".")
                                
                                // ✅ Collect from Flow
                                discovery.scanNetwork(subnet).collect { device ->
                                    if (!autoScan) return@collect // Stop processing if stopped
                                    // Add to list if not already present (based on IP)
                                    val currentList = discovery._devices.value
                                    if (currentList.none { it.ip == device.ip }) {
                                         discovery._devices.value = currentList + device
                                    }
                                }

                                if (!autoScan) break // Exit loop if stopped

                                delay(2000)  // Scan every 2 seconds (after flow completes/closes)
                            } catch (e: Exception) {
                                Log.e("Discovery", "Scan error", e)
                                delay(5000)
                            }
                        }
                        // Reset state when loop exits
                        scanning = false
                        discovery._progress.value = 0
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !scanning && !autoScan
            ) {
                Text("🔍 START SCAN")
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    autoScan = false
                    scanning = false
                    nativeStopScan()
                    discovery._progress.value = 0 // Reset progress UI immediately
                },
                colors = ButtonDefaults.buttonColors(Color.Red),
                modifier = Modifier.weight(1f)
            ) {
                Text("🛑 STOP")
            }
        }

        QuickConnect(onDeviceSelected)

        // ✅ DEVICES ALWAYS VISIBLE + CLICKABLE
        if (devices.isNotEmpty()) {
            LazyColumn {
                items(devices) { device ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDeviceSelected(device.ip)
                                prefs.edit().putString("last_ip", device.ip).apply()
                            }
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (device.isRaspberryPi)
                                Color.Green.copy(alpha = 0.2f) else Color.Unspecified
                        )
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (device.isRaspberryPi) {
                                Icon(Icons.Default.Check, "Pi", tint = Color.Green, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(device.hostname, fontWeight = FontWeight.Bold)
                                Text(device.ip, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

