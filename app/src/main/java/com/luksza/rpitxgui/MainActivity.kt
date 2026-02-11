package com.luksza.rpitxgui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.luksza.rpitxgui.ui.theme.RpitxguiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RpitxguiTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RpitxApp(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun RpitxApp(modifier: Modifier = Modifier) {
    var showDiscovery by remember { mutableStateOf(true) }
    var selectedHost by remember { mutableStateOf("") }

    BackHandler {
        showDiscovery = true
        selectedHost = ""
    }

    if (showDiscovery && selectedHost.isEmpty()) {
        DiscoveryScreen { ip ->
            selectedHost = ip
            showDiscovery = false
        }
    } else {
        RpitxConnectionScreen(
            host = selectedHost,
            onBackToDiscovery = {
                showDiscovery = true
                selectedHost = ""
            }
        )
    }
}

internal external fun nativeStopScan()
