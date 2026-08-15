package com.aws.gestaoestoque.ui

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
import com.aws.gestaoestoque.data.AwsDb
import com.aws.gestaoestoque.data.ExpiryRow
import com.aws.gestaoestoque.data.ProductRow
import com.aws.gestaoestoque.data.UserSession
import com.aws.gestaoestoque.data.expiryForProduct
import com.aws.gestaoestoque.data.formatExpiryForDisplay
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.util.Locale

@Composable
internal fun AwsModuleScreen(
    module: AwsModuleDef,
    db: AwsDb,
    user: UserSession
) {
    var query by remember(module.id) { mutableStateOf("") }
    var results by remember(module.id) { mutableStateOf(emptyList<ProductRow>()) }
    var selected by remember(module.id) { mutableStateOf<ProductRow?>(null) }
    var productExpiries by remember(module.id) { mutableStateOf(emptyList<ExpiryRow>()) }
    var qty by remember(module.id) { mutableStateOf("1") }
    var expiry by remember(module.id) { mutableStateOf("") }
    var lot by remember(module.id) { mutableStateOf("") }
    var observation by remember(module.id) { mutableStateOf("") }
    var message by remember(module.id) { mutableStateOf("") }
    var expiryRows by remember(module.id) {
        mutableStateOf(
            if (module.id == "expiry") runCatching { db.listExpiry() }.getOrDefault(emptyList())
            else emptyList()
        )
    }
    var saleItems by remember(module.id) { mutableStateOf(emptyList<Pair<ProductRow, Double>>()) }

    fun selectProduct(product: ProductRow) {
        selected = product
        productExpiries = runCatching { db.expiryForProduct(product.id) }.getOrDefault(emptyList())
    }

    fun search(value: String) {
        query = value
        runCatching {
            db.findExact(value)?.let { listOf(it) } ?: db.searchProducts(value)
        }.onSuccess { rows ->
            if (rows.size == 1) {
                selectProduct(rows.first())
                results = if (module.id == "price") emptyList() else rows
            } else {
                results = rows
                selected = null
                productExpiries = emptyList()
            }
            message = if (rows.isEmpty()) "Nenhuma mercadoria encontrada." else ""
        }.onFailure {
            results = emptyList()
            selected = null
            productExpiries = emptyList()
            message = it.message ?: "Falha na pesquisa."
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AwsBackground),
        contentPadding = PaddingValues(bottom = 18.dp),
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
                    AwsModuleIcon(awsModuleVisual(module.id))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("AWS", color = AwsPurpleBright, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold)
                        Text(module.title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Busca de produto", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = AwsText)
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Digite o nome, código ou EAN") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AwsPurple,
                            focusedLabelColor = AwsPurple,
                            unfocusedBorderColor = Color(0xFFD7DAE3),
                            focusedContainerColor = Color(0xFFF9FAFD),
                            unfocusedContainerColor = Color(0xFFF9FAFD)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { search(query) },
                            modifier = Modifier.weight(0.8f).heightIn(min = 54.dp),
                            shape = awsFieldShape,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AwsPurple),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AwsPurple.copy(alpha = 0.45f))
                        ) {
                            Text("Pesquisar", fontWeight = FontWeight.Bold)
                        }
                        AwsBarcodeButton(
                            modifier = Modifier.weight(1.2f),
                            onBarcode = { search(it) }
                        )
                    }
                }
            }
        }

        selected?.let { product ->
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .background(Color(0xFFF0E9FF), RoundedCornerShape(18.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("▦", color = AwsPurple, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(product.description, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = AwsText)
                                Text("Código ${product.id}", color = AwsMuted)
                            }
                        }

                        HorizontalDivider(color = Color(0xFFE8EAF0))
                        AwsInfoRow("EAN", product.ean.ifBlank { "Não informado" })
                        AwsInfoRow("Grupo", product.groupId.ifBlank { "Não informado" })
                        AwsInfoRow(
                            "Localização",
                            product.sectorId?.let { "Setor $it" } ?: "Não disponível no banco"
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AwsMetricCard("Estoque", product.stock?.let { awsQuantity(it) } ?: "-", AwsPurple, Modifier.weight(1f))
                            AwsMetricCard("Preço", "R$ ${awsMoney(product.price)}", AwsBlue, Modifier.weight(1f))
                        }
                        if (product.cashPrice > 0.0 && product.cashPrice != product.price) {
                            Text("À vista: R$ ${awsMoney(product.cashPrice)}", color = AwsMuted, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Validade", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = AwsText)

                        if (productExpiries.isEmpty()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFFFE6E6)
                            ) {
                                Text(
                                    "SEM VALIDADE CADASTRADA",
                                    modifier = Modifier.padding(14.dp),
                                    color = AwsRed,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            val nearest = productExpiries.first()
                            val nearestStatus = awsExpiryStatus(nearest.expiry)
                            AwsExpiryStatusBadge(nearestStatus, modifier = Modifier.fillMaxWidth())
                            Text(
                                "Validade mais próxima: ${formatExpiryForDisplay(nearest.expiry)}",
                                color = AwsText,
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            AwsExpiryLine(nearest, nearestStatus)

                            if (productExpiries.size > 1) {
                                Text("Outras validades", fontWeight = FontWeight.Bold, color = AwsText, modifier = Modifier.padding(top = 4.dp))
                                productExpiries.drop(1).take(10).forEach { row ->
                                    AwsExpiryLine(row, awsExpiryStatus(row.expiry))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (module.id !in setOf("price", "restock")) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Dados do registro", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = AwsText)
                        OutlinedTextField(
                            value = qty,
                            onValueChange = { qty = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                            label = { Text("Quantidade") },
                            singleLine = true,
                            shape = awsFieldShape,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (module.id == "expiry") {
                            OutlinedTextField(
                                value = expiry,
                                onValueChange = { expiry = it },
                                label = { Text("Validade (AAAA-MM-DD)") },
                                singleLine = true,
                                shape = awsFieldShape,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = lot,
                                onValueChange = { lot = it },
                                label = { Text("Lote") },
                                singleLine = true,
                                shape = awsFieldShape,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (module.id == "movement") {
                            OutlinedTextField(
                                value = observation,
                                onValueChange = { observation = it },
                                label = { Text("Observação") },
                                shape = awsFieldShape,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        AwsGradientButton(
                            text = awsActionLabel(module.id),
                            onClick = {
                                val product = selected
                                val number = qty.replace(',', '.').toDoubleOrNull()
                                if (product == null) {
                                    message = "Selecione um produto."
                                } else if (number == null || number <= 0) {
                                    message = "Informe uma quantidade válida."
                                } else {
                                    runCatching {
                                        when (module.id) {
                                            "prevenda" -> saleItems = saleItems + (product to number)
                                            "inventory" -> db.addInventory(product, number, user)
                                            "conference" -> db.addConference(product, number, user)
                                            "damage" -> db.addDamage(product, number, user)
                                            "print" -> db.addPrint(product, number.toInt().coerceAtLeast(1))
                                            "movement" -> db.addMovement(product, number, user, observation)
                                            "expiry" -> {
                                                require(expiry.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                                                    "Use a validade no formato AAAA-MM-DD."
                                                }
                                                db.addExpiry(product, expiry, lot, number, user)
                                                expiryRows = db.listExpiry()
                                                productExpiries = db.expiryForProduct(product.id)
                                            }
                                            else -> Unit
                                        }
                                    }.onSuccess {
                                        message = "Registro salvo com sucesso."
                                        qty = "1"
                                    }.onFailure { message = it.message ?: "Falha ao salvar." }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        if (message.isNotBlank()) {
            item {
                val isError = message.contains("nenhuma", true) || message.contains("falha", true) || message.contains("invál", true)
                Surface(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = if (isError) Color(0xFFFFE6E6) else Color(0xFFE6F6ED)
                ) {
                    Text(
                        message,
                        modifier = Modifier.padding(14.dp),
                        color = if (isError) AwsRed else AwsGreen,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        if (module.id == "prevenda" && saleItems.isNotEmpty()) {
            item {
                val total = saleItems.sumOf { (product, amount) -> product.price * amount }
                Surface(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFFF0E9FF)
                ) {
                    Text(
                        "Itens na pré-venda: ${saleItems.size}  •  Total: R$ ${awsMoney(total)}",
                        modifier = Modifier.padding(16.dp),
                        fontWeight = FontWeight.Bold,
                        color = AwsPurple
                    )
                }
            }
        }

        if (module.id == "expiry" || results.isNotEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(horizontal = 18.dp, vertical = 2.dp)) {
                    Text(
                        if (module.id == "expiry") "Validades registradas" else "Resultados",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = AwsText
                    )
                    if (module.id != "expiry") Text("Toque em uma mercadoria para ver os detalhes", color = AwsMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (module.id == "expiry" && expiryRows.isNotEmpty()) {
            items(expiryRows) { row ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(row.description.ifBlank { "Produto ${row.productId}" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = AwsText)
                        AwsExpiryLine(row, awsExpiryStatus(row.expiry))
                    }
                }
            }
        } else if (results.isNotEmpty()) {
            items(results) { product ->
                ElevatedCard(
                    onClick = { selectProduct(product) },
                    modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(42.dp).background(Color(0xFFF0E9FF), RoundedCornerShape(13.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("▦", color = AwsPurple, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(product.description, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = AwsText)
                            Spacer(Modifier.height(3.dp))
                            Text("Código ${product.id}  •  ${product.ean.ifBlank { "sem EAN" }}", color = AwsMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("›", style = MaterialTheme.typography.headlineSmall, color = AwsPurple, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AwsInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = AwsMuted, modifier = Modifier.width(96.dp), style = MaterialTheme.typography.bodyMedium)
        Text(value, color = AwsText, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun AwsMetricCard(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = tint.copy(alpha = 0.09f)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, color = AwsMuted, style = MaterialTheme.typography.bodySmall)
            Text(value, color = tint, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun AwsExpiryLine(row: ExpiryRow, status: AwsExpiryStatus) {
    val tint = when (status) {
        AwsExpiryStatus.EXPIRED -> AwsRed
        AwsExpiryStatus.TODAY -> Color(0xFFE67822)
        AwsExpiryStatus.NEAR -> AwsOrange
        AwsExpiryStatus.OK -> AwsGreen
        AwsExpiryStatus.UNKNOWN -> AwsMuted
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF8F9FC),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE7E9F0))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(4.dp).height(34.dp).background(tint, RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(formatExpiryForDisplay(row.expiry), color = AwsText, fontWeight = FontWeight.Bold)
                Text(status.label, color = tint, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
            }
            Text("${awsQuantity(row.quantity)} un.", color = AwsText, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AwsExpiryStatusBadge(status: AwsExpiryStatus, modifier: Modifier = Modifier) {
    val background = when (status) {
        AwsExpiryStatus.EXPIRED -> Color(0xFFFFE1E1)
        AwsExpiryStatus.TODAY -> Color(0xFFFFE9D6)
        AwsExpiryStatus.NEAR -> Color(0xFFFFF0D9)
        AwsExpiryStatus.OK -> Color(0xFFE1F3E8)
        AwsExpiryStatus.UNKNOWN -> Color(0xFFEEF0F4)
    }
    val foreground = when (status) {
        AwsExpiryStatus.EXPIRED -> AwsRed
        AwsExpiryStatus.TODAY -> Color(0xFFE67822)
        AwsExpiryStatus.NEAR -> AwsOrange
        AwsExpiryStatus.OK -> AwsGreen
        AwsExpiryStatus.UNKNOWN -> AwsMuted
    }

    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = background) {
        Text(
            text = status.label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            color = foreground,
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AwsBarcodeButton(modifier: Modifier = Modifier, onBarcode: (String) -> Unit) {
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

    Surface(
        onClick = {
            if (reading) return@Surface
            reading = true
            failed = false
            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    barcode.rawValue?.takeIf { it.isNotBlank() }?.let { value ->
                        playAwsFunnyScanBip()
                        onBarcode(value)
                    }
                }
                .addOnFailureListener { failed = true }
                .addOnCompleteListener { reading = false }
        },
        enabled = !reading,
        modifier = modifier.heightIn(min = 54.dp),
        shape = awsFieldShape,
        color = Color.Transparent,
        shadowElevation = 5.dp
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().background(AwsPrimaryGradient).padding(horizontal = 14.dp, vertical = 15.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                when {
                    reading -> "Abrindo scanner..."
                    failed -> "Tentar novamente"
                    else -> "▥  Bipar código"
                },
                color = Color.White,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

private fun awsActionLabel(id: String): String = when (id) {
    "prevenda" -> "Adicionar à pré-venda"
    "inventory" -> "Salvar inventário"
    "conference" -> "Salvar conferência"
    "damage" -> "Registrar avaria"
    "print" -> "Adicionar à impressão"
    "movement" -> "Salvar movimentação"
    "expiry" -> "Salvar validade"
    else -> "Salvar"
}

private fun awsMoney(value: Double): String = String.format(Locale("pt", "BR"), "%.2f", value)
private fun awsQuantity(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale("pt", "BR"), "%.2f", value)
