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
import com.stockmaster.clone.data.AwsCredentialStore
import com.stockmaster.clone.data.SavedCredentials
import com.stockmaster.clone.data.StockMasterDb
import com.stockmaster.clone.data.UserSession
import com.stockmaster.clone.data.importDatabaseSafely

internal data class AwsModuleDef(val id: String, val title: String, val permission: String?)

internal val awsModules = listOf(
    AwsModuleDef("price", "Busca de preço", "acessoBuscaPreco"),
    AwsModuleDef("restock", "Reposição", "acessoReposicao"),
    AwsModuleDef("prevenda", "Pré-venda", "acessoPrevenda"),
    AwsModuleDef("inventory", "Inventário", "acessoInventario"),
    AwsModuleDef("conference", "Conferência", "acessoConferencia"),
    AwsModuleDef("print", "Impressão", "acessoImpressao"),
    AwsModuleDef("damage", "Avaria", "acessoAvaria"),
    AwsModuleDef("movement", "Movimentação", "acessoMovimentacao"),
    AwsModuleDef("import_products_pdf", "Importar mercadorias – PDF", null),
    AwsModuleDef("import_expiry_pdf", "Importar datas de validade – PDF", null),
    AwsModuleDef("expiry", "Controle de validade", null)
)

internal val awsCardShape = RoundedCornerShape(22.dp)
internal val awsFieldShape = RoundedCornerShape(16.dp)

@Composable
fun AwsApp() {
    val context = LocalContext.current
    val db = remember { StockMasterDb(context.applicationContext) }
    val credentialStore = remember { AwsCredentialStore(context.applicationContext) }
    var savedCredentials by remember { mutableStateOf(credentialStore.load()) }
    var dbReady by remember {
        mutableStateOf(
            runCatching { db.hasDatabase().also { if (it) db.validateSchema() } }
                .getOrDefault(false)
        )
    }
    var user by remember { mutableStateOf<UserSession?>(null) }
    var screen by remember { mutableStateOf("login") }
    var message by remember { mutableStateOf("") }

    val databaseLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                db.importDatabaseSafely(context.applicationContext, uri)
                dbReady = true
                user = null
                screen = "login"
            }.onSuccess {
                message = "Banco importado com sucesso."
            }.onFailure { error ->
                dbReady = runCatching {
                    db.hasDatabase().also { if (it) db.validateSchema() }
                }.getOrDefault(false)
                message = (error.message ?: "Falha ao importar o banco.") +
                    if (dbReady) " Banco anterior mantido e disponível." else ""
            }
        }
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainerLowest
        ) {
            when {
                user == null || screen == "login" -> AwsLoginScreen(
                    dbReady = dbReady,
                    message = message,
                    savedCredentials = savedCredentials,
                    onImportDatabase = {
                        databaseLauncher.launch(
                            arrayOf("application/octet-stream", "application/x-sqlite3", "*/*")
                        )
                    },
                    onLogin = { login, pass ->
                        runCatching { db.authenticate(login, pass) }
                            .onSuccess { found ->
                                if (found == null) {
                                    message = "Usuário ou senha inválidos."
                                } else {
                                    runCatching { credentialStore.save(login, pass) }
                                        .onSuccess {
                                            savedCredentials = SavedCredentials(login.trim(), pass)
                                        }
                                    user = found
                                    screen = "dashboard"
                                    message = ""
                                }
                            }
                            .onFailure {
                                message = it.message ?: "Erro ao abrir o banco."
                            }
                    },
                    onForgetSaved = {
                        credentialStore.clear()
                        savedCredentials = null
                        message = "Login salvo removido."
                    }
                )

                screen == "dashboard" -> AwsDashboardScreen(
                    user = user!!,
                    onOpen = { screen = it },
                    onImportDatabase = {
                        databaseLauncher.launch(
                            arrayOf("application/octet-stream", "application/x-sqlite3", "*/*")
                        )
                    },
                    onLogout = {
                        user = null
                        screen = "login"
                    }
                )

                screen == "import_products_pdf" -> ProductPdfImportScreen(
                    db = db,
                    onBack = { screen = "dashboard" }
                )

                screen == "import_expiry_pdf" -> ExpiryPdfImportScreen(
                    db = db,
                    user = user!!,
                    onBack = { screen = "dashboard" }
                )

                else -> AwsModuleScreen(
                    module = awsModules.firstOrNull { it.id == screen } ?: awsModules.first(),
                    db = db,
                    user = user!!,
                    onBack = { screen = "dashboard" }
                )
            }
        }
    }
}

@Composable
private fun AwsLoginScreen(
    dbReady: Boolean,
    message: String,
    savedCredentials: SavedCredentials?,
    onImportDatabase: () -> Unit,
    onLogin: (String, String) -> Unit,
    onForgetSaved: () -> Unit
) {
    var login by remember(savedCredentials) { mutableStateOf(savedCredentials?.login.orEmpty()) }
    var pass by remember(savedCredentials) { mutableStateOf(savedCredentials?.password.orEmpty()) }

    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "AWS",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Text(
                "AWS – Gestão de Estoque",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = awsCardShape,
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "Acesso ao sistema",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (dbReady) "Banco de dados carregado. Entre com seu usuário."
                        else "Importe o banco de dados para liberar o acesso.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = onImportDatabase,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = awsFieldShape
                    ) {
                        Text(if (dbReady) "Trocar arquivo .db" else "Importar arquivo .db")
                    }

                    OutlinedTextField(
                        value = login,
                        onValueChange = { login = it },
                        label = { Text("Usuário") },
                        enabled = dbReady,
                        singleLine = true,
                        shape = awsFieldShape,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text("Senha") },
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = dbReady,
                        singleLine = true,
                        shape = awsFieldShape,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onLogin(login, pass) },
                        enabled = dbReady && login.isNotBlank() && pass.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                        shape = awsFieldShape
                    ) {
                        Text("Entrar", style = MaterialTheme.typography.titleMedium)
                    }

                    if (savedCredentials != null) {
                        Text(
                            "Login salvo neste aparelho",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        TextButton(onClick = onForgetSaved, modifier = Modifier.fillMaxWidth()) {
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
                                message,
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AwsDashboardScreen(
    user: UserSession,
    onOpen: (String) -> Unit,
    onImportDatabase: () -> Unit,
    onLogout: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .padding(top = 18.dp, bottom = 14.dp)
        ) {
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = awsCardShape) {
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Módulos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(awsModules.filter { module -> module.permission?.let { user.can(it) } ?: true }) { module ->
                    ElevatedCard(
                        onClick = { onOpen(module.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                module.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onImportDatabase,
                    modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                    shape = awsFieldShape
                ) { Text("Trocar banco") }
                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                    shape = awsFieldShape
                ) { Text("Sair") }
            }
        }
    }
}
