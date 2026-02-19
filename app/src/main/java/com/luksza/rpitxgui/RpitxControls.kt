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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear

import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// Reusable section-card header
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@SuppressLint("NewApi")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RpitxControls(
    sshManager: SshManager,
    frequency: String,
    mode: String,
    modifier: Modifier = Modifier,
    onFrequencyChange: (String) -> Unit,
    onModeChange: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var customCommand by remember { mutableStateOf("df -h") }
    var sshResponse   by remember { mutableStateOf("") }
    var isExecuting   by remember { mutableStateOf(false) }

    var filePath        by remember { mutableStateOf("") }
    var pocslagMessage  by remember { mutableStateOf("1:TEST\n2:THIS IS THE FORM") }
    var operaCallsign   by remember { mutableStateOf("F5OEO") }
    var rttyMessage     by remember { mutableStateOf("HELLO WORLD FROM RPITX") }
    var spectrumImageUri by remember { mutableStateOf<Uri?>(null) }
    var nfmAudioUri      by remember { mutableStateOf<Uri?>(null) }
    var ssbAudioUri      by remember { mutableStateOf<Uri?>(null) }
    var amAudioUri       by remember { mutableStateOf<Uri?>(null) }
    var fmrDSAudioUri    by remember { mutableStateOf<Uri?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> spectrumImageUri = uri }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        when (mode) {
            "FmRds" -> fmrDSAudioUri = uri
            "NFM"   -> nfmAudioUri   = uri
            "SSB"   -> ssbAudioUri   = uri
            "AM"    -> amAudioUri    = uri
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
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // ── 1. FREQUENCY ─────────────────────────────────────────────────────
        SectionCard(title = "Frequency") {
            var textValue by remember(frequency) { mutableStateOf(frequency) }

            OutlinedTextField(
                value = textValue,
                onValueChange = { newValue ->
                    // 1. Update local text state immediately to allow typing
                    textValue = newValue.filter { it.isDigit() || it == '.' }
                    
                    // 2. Try to update external state only if it's a valid frequency
                    val floatValue = textValue.toFloatOrNull()
                    if (floatValue != null && floatValue in 0.05f..1500f) {
                        onFrequencyChange(textValue)
                    }
                },
                label = { Text("Frequency (MHz)") },
                placeholder = { Text("e.g. 434.0") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                suffix = { Text("MHz") }
            )
        }

        // ── 2. MODES GRID ─────────────────────────────────────────────────────
        SectionCard(title = "Mode") {
            val modes = listOf(
                "Tune", "Chirp", "Spectrum", "FmRds",
                "NFM", "SSB", "AM", "FreeDV", "SSTV",
                "Pocsag", "Opera", "RTTY"
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.height(360.dp),
                verticalArrangement   = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(modes) { _, m ->
                    FilterChip(
                        selected = mode == m,
                        onClick  = { onModeChange(m) },
                        leadingIcon = {
                            Icon(
                                getModeIcon(m),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
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

        // ── 3. CUSTOM SSH COMMAND ─────────────────────────────────────────────
        SectionCard(title = "SSH Terminal") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = customCommand,
                    onValueChange = { customCommand = it },
                    label = { Text("Command") },
                    placeholder = { Text("df -h, uptime, ps aux…") },
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    singleLine = false
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            isExecuting = true
                            sshResponse = ""
                            try {
                                sshResponse = sshManager.execute(customCommand)
                            } catch (e: Exception) {
                                sshResponse = "Error: ${e.message}"
                            } finally {
                                isExecuting = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isExecuting
                ) {
                    if (isExecuting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Running…")
                    } else {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Execute")
                    }
                }

                if (sshResponse.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = { sshResponse = "" }
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                    }
                }
            }

            if (sshResponse.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 2.dp
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // ── 4. MODE-SPECIFIC INPUTS ───────────────────────────────────────────
        when (mode) {
            "Pocsag" -> SectionCard(title = "POCSAG Message") {
                OutlinedTextField(
                    value = pocslagMessage,
                    onValueChange = { pocslagMessage = it },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }

            "Opera" -> SectionCard(title = "Callsign") {
                OutlinedTextField(
                    value = operaCallsign,
                    onValueChange = { operaCallsign = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            "RTTY" -> SectionCard(title = "RTTY Message") {
                OutlinedTextField(
                    value = rttyMessage,
                    onValueChange = { rttyMessage = it },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }

            "Spectrum", "SSTV" -> SectionCard(title = "Image") {
                Button(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (spectrumImageUri == null) "Pick image" else "Image ready")
                }
                spectrumImageUri?.let { uri ->
                    Text(
                        "Selected: ${uri.lastPathSegment}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    coroutineScope.launch {
                        val resizedFile = resizeImage(context, uri, 320, 240)
                        filePath = "/tmp/RPITX_IMAGE.jpg"
                        sshManager.uploadFile(resizedFile, filePath)
                    }
                }
            }

            "FmRds", "NFM", "SSB", "AM" -> SectionCard(title = "Audio") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { audioRecorderLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Record 10s")
                    }
                    Button(
                        onClick = { audioPickerLauncher.launch("audio/*;wav/*") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        val currentUri = when (mode) {
                            "FmRds" -> fmrDSAudioUri
                            "NFM"   -> nfmAudioUri
                            "SSB"   -> ssbAudioUri
                            "AM"    -> amAudioUri
                            else    -> null
                        }
                        Text(if (currentUri != null) "Audio ready" else "Pick WAV")
                    }
                }
                val currentUri = when (mode) {
                    "FmRds" -> fmrDSAudioUri
                    "NFM"   -> nfmAudioUri
                    "SSB"   -> ssbAudioUri
                    "AM"    -> amAudioUri
                    else    -> null
                }
                currentUri?.let { uri ->
                    coroutineScope.launch {
                        val resizedFile = convertUriToWavFile(context, uri)
                        filePath = "/tmp/RPITX_AUDIO.wav"
                        sshManager.uploadFile(resizedFile, filePath)
                    }
                }
            }
        }

        // ── 5. TRANSMIT BUTTON ────────────────────────────────────────────────
        Spacer(modifier = Modifier.height(4.dp))
        Button(
            onClick = {
                Log.i("SshManager", "execute mode $mode")
                val cmd = when (mode) {
                    "Tune"     -> "testvfo.sh ${frequency}e6"
                    "Chirp"    -> "testchirp.sh ${frequency}e6"
                    "RfMyFace" -> "snap2spectrum.sh ${frequency}e6"
                    "Spectrum" -> "testspectrum.sh ${frequency}e6 \"$filePath\""
                    "Pocsag"   -> "testpocsag.sh ${frequency}e6 \"$pocslagMessage\""
                    "Opera"    -> "testopera.sh ${frequency}e6 $operaCallsign"
                    "RTTY"     -> "testrtty.sh ${frequency}e6 \"$rttyMessage\""
                    "NFM"      -> "testnfm.sh ${frequency}e6 \"$filePath\""
                    "FmRds"    -> "testfmrds.sh ${frequency}e6 \"$filePath\""
                    "SSB"      -> "testssb.sh ${frequency}e6 \"$filePath\""
                    "AM"       -> "testam.sh ${frequency}e6 \"$filePath\""
                    "SSTV"     -> "testsstv.sh ${frequency}e6 \"$filePath\""
                    else       -> "${mode.lowercase()}.sh ${frequency}e6"
                }
                coroutineScope.launch {
                    sshManager.execute("cd ~/rpitx && sudo timeout 5s ./$cmd")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor   = MaterialTheme.colorScheme.onError
            )
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "TRANSMIT  ·  $mode  ·  $frequency MHz",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
