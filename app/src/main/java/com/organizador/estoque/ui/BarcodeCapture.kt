package com.organizador.estoque.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

@Composable
fun BarcodeCaptureButton(
    modifier: Modifier = Modifier,
    onBarcode: (String) -> Unit
) {
    val context = LocalContext.current
    var reading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

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
            failed = false
            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    barcode.rawValue?.takeIf { it.isNotBlank() }?.let(onBarcode)
                }
                .addOnFailureListener { failed = true }
                .addOnCompleteListener { reading = false }
        },
        enabled = !reading,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D63E6))
    ) {
        Text(
            when {
                reading -> "ABRINDO LEITOR..."
                failed -> "TENTAR BIPAR NOVAMENTE"
                else -> "▣  BIPAR CÓDIGO"
            }
        )
    }
}
