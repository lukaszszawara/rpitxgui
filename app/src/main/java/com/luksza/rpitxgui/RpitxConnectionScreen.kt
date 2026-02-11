package com.luksza.rpitxgui

import SshManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RpitxConnectionScreen(
    host: String,
    onBackToDiscovery: () -> Unit
) {
    var sshManager by remember { mutableStateOf<SshManager?>(null) }
    var frequency by remember { mutableStateOf("434.0") }
    var mode by remember { mutableStateOf("WFM") }

    if (sshManager == null) {
        RpitxConnectionScreen(
            host = host,
            onBackToDiscovery = onBackToDiscovery,
            onConnected = { manager, freq, m ->
                sshManager = manager
                frequency = freq
                mode = m
            }
        )
    } else {
        RpitxControls(
            sshManager = sshManager!!,
            frequency = frequency,
            mode = mode,
            onFrequencyChange = { frequency = it },
            onModeChange = { mode = it }
        )
    }
}


@Composable
fun RpitxConnectionScreen(
    host: String,
    onBackToDiscovery: () -> Unit,
    onConnected: (SshManager, String, String) -> Unit
) {
    val context = LocalContext.current

    // ✅ ENCRYPTED SharedPreferences
    val masterKey = remember {
        androidx.security.crypto.MasterKey.Builder(context)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    val encryptedPrefs = remember {
        androidx.security.crypto.EncryptedSharedPreferences.create(
            context,
            "ssh_credentials",
            masterKey,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ✅ AUTO-LOAD saved credentials
    var username by remember {
        mutableStateOf(encryptedPrefs.getString("username", "pi") ?: "pi")
    }
    var password by remember {
        mutableStateOf(encryptedPrefs.getString("password", "raspberry") ?: "raspberry")
    }
    var isConnecting by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        
        Spacer(modifier = Modifier.height(42.dp))
        // ✅ Login Form - Pre-filled from encrypted storage
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )



        Button(
            onClick = {
                isConnecting = true
                connectionError = null

                // ✅ SAVE credentials ENCRYPTED
                encryptedPrefs.edit().apply {
                    putString("username", username)
                    putString("password", password)
                    apply()
                }

                CoroutineScope(Dispatchers.IO).launch {
                    val manager = SshManager()
                    val success = manager.connect(host, username, password)

                    withContext(Dispatchers.Main) {
                        isConnecting = false
                        if (success) {
                            onConnected(manager, "434.0", "WFM")
                        } else {
                            connectionError = "Connection failed"
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isConnecting
        ) {
            if (isConnecting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Connecting...")
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                Text("CONNECT & SAVE")
            }
        }

        connectionError?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(it, color = Color.Red)
        }
    }
}
