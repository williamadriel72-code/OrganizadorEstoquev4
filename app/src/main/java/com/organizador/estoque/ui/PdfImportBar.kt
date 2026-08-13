package com.organizador.estoque.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.organizador.estoque.data.InventoryRepository
import com.organizador.estoque.data.PdfImportService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PdfImportBar(repository: InventoryRepository, onImported: () -> Unit = {}) {
    val context = LocalContext.current
    val service = remember(repository) { PdfImportService(context, repository) }
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun importPdf(stock: Boolean, uri: Uri) {
        scope.launch {
            busy = true
            status = null
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    if (stock) service.importStock(uri) else service.importExpiries(uri)
                }
            }
            status = result.fold(
                onSuccess = {
                    onImported()
                    if (it.skipped > 0) "${it.imported} importado(s), ${it.skipped} ignorado(s)." else "${it.imported} importado(s) com sucesso."
                },
                onFailure = { it.message ?: "Falha ao importar PDF." }
            )
            busy = false
        }
    }

    val stockPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importPdf(true, uri)
    }
    val expiryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importPdf(false, uri)
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("IMPORTAR PDF EXTERNO")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { stockPicker.launch(arrayOf("application/pdf")) },
                modifier = Modifier.weight(1f),
                enabled = !busy
            ) { Text("PDF ESTOQUE") }
            Button(
                onClick = { expiryPicker.launch(arrayOf("application/pdf")) },
                modifier = Modifier.weight(1f),
                enabled = !busy
            ) { Text("PDF VALIDADES") }
        }
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        status?.let { Text(it) }
    }
}
