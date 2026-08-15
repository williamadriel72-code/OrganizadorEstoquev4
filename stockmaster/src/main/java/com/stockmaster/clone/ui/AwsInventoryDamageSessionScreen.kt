package com.aws.gestaoestoque.ui

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aws.gestaoestoque.data.AwsDamageSessionRecord
import com.aws.gestaoestoque.data.AwsDb
import com.aws.gestaoestoque.data.ProductRow
import com.aws.gestaoestoque.data.UserSession
import com.aws.gestaoestoque.data.saveDamageSession
import com.aws.gestaoestoque.data.saveInventorySession
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class AwsSessionLine(
    val product: ProductRow,
    val quantity: Double,
    val reason: String = ""
)

@Composable
internal fun AwsInventoryDamageSessionScreen(
    mode: String,
    db: AwsDb,
    user: UserSession
) {
    val isDamage = mode == "damage"
    val context = LocalContext.current

    var query by remember(mode) { mutableStateOf("") }
    var selected by remember(mode) { mutableStateOf<ProductRow?>(null) }
    var searchResults by remember(mode) { mutableStateOf(emptyList<ProductRow>()) }
    var quantityText by remember(mode) { mutableStateOf("1") }
    var session by remember(mode) { mutableStateOf(emptyList<AwsSessionLine>()) }
    var message by remember(mode) { mutableStateOf("") }
    var selectedReason by remember(mode) { mutableStateOf("Danificado") }
    var customReason by remember(mode) { mutableStateOf("") }
    var exportSnapshot by remember(mode) { mutableStateOf(emptyList<AwsSessionLine>()) }

    fun selectProduct(product: ProductRow) {
        selected = product
        searchResults = emptyList()
        query = product.ean.ifBlank { product.id }
        quantityText = "1"
        message = ""
    }

    fun search(value: String) {
        val clean = value.trim()
        if (clean.isBlank()) {
            selected = null
            searchResults = emptyList()
            message = "Digite um código, EAN ou nome."
            return
        }
        runCatching {
            db.findExact(clean)?.let { listOf(it) } ?: db.searchProducts(clean)
        }.onSuccess { rows ->
            when {
                rows.isEmpty() -> {
                    selected = null
                    searchResults = emptyList()
                    message = "Nenhuma mercadoria encontrada."
                }
                rows.size == 1 -> selectProduct(rows.first())
                else -> {
                    selected = null
                    searchResults = rows
                    message = "Selecione uma mercadoria."
                }
            }
        }.onFailure {
            selected = null
            searchResults = emptyList()
            message = it.message ?: "Falha na pesquisa."
        }
    }

    fun addSelected() {
        val product = selected ?: run {
            message = "Bipe ou selecione uma mercadoria."
            return
        }
        val qty = quantityText.replace(',', '.').toDoubleOrNull()
        if (qty == null || qty <= 0.0) {
            message = "Informe uma quantidade válida."
            return
        }

        val reason = if (isDamage) {
            if (selectedReason == "Outros") customReason.trim() else selectedReason
        } else ""

        if (isDamage && reason.isBlank()) {
            message = "Informe o motivo da avaria."
            return
        }

        val index = session.indexOfFirst {
            it.product.id == product.id && (!isDamage || it.reason.equals(reason, ignoreCase = true))
        }
        session = if (index >= 0) {
            session.toMutableList().also { lines ->
                val old = lines[index]
                lines[index] = old.copy(quantity = old.quantity + qty)
            }
        } else {
            session + AwsSessionLine(product, qty, reason)
        }

        message = if (index >= 0) {
            "Quantidade somada ao item existente."
        } else {
            if (isDamage) "Avaria adicionada à lista." else "Produto adicionado ao inventário."
        }
        selected = null
        searchResults = emptyList()
        query = ""
        quantityText = "1"
        customReason = ""
    }

    fun buildCsv(lines: List<AwsSessionLine>): String {
        fun csv(value: String): String = "\"${value.replace("\"", "\"\"")}\""
        fun number(value: Double): String = if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(Locale("pt", "BR"), "%.3f", value)
        }

        val header = if (isDamage) {
            "Codigo;EAN;Descricao;Quantidade;Motivo;EstoqueSistema"
        } else {
            "Codigo;EAN;Descricao;QuantidadeContada;EstoqueSistema"
        }
        return buildString {
            append('\uFEFF')
            appendLine(header)
            lines.forEach { line ->
                val stock = line.product.stock?.let(::number).orEmpty()
                if (isDamage) {
                    appendLine(
                        listOf(
                            line.product.id,
                            line.product.ean,
                            csv(line.product.description),
                            number(line.quantity),
                            csv(line.reason),
                            stock
                        ).joinToString(";")
                    )
                } else {
                    appendLine(
                        listOf(
                            line.product.id,
                            line.product.ean,
                            csv(line.product.description),
                            number(line.quantity),
                            stock
                        ).joinToString(";")
                    )
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) {
            message = "Exportação cancelada. A lista foi mantida."
        } else {
            runCatching {
                val csv = buildCsv(exportSnapshot)
                context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8).use { writer ->
                    requireNotNull(writer) { "Não foi possível criar o arquivo." }
                    writer.write(csv)
                }

                if (isDamage) {
                    db.saveDamageSession(
                        exportSnapshot.map {
                            AwsDamageSessionRecord(it.product, it.quantity, it.reason)
                        },
                        user
                    )
                } else {
                    db.saveInventorySession(
                        exportSnapshot.map { it.product to it.quantity },
                        user
                    )
                }
            }.onSuccess {
                val count = exportSnapshot.size
                session = emptyList()
                exportSnapshot = emptyList()
                selected = null
                query = ""
                message = if (isDamage) {
                    "$count item(ns) de avaria exportado(s) e finalizado(s)."
                } else {
                    "$count item(ns) de inventário exportado(s) e finalizado(s)."
                }
            }.onFailure {
                message = it.message ?: "Falha ao finalizar e exportar."
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AwsBackground),
        contentPadding = PaddingValues(bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AwsHeaderGradient)
                    .padding(horizontal = 18.dp, vertical = 22.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AwsModuleIcon(awsModuleVisual(mode))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("AWS", color = AwsPurpleBright, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold)
                        Text(
                            if (isDamage) "Avaria" else "Inventário",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (isDamage) "Bipar produto avariado" else "Bipar produto do inventário",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = AwsText
                    )
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Nome, código ou EAN") },
                        singleLine = true,
                        shape = awsFieldShape,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { search(query) },
                            modifier = Modifier.weight(0.85f).heightIn(min = 54.dp),
                            shape = awsFieldShape
                        ) {
                            Text("Pesquisar", color = AwsPurple, fontWeight = FontWeight.Bold)
                        }
                        AwsSessionBarcodeButton(
                            modifier = Modifier.weight(1.15f),
                            onBarcode = { value ->
                                query = value
                                search(value)
                            }
                        )
                    }
                }
            }
        }

        if (searchResults.size > 1) {
            item {
                Text(
                    "Selecione o produto",
                    modifier = Modifier.padding(horizontal = 18.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = AwsText
                )
            }
            items(searchResults.take(30), key = { it.id }) { product ->
                ElevatedCard(
                    onClick = { selectProduct(product) },
                    modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(product.description, fontWeight = FontWeight.ExtraBold, color = AwsText)
                        Text("Código ${product.id} • ${product.ean.ifBlank { "sem EAN" }}", color = AwsMuted)
                    }
                }
            }
        }

        selected?.let { product ->
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(product.description, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = AwsText)
                        Text("Código ${product.id} • EAN ${product.ean.ifBlank { "não informado" }}", color = AwsMuted)
                        product.stock?.let {
                            Text("Estoque do sistema: ${sessionQuantity(it)}", color = AwsText, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedTextField(
                            value = quantityText,
                            onValueChange = { quantityText = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' } },
                            label = { Text(if (isDamage) "Quantidade avariada" else "Quantidade contada") },
                            singleLine = true,
                            shape = awsFieldShape,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (isDamage) {
                            DamageReasonSelector(
                                selected = selectedReason,
                                onSelected = { selectedReason = it }
                            )
                            if (selectedReason == "Outros") {
                                OutlinedTextField(
                                    value = customReason,
                                    onValueChange = { customReason = it },
                                    label = { Text("Informe o motivo") },
                                    shape = awsFieldShape,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        AwsGradientButton(
                            text = if (isDamage) "Adicionar avaria" else "Adicionar ao inventário",
                            onClick = { addSelected() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        if (message.isNotBlank()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF0E9FF)
                ) {
                    Text(
                        message,
                        modifier = Modifier.padding(12.dp),
                        color = AwsPurple,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isDamage) "Avarias acumuladas" else "Inventário acumulado",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = AwsText
                    )
                    Text("${session.size} produto(s) • ${sessionQuantity(session.sumOf { it.quantity })} unidade(s)", color = AwsMuted)
                }
            }
        }

        if (session.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White
                ) {
                    Text(
                        if (isDamage) "Nenhuma avaria adicionada ainda." else "Nenhum produto contado ainda.",
                        modifier = Modifier.padding(20.dp),
                        textAlign = TextAlign.Center,
                        color = AwsMuted
                    )
                }
            }
        } else {
            items(session, key = { "${it.product.id}|${it.reason}" }) { line ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(line.product.description, fontWeight = FontWeight.ExtraBold, color = AwsText)
                                Text("Código ${line.product.id} • ${line.product.ean.ifBlank { "sem EAN" }}", color = AwsMuted)
                                if (isDamage) {
                                    Text("Motivo: ${line.reason}", color = AwsOrange, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Text(
                                sessionQuantity(line.quantity),
                                color = AwsPurple,
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        TextButton(
                            onClick = { session = session.filterNot { it === line } },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Remover", color = AwsRed)
                        }
                    }
                }
            }

            item {
                AwsGradientButton(
                    text = "Finalizar e exportar",
                    onClick = {
                        exportSnapshot = session
                        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                        val name = if (isDamage) "avarias_$stamp.csv" else "inventario_$stamp.csv"
                        exportLauncher.launch(name)
                    },
                    modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun DamageReasonSelector(
    selected: String,
    onSelected: (String) -> Unit
) {
    val reasons = listOf("Danificado", "Furto", "Vencido", "Outros")
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Motivo da avaria", fontWeight = FontWeight.Bold, color = AwsText)
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = awsFieldShape
            ) {
                Text(selected, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                Text("▾")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                reasons.forEach { reason ->
                    DropdownMenuItem(
                        text = { Text(reason) },
                        onClick = {
                            onSelected(reason)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AwsSessionBarcodeButton(
    modifier: Modifier = Modifier,
    onBarcode: (String) -> Unit
) {
    val context = LocalContext.current
    var reading by remember { mutableStateOf(false) }
    val scanner = remember(context) {
        val options = GmsBarcodeScannerOptions.Builder()
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
        GmsBarcodeScanning.getClient(context, options)
    }

    AwsGradientButton(
        text = if (reading) "Abrindo scanner..." else "▥  Bipar código",
        enabled = !reading,
        onClick = {
            if (reading) return@AwsGradientButton
            reading = true
            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    barcode.rawValue?.takeIf { it.isNotBlank() }?.let {
                        playSessionBip()
                        onBarcode(it)
                    }
                }
                .addOnCompleteListener { reading = false }
        },
        modifier = modifier
    )
}

private fun playSessionBip() {
    runCatching {
        val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 90)
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching { tone.startTone(ToneGenerator.TONE_PROP_ACK, 80) }
            Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 100)
        }, 95)
    }
}

private fun sessionQuantity(value: Double): String = if (value % 1.0 == 0.0) {
    value.toInt().toString()
} else {
    String.format(Locale("pt", "BR"), "%.3f", value)
}
