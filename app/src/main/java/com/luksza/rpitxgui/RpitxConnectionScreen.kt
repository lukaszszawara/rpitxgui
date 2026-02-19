package com.luksza.rpitxgui

import SshManager
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RpitxConnectionScreen(
    host: String,
    modifier: Modifier = Modifier,
    onBackToDiscovery: () -> Unit
) {
    var sshManager by remember { mutableStateOf<SshManager?>(null) }
    var frequency by remember { mutableStateOf("434.0") }
    var mode by remember { mutableStateOf("WFM") }

    if (sshManager == null) {
        RpitxLoginForm(
            host = host,
            modifier = modifier,
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
            modifier = modifier,
            onFrequencyChange = { frequency = it },
            onModeChange = { mode = it }
        )
    }
}

@Composable
fun RpitxLoginForm(
    host: String,
    modifier: Modifier = Modifier,
    onBackToDiscovery: () -> Unit,
    onConnected: (SshManager, String, String) -> Unit
) {
    val context = LocalContext.current

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

    var username by remember {
        mutableStateOf(encryptedPrefs.getString("username", "pi") ?: "pi")
    }
    var password by remember {
        mutableStateOf(encryptedPrefs.getString("password", "raspberry") ?: "raspberry")
    }
    var isConnecting by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("SSH Connection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(host, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                HorizontalDivider()

                // Credentials
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Error
                connectionError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Connect button
                Button(
                    onClick = {
                        isConnecting = true
                        connectionError = null

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
                                    connectionError = "Connection failed — check host and credentials"
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isConnecting
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connecting…")
                    } else {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connect & Save")
                    }
                }
            }
        }
    }
}
