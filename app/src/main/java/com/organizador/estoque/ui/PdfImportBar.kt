package com.organizador.estoque.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.organizador.estoque.data.InventoryRepository
import com.organizador.estoque.data.PdfImportProgress
import com.organizador.estoque.data.PdfImportService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PdfImportBar(repository: InventoryRepository, onImported: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val service = remember(repository) { PdfImportService(context, repository) }
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var stage by remember { mutableStateOf("Preparando importação") }

    fun importPdf(stock: Boolean, uri: Uri) {
        scope.launch {
            busy = true
            status = null
            progress = 0
            stage = "Preparando importação"

            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val callback: (PdfImportProgress) -> Unit = { update ->
                        scope.launch(Dispatchers.Main.immediate) {
                            progress = update.percent
                            stage = update.stage
                        }
                    }
                    if (stock) service.importStock(uri, callback)
                    else service.importExpiries(uri, callback)
                }
            }

            status = result.fold(
                onSuccess = {
                    progress = 100
                    stage = "Concluído"
                    onImported()
                    "${it.imported} importado(s)${if (it.skipped > 0) " • ${it.skipped} ignorado(s)" else ""}."
                },
                onFailure = {
                    stage = "Falha na importação"
                    it.message ?: "Falha ao importar PDF."
                }
            )
            busy = false
        }
    }

    val stockPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { uri -> importPdf(true, uri) }
    }
    val expiryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { uri -> importPdf(false, uri) }
    }

    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10243A))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Importar dados", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { stockPicker.launch(arrayOf("application/pdf")) },
                    modifier = Modifier.weight(1f),
                    enabled = !busy,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column {
                        Text("PDF ESTOQUE")
                        Text("Importar estoque", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Button(
                    onClick = { expiryPicker.launch(arrayOf("application/pdf")) },
                    modifier = Modifier.weight(1f),
                    enabled = !busy,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C3FD1))
                ) {
                    Column {
                        Text("PDF VALIDADES")
                        Text("Importar validades", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (busy) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stage, style = MaterialTheme.typography.bodySmall)
                    Text("$progress%", style = MaterialTheme.typography.labelMedium)
                }
            }

            status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
