package com.luksza.rpitxgui

import android.content.Context
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Home

import androidx.compose.material.icons.filled.Search

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DiscoveryScreen(
    modifier: Modifier = Modifier,
    onDeviceSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val discovery = remember { NmapDiscovery(context) }
    var scanning by remember { mutableStateOf(false) }
    var autoScan by remember { mutableStateOf(false) }
    val devices by discovery.devices.collectAsState()
    val progress by discovery.progress.collectAsState()
    val prefs = remember { context.getSharedPreferences("rpitx", Context.MODE_PRIVATE) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // ── Scan status card ─────────────────────────────────────────────────
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (scanning || autoScan) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (scanning || autoScan) "Scanning…  ${devices.size} found"
                               else "Ready  ·  ${devices.size} device(s)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (scanning || (autoScan && progress > 0)) {
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progress / 254f },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }

        // ── Scan controls ─────────────────────────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    scanning = true
                    autoScan = true
                    CoroutineScope(Dispatchers.IO).launch {
                        while (autoScan) {
                            try {
                                val subnet = getLocalSubnet(context).substringBeforeLast(".")
                                discovery.scanNetwork(subnet).collect { device ->
                                    if (!autoScan) return@collect
                                    val currentList = discovery._devices.value
                                    if (currentList.none { it.ip == device.ip }) {
                                        discovery._devices.value = currentList + device
                                    }
                                }
                                if (!autoScan) break
                                delay(2000)
                            } catch (e: Exception) {
                                Log.e("Discovery", "Scan error", e)
                                delay(5000)
                            }
                        }
                        scanning = false
                        discovery._progress.value = 0
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !scanning && !autoScan
            ) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Start Scan")
            }

            FilledTonalButton(
                onClick = {
                    autoScan = false
                    scanning = false
                    nativeStopScan()
                    discovery._progress.value = 0
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Stop")
            }
        }

        // ── Quick Connect ─────────────────────────────────────────────────────
        QuickConnect(onDeviceSelected)

        // ── Device list ───────────────────────────────────────────────────────
        if (devices.isNotEmpty()) {
            Text(
                "Found devices",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(devices) { device ->
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDeviceSelected(device.ip)
                                prefs.edit().putString("last_ip", device.ip).apply()
                            },
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = if (device.isRaspberryPi)
                                MaterialTheme.colorScheme.tertiaryContainer
                            else
                                MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (device.isRaspberryPi) Icons.Default.CheckCircle else Icons.Default.Home,
                                contentDescription = null,
                                tint = if (device.isRaspberryPi)
                                    MaterialTheme.colorScheme.tertiary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(device.hostname, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(device.ip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
