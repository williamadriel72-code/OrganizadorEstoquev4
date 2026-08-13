package com.organizador.estoque.ui

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.android.gms.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode

@Composable
fun BarcodeCaptureButton(
    modifier: Modifier = Modifier,
    onBarcode: (String) -> Unit
) {
    val context = LocalContext.current
    var reading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

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

    Button(
        onClick = {
            if (reading) return@Button
            reading = true
            error = null
            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    barcode.rawValue?.takeIf { it.isNotBlank() }?.let(onBarcode)
                }
                .addOnFailureListener { e ->
                    error = e.message ?: "Não foi possível abrir o leitor."
                }
                .addOnCompleteListener { reading = false }
        },
        enabled = !reading,
        modifier = modifier
    ) {
        Text(if (reading) "ABRINDO LEITOR..." else if (error != null) "TENTAR BIPAR NOVAMENTE" else "BIPAR CÓDIGO")
    }
}
