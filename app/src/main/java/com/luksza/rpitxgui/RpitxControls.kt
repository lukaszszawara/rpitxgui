package com.luksza.rpitxgui

import SshManager
import android.Manifest
import android.annotation.SuppressLint
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@SuppressLint("NewApi")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RpitxControls(
    sshManager: SshManager,
    frequency: String,
    mode: String,
    onFrequencyChange: (String) -> Unit,
    onModeChange: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // ✅ NEW: Custom SSH Command State
    var customCommand by remember { mutableStateOf("df -h") }
    var sshResponse by remember { mutableStateOf("") }
    var isExecuting by remember { mutableStateOf(false) }

    // Existing state
    var filePath by remember { mutableStateOf("") }
    var pocslagMessage by remember { mutableStateOf("1:TEST\n2:THIS IS THE FORM") }
    var operaCallsign by remember { mutableStateOf("F5OEO") }
    var rttyMessage by remember { mutableStateOf("HELLO WORLD FROM RPITX") }
    var spectrumImageUri by remember { mutableStateOf<Uri?>(null) }
    var nfmAudioUri by remember { mutableStateOf<Uri?>(null) }
    var ssbAudioUri by remember { mutableStateOf<Uri?>(null) }
    var amAudioUri by remember { mutableStateOf<Uri?>(null) }
    var fmrDSAudioUri by remember { mutableStateOf<Uri?>(null) }

    // Launchers
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        spectrumImageUri = uri
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        when (mode) {
            "FmRds" -> fmrDSAudioUri = uri
            "NFM" -> nfmAudioUri = uri
            "SSB" -> ssbAudioUri = uri
            "AM" -> amAudioUri = uri
        }
    }

    val audioRecorderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            recordAudio(context) { recordedFile ->
                fmrDSAudioUri = Uri.fromFile(recordedFile)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 1. FREQUENCY
        Spacer(modifier = Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Frequency",
                    fontWeight = FontWeight.Thin,
                    style = MaterialTheme.typography.headlineSmall
                )

                // Toggle between Slider and TextInput modes
                var useTextInput by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Current: $frequency MHz", style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = { useTextInput = !useTextInput }) {
                        Text(if (useTextInput) "Use Slider" else "Use Keyboard")
                    }
                }

                if (useTextInput) {
                    // Text input mode - works with Android keyboard
                    OutlinedTextField(
                        value = frequency,
                        onValueChange = { newValue ->
                            // Validate and format input (only numbers and decimal)
                            val cleanValue = newValue.filter { it.isDigit() || it == '.' }
                            if (cleanValue.toFloat() in 0.05f..1500f) {
                                onFrequencyChange(String.format("%.1f", cleanValue.toFloat()))
                            }
                        },
                        label = { Text("Frequency (MHz)") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // Slider mode - fixed responsiveness issue
                    var sliderValue by remember(frequency) {
                        mutableFloatStateOf(frequency.toFloatOrNull() ?: 434f)
                    }

                    Slider(
                        value = sliderValue,
                        onValueChange = { newValue ->
                            sliderValue = newValue
                        },
                        onValueChangeFinished = {
                            // Only call callback when user finishes dragging
                            onFrequencyChange(String.format("%.1f", sliderValue))
                        },
                        valueRange = 0.05f..1500f,
                        steps = 14995,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }


        Spacer(modifier = Modifier.height(16.dp))

        // 2. MODES GRID
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "📡 Modes",
                    fontWeight = FontWeight.Thin,
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf(
                        "Tune", "Chirp", "Spectrum", "FmRds",
                        "NFM", "SSB", "AM", "FreeDV", "SSTV",
                        "Pocsag", "Opera", "RTTY"
                    )
                    itemsIndexed(modes) { _, m ->
                        FilterChip(
                            selected = mode == m,
                            onClick = { onModeChange(m) },
                            leadingIcon = {
                                Icon(
                                    getModeIcon(m),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            label = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = m,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = getModeDescription(m),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ✅ 3. NEW: CUSTOM SSH COMMAND
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "🛠️ Custom SSH Command",
                    fontWeight = FontWeight.Thin,
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = customCommand,
                    onValueChange = { customCommand = it },
                    label = { Text("Command") },
                    placeholder = { Text("df -h, uptime, ps aux, ls ~/rpitx") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    singleLine = false
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row {
                    Button(
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                            isExecuting = true
                                sshResponse = ""

                                try {
                                    sshResponse = sshManager.execute("$customCommand")
                                } catch (e: Exception) {
                                    sshResponse = "❌ Error: ${e.message}"
                                } finally {
                                    isExecuting = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isExecuting
                    ) {
                        if (isExecuting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Executing...")
                        } else {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("EXECUTE")
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    TextButton(
                        onClick = { sshResponse = "" },
                        enabled = sshResponse.isNotEmpty()
                    ) {
                        Text("Clear")
                    }
                }

                // SSH RESPONSE DISPLAY
                if (sshResponse.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        ) {
                            item {
                                Text(
                                    text = sshResponse,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. MODE-SPECIFIC INPUTS
        when (mode) {
            "Pocsag" -> Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📨 POCSAG Message", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = pocslagMessage,
                        onValueChange = { pocslagMessage = it },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            }
            "Opera" -> Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📡 Callsign", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = operaCallsign,
                        onValueChange = { operaCallsign = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            "RTTY" -> Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("RTTY Message", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = rttyMessage,
                        onValueChange = { rttyMessage = it },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            }
            "Spectrum", "SSTV" -> Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Image", fontWeight = FontWeight.Bold)
                    Button(onClick = { imagePickerLauncher.launch("image/*") }) {
                        Text(if (spectrumImageUri == null) "📸 Pick image" else "✅ Image Ready")
                    }
                    spectrumImageUri?.let { uri ->
                        Text(
                            "Selected: ${uri.lastPathSegment}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        coroutineScope.launch {
                            val resizedFile = resizeImage(context, uri, 320, 240)
                            filePath = "/tmp/RPITX_IMAGE.jpg"
                            sshManager.uploadFile(resizedFile, filePath)
                        }
                    }
                }
            }
            "FmRds", "NFM", "SSB", "AM" -> Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Audio", fontWeight = FontWeight.Bold)
                    Button(onClick = {
                        audioRecorderLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }) {
                        Text("Record Message (10s)")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { audioPickerLauncher.launch("audio/*;wav/*") }) {
                        val currentUri = when (mode) {
                            "FmRds" -> fmrDSAudioUri
                            "NFM" -> nfmAudioUri
                            "SSB" -> ssbAudioUri
                            "AM" -> amAudioUri
                            else -> null
                        }
                        Text(currentUri?.let { "Audio Ready" } ?: "Pick Mono WAV")

                    }

                    val currentUri = when (mode) {
                        "FmRds" -> fmrDSAudioUri
                        "NFM" -> nfmAudioUri
                        "SSB" -> ssbAudioUri
                        "AM" -> amAudioUri
                        else -> null
                    }
                    currentUri?.let { uri ->
                        coroutineScope.launch {
                            val resizedFile = convertUriToWavFile(context,uri)
                            filePath = "/tmp/RPITX_AUDIO.wav"
                            sshManager.uploadFile(resizedFile, filePath)
                        }
                    }

                }

            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 5. TRANSMIT BUTTON
        Button(
            onClick = {
                Log.i("SshManager", "execute mode"+mode)
                val cmd = when (mode) {
                    "Tune" -> "testvfo.sh ${frequency}e6"
                    "Chirp" -> "testchirp.sh ${frequency}e6"
                    "RfMyFace" -> "snap2spectrum.sh ${frequency}e6"
                    "Spectrum" -> "testspectrum.sh ${frequency}e6 \"$filePath\""
                    "Pocsag" -> "testpocsag.sh ${frequency}e6 \"$pocslagMessage\""
                    "Opera" -> "testopera.sh ${frequency}e6 $operaCallsign"
                    "RTTY" -> "testrtty.sh ${frequency}e6 \"$rttyMessage\""
                    "NFM" -> "testnfm.sh ${frequency}e6 \"$filePath\""
                    "FmRds" -> "testfmrds.sh ${frequency}e6 \"$filePath\""
                    "SSB" -> "testssb.sh ${frequency}e6 \"$filePath\""
                    "AM" -> "testam.sh ${frequency}e6 \"$filePath\""
                    "SSTV" -> "testsstv.sh ${frequency}e6 \"$filePath\""
                    else -> "${mode.lowercase()}.sh ${frequency}e6"
                }
                coroutineScope.launch {
                    sshManager.execute("cd ~/rpitx && sudo timeout 5s ./$cmd")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("TRANSMIT $mode", fontWeight = FontWeight.Bold)
        }
    }
}
