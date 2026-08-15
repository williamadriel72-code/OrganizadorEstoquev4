package com.stockmaster.clone.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stockmaster.clone.data.ExpiryPdfImportReport
import com.stockmaster.clone.data.ExpiryPdfImporter
import com.stockmaster.clone.data.StockMasterDb
import com.stockmaster.clone.data.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ExpiryPdfImportScreen(
    db: StockMasterDb,
    user: UserSession,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<ExpiryPdfImportReport?>(null) }
    var message by remember { mutableStateOf("") }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && !importing) {
            importing = true
            report = null
            message = ""
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        ExpiryPdfImporter(context.applicationContext, db).import(uri, user)
                    }
                }.onSuccess {
                    report = it
                    message = if (it.errors == 0) {
                        "Importação de validades finalizada sem linhas rejeitadas."
                    } else {
                        "Importação finalizada com ${it.errors} linha(s) de validade não reconhecida(s) ou com erro."
                    }
                }.onFailure {
                    message = it.message ?: "Não foi possível importar o PDF de validades."
                }
                importing = false
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        enabled = !importing,
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Voltar") }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "AWS",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Importar datas de validade – PDF",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Importação inteligente de validades",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Selecione o relatório em PDF com Código, EAN, Descrição, Estoque Atual e Data Validade.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "A mercadoria é localizada primeiro pelo código e depois pelo EAN. Se a mesma validade já existir, o registro é atualizado em vez de duplicado.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "PDFs grandes são processados fora da interface. No final, o AWS informa quantas linhas foram reconhecidas e quantas não puderam ser vinculadas corretamente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = { pdfLauncher.launch(arrayOf("application/pdf")) },
                        enabled = !importing,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(if (importing) "Processando PDF..." else "Selecionar PDF de validades")
                    }

                    if (importing) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("Lendo, vinculando e mesclando as validades...")
                        }
                    }
                }
            }

            report?.let { result ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Resultado da importação",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        ExpiryImportResultLine("Validades reconhecidas", result.found)
                        ExpiryImportResultLine("Novas validades", result.inserted)
                        ExpiryImportResultLine("Validades atualizadas", result.updated)
                        ExpiryImportResultLine("Duplicadas/não vinculadas", result.ignored)
                        ExpiryImportResultLine("Linhas não reconhecidas / erros", result.errors)

                        if (result.errors > 0 || result.ignored > 0) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                "Atenção: confira os números acima. Linhas ignoradas podem indicar produto não localizado por código/EAN; erros podem indicar formato de linha ou data não reconhecido.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            if (message.isNotBlank()) {
                val clean = report != null && (report?.errors ?: 0) == 0 && (report?.ignored ?: 0) == 0
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = if (clean) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(14.dp),
                        color = if (clean) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpiryImportResultLine(label: String, value: Int) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(value.toString(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
