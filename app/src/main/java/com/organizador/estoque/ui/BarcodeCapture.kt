package com.organizador.estoque.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

@Composable
fun BarcodeCaptureButton(
    modifier: Modifier = Modifier,
    onBarcode: (String) -> Unit
) {
    var reading by remember { mutableStateOf(false) }
    val scanner = remember { BarcodeScanning.getClient() }
    DisposableEffect(scanner) { onDispose { scanner.close() } }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap == null) {
            reading = false
        } else {
            scanner.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { codes ->
                    codes.firstOrNull()?.rawValue?.takeIf { it.isNotBlank() }?.let(onBarcode)
                }
                .addOnCompleteListener { reading = false }
        }
    }

    Button(
        onClick = { reading = true; launcher.launch(null) },
        enabled = !reading,
        modifier = modifier
    ) {
        Text(if (reading) "LENDO..." else "BIPAR CÓDIGO")
    }
}
