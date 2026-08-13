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
import com.organizador.estoque.data.PdfImportService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PdfImportBar(repository: InventoryRepository, onImported: () -> Unit = {}) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val service=remember(repository){PdfImportService(context,repository)}
    val scope=rememberCoroutineScope(); var status by remember{mutableStateOf<String?>(null)}; var busy by remember{mutableStateOf(false)}
    fun importPdf(stock:Boolean,uri:Uri){scope.launch{busy=true;status=null;val r=runCatching{withContext(Dispatchers.IO){if(stock)service.importStock(uri) else service.importExpiries(uri)}};status=r.fold({onImported();"${it.imported} importado(s)${if(it.skipped>0)" • ${it.skipped} ignorado(s)" else ""}."},{it.message?:"Falha ao importar PDF."});busy=false}}
    val stockPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let{u->importPdf(true,u)}}
    val expiryPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let{u->importPdf(false,u)}}
    Card(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF10243A))){
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text("Importar dados",style=MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                Button({stockPicker.launch(arrayOf("application/pdf"))},Modifier.weight(1f),enabled=!busy,shape=RoundedCornerShape(14.dp)){Column{Text("PDF ESTOQUE");Text("Importar estoque",style=MaterialTheme.typography.labelSmall)}}
                Button({expiryPicker.launch(arrayOf("application/pdf"))},Modifier.weight(1f),enabled=!busy,shape=RoundedCornerShape(14.dp),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF6C3FD1))){Column{Text("PDF VALIDADES");Text("Importar validades",style=MaterialTheme.typography.labelSmall)}}
            }
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            status?.let{Text(it,style=MaterialTheme.typography.bodySmall)}
        }
    }
}
