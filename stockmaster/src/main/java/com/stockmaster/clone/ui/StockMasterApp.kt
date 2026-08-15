package com.stockmaster.clone.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.stockmaster.clone.data.AwsCredentialStore
import com.stockmaster.clone.data.ProductRow
import com.stockmaster.clone.data.SavedCredentials
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

private val cardShape = RoundedCornerShape(22.dp)
private val fieldShape = RoundedCornerShape(16.dp)

@Composable
fun StockMasterApp() {
    val context = LocalContext.current
    val db = remember { StockMasterDb(context.applicationContext) }
    val credentialStore = remember { AwsCredentialStore(context.applicationContext) }
    var savedCredentials by remember { mutableStateOf(credentialStore.load()) }
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
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainerLowest
        ) {
            when {
                user == null || screen == "login" -> LoginScreen(
                    dbReady = dbReady,
                    message = message,
                    savedCredentials = savedCredentials,
                    onImport = { importLauncher.launch(arrayOf("application/octet-stream", "application/x-sqlite3", "*/*")) },
                    onLogin = { login, pass ->
                        runCatching { db.authenticate(login, pass) }
                            .onSuccess { found ->
                                if (found == null) {
                                    message = "Usuário ou senha inválidos."
                                } else {
                                    runCatching { credentialStore.save(login, pass) }
                                        .onSuccess { savedCredentials = SavedCredentials(login.trim(), pass) }
                                    user = found
                                    screen = "dashboard"
                                    message = ""
                                }
                            }
                            .onFailure { message = it.message ?: "Erro ao abrir o banco." }
                    },
                    onForgetSaved = {
                        credentialStore.clear()
                        savedCredentials = null
                        message = "Login salvo removido."
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
    savedCredentials: SavedCredentials?,
    onImport: () -> Unit,
    onLogin: (String, String) -> Unit,
    onForgetSaved: () -> Unit
) {
    var login by remember(savedCredentials) { mutableStateOf(savedCredentials?.login.orEmpty()) }
    var pass by remember(savedCredentials) { mutableStateOf(savedCredentials?.password.orEmpty()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "AWS",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "AWS – Gestão de Estoque",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = cardShape,
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Acesso ao sistema",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (dbReady) "Banco de dados carregado. Entre com seu usuário." else "Importe o banco de dados para liberar o acesso.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = onImport,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                        shape = fieldShape
                    ) {
                        Text(if (dbReady) "Trocar arquivo .db" else "Importar arquivo .db")
                    }

                    OutlinedTextField(
                        value = login,
                        onValueChange = { login = it },
                        label = { Text("Usuário") },
                        enabled = dbReady,
                        singleLine = true,
                        shape = fieldShape,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text("Senha") },
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = dbReady,
                        singleLine = true,
                        shape = fieldShape,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { onLogin(login, pass) },
                        enabled = dbReady && login.isNotBlank() && pass.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp),
                        shape = fieldShape
                    ) {
                        Text("Entrar", style = MaterialTheme.typography.titleMedium)
                    }

                    if (savedCredentials != null) {
                        Text(
                            text = "Login salvo neste aparelho",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        TextButton(
                            onClick = onForgetSaved,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Esquecer login salvo")
                        }
                    }

                    if (message.isNotBlank()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = message,
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .padding(top = 18.dp, bottom = 14.dp)
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = cardShape
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "AWS",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Olá, ${user.name}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Escolha uma função para continuar",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Módulos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(modules.filter { module -> module.permission?.let { user.can(it) } ?: true }) { module ->
                    ElevatedCard(
                        onClick = { onOpen(module.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                module.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "›",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onImport,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 50.dp),
                    shape = fieldShape
                ) { Text("Trocar banco") }
                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 50.dp),
                    shape = fieldShape
                ) { Text("Sair") }
            }
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

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = cardShape
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onBack,
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Voltar") }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
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
            }

            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = cardShape
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Pesquisar mercadoria",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("Código, EAN ou produto") },
                            singleLine = true,
                            shape = fieldShape,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { search(query) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 50.dp),
                                shape = fieldShape
                            ) { Text("Pesquisar") }
                            BarcodeButton(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 50.dp),
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
                        shape = cardShape,
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
                            Text("Preço gôndola: R$ ${money(product.price)}", fontWeight = FontWeight.SemiBold)
                            Text("Preço à vista: R$ ${money(product.cashPrice)}", fontWeight = FontWeight.SemiBold)
                            Text("Estoque: ${product.stock?.toString() ?: "não informado"}")
                        }
                    }
                }
            }

            if (module.id !in setOf("price", "restock")) {
                item {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = cardShape
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "Dados do registro",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedTextField(
                                value = qty,
                                onValueChange = { qty = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                                label = { Text("Quantidade") },
                                singleLine = true,
                                shape = fieldShape,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (module.id == "expiry") {
                                OutlinedTextField(
                                    value = expiry,
                                    onValueChange = { expiry = it },
                                    label = { Text("Validade (AAAA-MM-DD)") },
                                    singleLine = true,
                                    shape = fieldShape,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = lot,
                                    onValueChange = { lot = it },
                                    label = { Text("Lote") },
                                    singleLine = true,
                                    shape = fieldShape,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            if (module.id == "movement") {
                                OutlinedTextField(
                                    value = observation,
                                    onValueChange = { observation = it },
                                    label = { Text("Observação") },
                                    shape = fieldShape,
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp),
                                shape = fieldShape
                            ) { Text(actionLabel(module.id)) }
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
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (module.id == "prevenda" && saleItems.isNotEmpty()) {
                item {
                    val total = saleItems.sumOf { (product, amount) -> product.price * amount }
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(
                            "Itens na pré-venda: ${saleItems.size}  •  Total: R$ ${money(total)}",
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
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                row.description.ifBlank { "Produto ${row.productId}" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Validade: ${row.expiry}  •  Lote: ${row.lot.ifBlank { "-" }}  •  Qtde: ${row.quantity}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(results) { product ->
                    ElevatedCard(
                        onClick = { selected = product },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    product.description,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    "${product.id} • ${product.ean.ifBlank { "sem EAN" }} • R$ ${money(product.price)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                "›",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
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
        modifier = modifier,
        shape = fieldShape
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
