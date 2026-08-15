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
import com.stockmaster.clone.data.ProductPdfImportReport
import com.stockmaster.clone.data.ProductPdfImporter
import com.stockmaster.clone.data.StockMasterDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProductPdfImportScreen(
    db: StockMasterDb,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<ProductPdfImportReport?>(null) }
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
                        ProductPdfImporter(context.applicationContext, db).import(uri)
                    }
                }.onSuccess {
                    report = it
                    message = if (it.errors == 0) {
                        "Importação finalizada sem linhas rejeitadas."
                    } else {
                        "Importação finalizada com ${it.errors} linha(s) não reconhecida(s) ou com erro."
                    }
                }.onFailure {
                    message = it.message ?: "Não foi possível importar o PDF."
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        enabled = !importing,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Voltar")
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "AWS",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Importar mercadorias – PDF",
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
                        "Importação inteligente de mercadorias",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Selecione o relatório em PDF com as colunas Código, EAN, Descrição e Estoque Atual. Os grupos do relatório também serão reconhecidos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Produtos já existentes são atualizados sem duplicar. O estoque do PDF substitui o estoque atual; preços e outros dados que não vêm nesse relatório são preservados.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "PDFs grandes são processados fora da interface para manter o aplicativo responsivo. O resultado informa qualquer linha que não tenha sido reconhecida.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = { pdfLauncher.launch(arrayOf("application/pdf")) },
                        enabled = !importing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(if (importing) "Processando PDF..." else "Selecionar PDF de mercadorias")
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
                            Text("Lendo, mesclando e atualizando as mercadorias...")
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
                        ImportResultLine("Produtos reconhecidos", result.found)
                        ImportResultLine("Produtos novos", result.inserted)
                        ImportResultLine("Produtos atualizados", result.updated)
                        ImportResultLine("Duplicados/ignorados", result.ignored)
                        ImportResultLine("Linhas não reconhecidas / erros", result.errors)

                        if (result.errors > 0) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                "Atenção: ${result.errors} linha(s) do PDF não puderam ser aplicadas. Confira este número principalmente em relatórios grandes para detectar importação parcial.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            if (message.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = if (report != null && (report?.errors ?: 0) == 0) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(14.dp),
                        color = if (report != null && (report?.errors ?: 0) == 0) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportResultLine(label: String, value: Int) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            value.toString(),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
