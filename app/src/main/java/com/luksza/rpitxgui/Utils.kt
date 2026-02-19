package com.luksza.rpitxgui

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

internal fun getLocalSubnet(context: Context): String {
    return try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return "192.168.1"
        val caps = cm.getNetworkCapabilities(network) ?: return "192.168.1"
        val props = cm.getLinkProperties(network)
        props?.linkAddresses?.firstOrNull()?.address?.hostAddress ?: "192.168.1"
    } catch (e: Exception) { "192.168.1" }
}

internal fun getModeDescription(mode: String): String = when (mode) {
    "Tune" -> "Carrier"
    "Chirp" -> "Moving carrier"
    "Spectrum" -> "Spectrum painting"
    "RfMyFace" -> "Snap with Raspicam"
    "FmRds" -> "Broadcast + RDS"
    "NFM" -> "Narrow FM"
    "SSB" -> "Side Band"
    "AM" -> "Amplitude Mod"
    "FreeDV" -> "Digital voice"
    "SSTV" -> "Picture"
    "Pocsag" -> "Pager message"
    "Opera" -> "Weak signal"
    "RTTY" -> "Radioteletype"
    else -> ""
}

internal fun getModeIcon(mode: String): ImageVector = when (mode) {
    "Tune" -> Icons.Default.Info
    "Chirp" -> Icons.Default.KeyboardArrowUp
    "Spectrum" -> Icons.Default.AccountBox
    "RfMyFace" -> Icons.Default.Info
    "FmRds" -> Icons.Default.Notifications
    "NFM" -> Icons.Default.Notifications
    "SSB" -> Icons.Default.Notifications
    "AM" -> Icons.Default.Notifications
    "FreeDV" -> Icons.Default.PlayArrow
    "SSTV" -> Icons.Default.AccountBox
    "Pocsag" -> Icons.Default.MailOutline
    "Opera" -> Icons.Default.Email
    "RTTY" -> Icons.Default.MailOutline
    else -> Icons.Default.Close
}

@RequiresApi(Build.VERSION_CODES.P)
internal suspend fun convertUriToWavFile(context: Context, uri: Uri): File {
    val inputStream = context.contentResolver.openInputStream(uri)!!
    val tempFile = File(context.cacheDir, "temp_audio.wav")

    // Copy file (convert if needed)
    inputStream.copyTo(tempFile.outputStream())
    inputStream.close()

    return tempFile
}

private var currentRecorder: MediaRecorder? = null

internal fun recordAudioWhilePressed(
    activity: Activity,
    onRecorded: (File) -> Unit
): MediaRecorder? {
    return try {
        val outputFile = File(activity.cacheDir, "fmrds_${System.currentTimeMillis()}.m4a")
        val recorder = MediaRecorder(activity).apply {
            currentRecorder = this


            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }
        recorder
    } catch (e: Exception) {
        null
    }
}

private fun stopRecording() {
    currentRecorder?.let { recorder ->
        try {
            recorder.stop()
            recorder.release()
        } catch (e: Exception) {
            // Handle partial recording
        }
        currentRecorder = null
    }
}


internal fun recordAudio(context: Context, onRecorded: (File) -> Unit) {
    val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            val outputFile = File(context.cacheDir, "fmrds_${System.currentTimeMillis()}.wav")
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()

            // ✅ Auto-stop after 10 seconds
            CoroutineScope(Dispatchers.IO).launch {
                delay(10000)
                stop()
                release()
                onRecorded(outputFile)
            }
        }
    } else {
        TODO("VERSION.SDK_INT < S")
    }
}

@RequiresApi(Build.VERSION_CODES.P)
internal suspend fun resizeImage(
    context: Context,
    uri: Uri,
    width: Int,
    height: Int
): File = withContext(Dispatchers.IO) {
    val source = ImageDecoder.createSource(context.contentResolver, uri)

    // ✅ FIXED: 3 parameters (decoder, info, source)
    val bitmap = ImageDecoder.decodeBitmap(source, ImageDecoder.OnHeaderDecodedListener { decoder, info, source ->
        decoder.isMutableRequired = true  // ✅ Make mutable
    })

    // ✅ Copy to SOFTWARE (supports getPixel)
    val softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    bitmap.recycle()

    // ✅ Resize + copy to SOFTWARE again
    val resized = Bitmap.createScaledBitmap(softwareBitmap, width, height, true)
    val finalSoftware = resized.copy(Bitmap.Config.ARGB_8888, true)
    softwareBitmap.recycle()
    resized.recycle()

    // ✅ Convert to B&W
    val bwBitmap = toBlackWhiteHighContrast(finalSoftware)
    finalSoftware.recycle()

    // ✅ Save
    val outputFile = File(context.cacheDir, "spectrum_${System.currentTimeMillis()}.jpg")
    FileOutputStream(outputFile).use { out ->
        bwBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
    }
    bwBitmap.recycle()

    outputFile
}

internal fun toBlackWhiteHighContrast(bitmap: Bitmap): Bitmap {
    val width = bitmap.width
    val height = bitmap.height


    val bwBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    for (x in 0 until width) {
        for (y in 0 until height) {
            val pixel = bitmap.getPixel(x, y)

            // ✅ DIRECT pixel extraction - NO Color import needed
            val alpha = (pixel shr 24) and 0xFF
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF

            // ✅ Grayscale conversion (luma)
            val gray = (0.299f * red + 0.587f * green + 0.114f * blue).toInt()

            // ✅ HIGH CONTRAST: <128 → BLACK, >=128 → WHITE
            val bwValue = if (gray < 128) 0 else 255
            val bwColor = (alpha shl 24) or (bwValue shl 16) or (bwValue shl 8) or bwValue
            bwBitmap.setPixel(x, y, bwColor)
        }
    }
    return bwBitmap
}
