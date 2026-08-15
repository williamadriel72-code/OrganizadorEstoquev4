package com.stockmaster.clone.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.stockmaster.clone.data.ProductRow
import com.stockmaster.clone.data.StockMasterDb
import com.stockmaster.clone.data.UserSession
import java.util.Locale

private data class ModuleDef(val id: String, val title: String, val permission: String?)

private val modules = listOf(
    ModuleDef("price", "Busca de preço", "acessoBuscaPreco"),
    ModuleDef("restock", "Reposição", "acessoReposicao"),
    ModuleDef("prevenda", "Pré-venda", "acessoPrevenda"),
    ModuleDef("inventory", "Inventário", "acessoInventario"),
    ModuleDef("conference", "Conferência", "acessoConferencia"),
    ModuleDef("print", "Impressão", "acessoImpressao"),
    ModuleDef("damage", "Avaria", "acessoAvaria"),
    ModuleDef("movement", "Movimentação", "acessoMovimentacao"),
    ModuleDef("expiry", "Controle de validade", null)
)

@Composable
fun StockMasterApp() {
    val context = LocalContext.current
    val db = remember { StockMasterDb(context.applicationContext) }
    var dbReady by remember { mutableStateOf(runCatching { db.hasDatabase().also { if (it) db.validateSchema() } }.getOrDefault(false)) }
    var user by remember { mutableStateOf<UserSession?>(null) }
    var screen by remember { mutableStateOf("login") }
    var message by remember { mutableStateOf("") }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                db.importDatabase(uri)
                dbReady = true
                user = null
                screen = "login"
            }.onSuccess {
                message = "Banco importado com sucesso."
            }.onFailure {
                dbReady = false
                message = it.message ?: "Falha ao importar o banco."
            }
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when {
                user == null || screen == "login" -> LoginScreen(
                    dbReady = dbReady,
                    message = message,
                    onImport = { importLauncher.launch(arrayOf("application/octet-stream", "application/x-sqlite3", "*/*")) },
                    onLogin = { login, pass ->
                        runCatching { db.authenticate(login, pass) }
                            .onSuccess { found ->
                                if (found == null) message = "Usuário ou senha inválidos."
                                else {
                                    user = found
                                    screen = "dashboard"
                                    message = ""
                                }
                            }
                            .onFailure { message = it.message ?: "Erro ao abrir o banco." }
                    }
                )

                screen == "dashboard" -> DashboardScreen(
                    user = user!!,
                    onOpen = { screen = it },
                    onImport = { importLauncher.launch(arrayOf("application/octet-stream", "application/x-sqlite3", "*/*")) },
                    onLogout = { user = null; screen = "login" }
                )

                else -> ModuleScreen(
                    module = modules.firstOrNull { it.id == screen } ?: modules.first(),
                    db = db,
                    user = user!!,
                    onBack = { screen = "dashboard" }
                )
            }
        }
    }
}

