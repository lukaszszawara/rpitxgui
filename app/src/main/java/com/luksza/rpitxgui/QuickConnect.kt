package com.luksza.rpitxgui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
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

    // ✅ PERSISTENT: Survives app restarts!
    var lastIp by remember {
        mutableStateOf(prefs.getString("last_ip", "192.168.1.100") ?: "192.168.1.100")
    }
    var customIp by remember { mutableStateOf(lastIp) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Quick Connect",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = customIp,
                onValueChange = { customIp = it },
                label = { Text("IP Address") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row {
                Button(
                    onClick = {
                        // ✅ SAVE PERMANENTLY to SharedPreferences
                        prefs.edit().putString("last_ip", customIp).apply()
                        lastIp = customIp
                        onDeviceSelected(customIp)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("CONNECT")
                }

                Spacer(modifier = Modifier.width(8.dp))

                TextButton(
                    onClick = { customIp = lastIp },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Last: $lastIp")
                }
            }
        }
    }
}
