package com.organizador.scanner

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private const val PC_BASE_URL = "http://127.0.0.1:8765"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ScannerApp() }
    }
}

@Composable
private fun ScannerApp() {
    val colors = darkColorScheme(
        primary = Color(0xFF1677FF),
        background = Color(0xFF061321),
        surface = Color(0xFF10243A)
    )
    MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF061321)) {
            ScannerScreen()
        }
    }
}

@Composable
private fun ScannerScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var reading by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Conecte o cabo USB e abra o Organizador de Estoque no computador.") }
    var statusOk by remember { mutableStateOf<Boolean?>(null) }
    var lastCode by remember { mutableStateOf<String?>(null) }

    val tone = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 75) }
    DisposableEffect(Unit) { onDispose { tone.release() } }

    val options = remember {
        GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39
            )
            .enableAutoZoom()
            .build()
    }
    val scanner = remember(context, options) { GmsBarcodeScanning.getClient(context, options) }

    fun sendBarcode(code: String) {
        lastCode = code
        sending = true
        status = "Enviando $code para o computador..."
        statusOk = null
        scope.launch {
            val result = sendToPc(code)
            sending = false
            if (result) {
                tone.startTone(ToneGenerator.TONE_PROP_ACK, 120)
                status = "Enviado para o computador ✓"
                statusOk = true
            } else {
                tone.startTone(ToneGenerator.TONE_PROP_NACK, 180)
                status = "Computador não conectado. Confira o cabo USB e a Depuração USB."
                statusOk = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "SCANNER\nORGANIZADOR DE ESTOQUE",
            fontSize = 26.sp,
            lineHeight = 31.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Use a câmera do celular como leitor do programa no computador.",
            color = Color(0xFF9FB0C4),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF10243A)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Último código", color = Color(0xFF9FB0C4), fontSize = 13.sp)
                Text(lastCode ?: "—", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text(
                    status,
                    color = when (statusOk) {
                        true -> Color(0xFF20C983)
                        false -> Color(0xFFFF6B6B)
                        null -> Color(0xFFFFC857)
                    },
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                if (reading || sending) return@Button
                reading = true
                status = "Abrindo câmera..."
                statusOk = null
                scanner.startScan()
                    .addOnSuccessListener { barcode ->
                        barcode.rawValue?.trim()?.takeIf { it.isNotEmpty() }?.let(::sendBarcode)
                    }
                    .addOnFailureListener {
                        status = "Não foi possível abrir o leitor. Tente novamente."
                        statusOk = false
                    }
                    .addOnCompleteListener { reading = false }
            },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            enabled = !reading && !sending,
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(
                when {
                    reading -> "ABRINDO LEITOR..."
                    sending -> "ENVIANDO..."
                    else -> "▣  BIPAR PRODUTO"
                },
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = {
                status = "Testando conexão USB..."
                statusOk = null
                scope.launch {
                    val ok = pingPc()
                    statusOk = ok
                    status = if (ok) "Computador conectado ✓" else "Computador não conectado."
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("TESTAR CONEXÃO USB")
        }

        Spacer(Modifier.height(18.dp))
        Text(
            "No primeiro uso: ative Opções do desenvolvedor > Depuração USB e aceite a autorização deste computador.",
            color = Color(0xFF7F91A5),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

private suspend fun pingPc(): Boolean = withContext(Dispatchers.IO) {
    request("GET", "$PC_BASE_URL/ping", null)
}

private suspend fun sendToPc(code: String): Boolean = withContext(Dispatchers.IO) {
    request("POST", "$PC_BASE_URL/scan", code)
}

private fun request(method: String, address: String, body: String?): Boolean {
    return runCatching {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 1800
            readTimeout = 1800
            useCaches = false
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            }
        }
        if (body != null) {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val ok = connection.responseCode in 200..299
        connection.disconnect()
        ok
    }.getOrDefault(false)
}