@Composable
private fun LoginScreen(
    dbReady: Boolean,
    message: String,
    onImport: () -> Unit,
    onLogin: (String, String) -> Unit
) {
    var login by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("AWS", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("AWS – Gestão de Estoque", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))

        Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Text(if (dbReady) "Trocar arquivo .db" else "Importar arquivo .db")
        }

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = login,
            onValueChange = { login = it },
            label = { Text("Usuário") },
            enabled = dbReady,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it },
            label = { Text("Senha") },
            visualTransformation = PasswordVisualTransformation(),
            enabled = dbReady,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onLogin(login, pass) },
            enabled = dbReady && login.isNotBlank() && pass.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Entrar") }
        if (message.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(message, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun DashboardScreen(
    user: UserSession,
    onOpen: (String) -> Unit,
    onImport: () -> Unit,
    onLogout: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("Olá, ${user.name}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Módulos liberados pelo seu perfil")
        Spacer(Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            items(modules.filter { module -> module.permission?.let { user.can(it) } ?: true }) { module ->
                ElevatedCard(onClick = { onOpen(module.id) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text(module.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("Trocar banco") }
            OutlinedButton(onClick = onLogout, modifier = Modifier.weight(1f)) { Text("Sair") }
        }
    }
}

@Composable
private fun ModuleScreen(
    module: ModuleDef,
    db: StockMasterDb,
    user: UserSession,
    onBack: () -> Unit
) {
    var query by remember(module.id) { mutableStateOf("") }
    var results by remember(module.id) { mutableStateOf(emptyList<ProductRow>()) }
    var selected by remember(module.id) { mutableStateOf<ProductRow?>(null) }
    var qty by remember(module.id) { mutableStateOf("1") }
    var expiry by remember(module.id) { mutableStateOf("") }
    var lot by remember(module.id) { mutableStateOf("") }
    var observation by remember(module.id) { mutableStateOf("") }
    var message by remember(module.id) { mutableStateOf("") }
    var expiryRows by remember(module.id) { mutableStateOf(if (module.id == "expiry") runCatching { db.listExpiry() }.getOrDefault(emptyList()) else emptyList()) }
    var saleItems by remember(module.id) { mutableStateOf(emptyList<Pair<ProductRow, Double>>()) }

    fun search(value: String) {
        query = value
        runCatching {
            db.findExact(value)?.let { listOf(it) } ?: db.searchProducts(value)
        }.onSuccess { rows ->
            results = rows
            if (rows.size == 1) selected = rows.first()
        }.onFailure { message = it.message ?: "Falha na pesquisa." }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("Voltar") }
            Spacer(Modifier.width(12.dp))
            Text(module.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Código, EAN ou produto") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { search(query) }, modifier = Modifier.weight(1f)) { Text("Pesquisar") }
            BarcodeButton(modifier = Modifier.weight(1f), onBarcode = { search(it) })
        }

        selected?.let { product ->
            Spacer(Modifier.height(12.dp))
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(product.description, fontWeight = FontWeight.Bold)
                    Text("Código: ${product.id}  •  EAN: ${product.ean.ifBlank { "-" }}")
                    Text("Grupo: ${product.groupId.ifBlank { "-" }}")
                    Text("Preço gôndola: R$ ${money(product.price)}")
                    Text("Preço à vista: R$ ${money(product.cashPrice)}")
                    Text("Estoque: ${product.stock?.toString() ?: "não informado"}")
                }
            }
        }

        if (module.id !in setOf("price", "restock")) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = qty,
                onValueChange = { qty = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                label = { Text("Quantidade") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (module.id == "expiry") {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = expiry, onValueChange = { expiry = it }, label = { Text("Validade (AAAA-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = lot, onValueChange = { lot = it }, label = { Text("Lote") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }

        if (module.id == "movement") {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = observation, onValueChange = { observation = it }, label = { Text("Observação") }, modifier = Modifier.fillMaxWidth())
        }

        if (module.id !in setOf("price", "restock")) {
            Spacer(Modifier.height(10.dp))
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
                                    require(expiry.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { "Use a validade no formato AAAA-MM-DD." }
                                    db.addExpiry(product, expiry, lot, number, user)
                                    expiryRows = db.listExpiry()
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
            ) { Text(actionLabel(module.id)) }
        }

        if (message.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(message, color = MaterialTheme.colorScheme.primary)
        }

        if (module.id == "prevenda" && saleItems.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            val total = saleItems.sumOf { (product, amount) -> product.price * amount }
            Text("Itens na pré-venda: ${saleItems.size}  •  Total: R$ ${money(total)}", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(10.dp))
        Text(if (module.id == "expiry") "Validades registradas" else "Resultados", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            if (module.id == "expiry" && expiryRows.isNotEmpty()) {
                items(expiryRows) { row ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Text(row.description.ifBlank { "Produto ${row.productId}" }, fontWeight = FontWeight.Medium)
                            Text("Validade: ${row.expiry}  •  Lote: ${row.lot.ifBlank { "-" }}  •  Qtde: ${row.quantity}")
                        }
                    }
                }
            } else {
                items(results) { product ->
                    ElevatedCard(onClick = { selected = product }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Text(product.description, fontWeight = FontWeight.Medium)
                            Text("${product.id} • ${product.ean.ifBlank { "sem EAN" }} • R$ ${money(product.price)}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BarcodeButton(modifier: Modifier = Modifier, onBarcode: (String) -> Unit) {
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
                .addOnSuccessListener { barcode -> barcode.rawValue?.takeIf { it.isNotBlank() }?.let(onBarcode) }
                .addOnFailureListener { failed = true }
                .addOnCompleteListener { reading = false }
        },
        enabled = !reading,
        modifier = modifier
    ) {
        Text(when { reading -> "Abrindo..."; failed -> "Tentar de novo"; else -> "Bipar código" })
    }
}

private fun actionLabel(id: String): String = when (id) {
    "prevenda" -> "Adicionar à pré-venda"
    "inventory" -> "Salvar inventário"
    "conference" -> "Salvar conferência"
    "damage" -> "Registrar avaria"
    "print" -> "Adicionar à impressão"
    "movement" -> "Salvar movimentação"
    "expiry" -> "Salvar validade"
    else -> "Salvar"
}

private fun money(value: Double): String = String.format(Locale("pt", "BR"), "%.2f", value)
