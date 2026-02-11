package com.luksza.rpitxgui

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import android.util.Log

class NmapDiscovery(private val context: Context) {
    val _devices = MutableStateFlow<List<NmapDevice>>(emptyList())
    val devices: StateFlow<List<NmapDevice>> = _devices

    val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress

    data class NmapDevice(
        val ip: String,
        val hostname: String,
        val ports: String,
        val isRaspberryPi: Boolean
    )

    fun scanNetwork(subnet: String): Flow<NmapDevice> = callbackFlow {
        _progress.value = 0 // Reset progress on start
        val callback = object : ScanCallback {
            override fun onDeviceFound(ip: String, hostname: String, ports: String) {
                val isPi = hostname.contains("raspberry", true) ||
                        hostname.contains("pi", true)
                trySend(NmapDevice(ip, hostname, ports, isPi))
            }

            override fun onProgress(progress: Int) {
                _progress.value = progress
            }
        }

        withContext(Dispatchers.IO) {
            try {
                nativeNmapScan(subnet + "/24", callback)
            } catch (e: Exception) {
                Log.e("Nmap", "Scan failed", e)
            } finally {
                close()
            }
        }
        awaitClose { nativeStopScan() }
    }

    interface ScanCallback {
        fun onDeviceFound(ip: String, hostname: String, ports: String)
        fun onProgress(progress: Int)
    }

    private external fun nativeNmapScan(target: String, callback: ScanCallback)

    companion object {
        init {
            System.loadLibrary("native-lib")
        }
    }
}


