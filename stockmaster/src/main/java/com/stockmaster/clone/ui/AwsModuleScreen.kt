package com.stockmaster.clone.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.stockmaster.clone.data.ExpiryRow
import com.stockmaster.clone.data.ProductRow
import com.stockmaster.clone.data.StockMasterDb
import com.stockmaster.clone.data.UserSession
import com.stockmaster.clone.data.expiryForProduct
import com.stockmaster.clone.data.formatExpiryForDisplay
import java.util.Locale

@Composable
internal fun AwsModuleScreen(
    module: AwsModuleDef,
    db: StockMasterDb,
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
            results = rows
            if (rows.size == 1) {
                selectProduct(rows.first())
            } else {
                selected = null
                productExpiries = emptyList()
            }
        }.onFailure {
            selected = null
            productExpiries = emptyList()
            message = it.message ?: "Falha na pesquisa."
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().widthIn(max = 760.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = awsCardShape) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Text(
                            "AWS",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            module.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = awsCardShape) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Pesquisar mercadoria", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("Código, EAN ou produto") },
                            singleLine = true,
                            shape = awsFieldShape,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { search(query) },
                                modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                                shape = awsFieldShape
                            ) { Text("Pesquisar") }
                            AwsBarcodeButton(
                                modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                                onBarcode = { search(it) }
                            )
                        }
                    }
                }
            }

            selected?.let { product ->
                item {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = awsCardShape,
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                product.description,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text("Código: ${product.id}  •  EAN: ${product.ean.ifBlank { "-" }}")
                            Text("Grupo: ${product.groupId.ifBlank { "-" }}")
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text("Preço gôndola: R$ ${awsMoney(product.price)}", fontWeight = FontWeight.SemiBold)
                            Text("Preço à vista: R$ ${awsMoney(product.cashPrice)}", fontWeight = FontWeight.SemiBold)
                            Text("Estoque: ${product.stock?.toString() ?: "não informado"}")
                        }
                    }
                }

                item {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = awsCardShape
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Text(
                                "Validade da mercadoria",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            if (productExpiries.isEmpty()) {
                                Text(
                                    "SEM VALIDADE CADASTRADA",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                val nearest = productExpiries.first()
                                Text(
                                    "VALIDADE MAIS PRÓXIMA: ${formatExpiryForDisplay(nearest.expiry)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (nearest.quantity != 0.0) {
                                    Text("Quantidade: ${nearest.quantity}")
                                }
                                if (productExpiries.size > 1) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 3.dp))
                                    Text("Outras validades", fontWeight = FontWeight.SemiBold)
                                    productExpiries.drop(1).take(10).forEach { row ->
                                        Text(
                                            "• ${formatExpiryForDisplay(row.expiry)}  •  Qtde: ${row.quantity}" +
                                                if (row.lot.isNotBlank()) "  •  Lote: ${row.lot}" else ""
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (module.id !in setOf("price", "restock")) {
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = awsCardShape) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Dados do registro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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

                            Button(
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
                                        }.onFailure {
                                            message = it.message ?: "Falha ao salvar."
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                shape = awsFieldShape
                            ) { Text(awsActionLabel(module.id)) }
                        }
                    }
                }
            }

            if (message.isNotBlank()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            message,
                            modifier = Modifier.padding(14.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            if (module.id == "prevenda" && saleItems.isNotEmpty()) {
                item {
                    val total = saleItems.sumOf { (product, amount) -> product.price * amount }
                    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                        Text(
                            "Itens na pré-venda: ${saleItems.size}  •  Total: R$ ${awsMoney(total)}",
                            modifier = Modifier.padding(16.dp),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            item {
                Text(
                    if (module.id == "expiry") "Validades registradas" else "Resultados",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
            }

            if (module.id == "expiry" && expiryRows.isNotEmpty()) {
                items(expiryRows) { row ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                row.description.ifBlank { "Produto ${row.productId}" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Validade: ${formatExpiryForDisplay(row.expiry)}  •  Lote: ${row.lot.ifBlank { "-" }}  •  Qtde: ${row.quantity}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(results) { product ->
                    ElevatedCard(
                        onClick = { selectProduct(product) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(product.description, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    "${product.id} • ${product.ean.ifBlank { "sem EAN" }} • R$ ${awsMoney(product.price)}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
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

    Button(
        onClick = {
            if (reading) return@Button
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
        modifier = modifier,
        shape = awsFieldShape
    ) {
        Text(when {
            reading -> "Abrindo..."
            failed -> "Tentar de novo"
            else -> "Bipar código"
        })
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
