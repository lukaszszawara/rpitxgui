package com.luksza.rpitxgui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun QuickConnect(
    onDeviceSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("rpitx", Context.MODE_PRIVATE) }

    var lastIp by remember {
        mutableStateOf(prefs.getString("last_ip", "192.168.1.100") ?: "192.168.1.100")
    }
    var customIp by remember { mutableStateOf(lastIp) }

    ElevatedCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Quick Connect",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = customIp,
                onValueChange = { customIp = it },
                label = { Text("IP Address") },
                leadingIcon = {
                    Icon(Icons.Default.Home, contentDescription = null)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        prefs.edit().putString("last_ip", customIp).apply()
                        lastIp = customIp
                        onDeviceSelected(customIp)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Connect")
                }

                FilledTonalButton(
                    onClick = { customIp = lastIp },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(lastIp, maxLines = 1)
                }
            }
        }
    }
}
