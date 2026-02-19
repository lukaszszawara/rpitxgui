package com.luksza.rpitxgui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.luksza.rpitxgui.ui.theme.RpitxguiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RpitxguiTheme {
                RpitxApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RpitxApp() {
    var showDiscovery by remember { mutableStateOf(true) }
    var selectedHost by remember { mutableStateOf("") }

    BackHandler {
        showDiscovery = true
        selectedHost = ""
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (showDiscovery || selectedHost.isEmpty()) "rpitx" else selectedHost,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "rpitx",
                        tint = MaterialTheme.colorScheme.primary,

                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        if (showDiscovery && selectedHost.isEmpty()) {
            DiscoveryScreen(modifier = Modifier.padding(innerPadding)) { ip ->
                selectedHost = ip
                showDiscovery = false
            }
        } else {
            RpitxConnectionScreen(
                host = selectedHost,
                modifier = Modifier.padding(innerPadding),
                onBackToDiscovery = {
                    showDiscovery = true
                    selectedHost = ""
                }
            )
        }
    }
}

external fun nativeStopScan()
