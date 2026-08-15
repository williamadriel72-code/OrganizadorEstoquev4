package com.aws.gestaoestoque.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aws.gestaoestoque.data.AwsCredentialStore
import com.aws.gestaoestoque.data.AwsDb
import com.aws.gestaoestoque.data.SavedCredentials
import com.aws.gestaoestoque.data.UserSession

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
    AwsModuleDef("expiry", "Controle de validade", null)
)

internal val awsCardShape = RoundedCornerShape(22.dp)
internal val awsFieldShape = RoundedCornerShape(16.dp)

@Composable
fun AwsApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = remember { AwsDb(context.applicationContext) }
    val credentialStore = remember { AwsCredentialStore(context.applicationContext) }
    var savedCredentials by remember { mutableStateOf(credentialStore.load()) }
    val dbReady = remember {
        runCatching { db.hasDatabase().also { if (it) db.validateSchema() } }
            .getOrDefault(false)
    }
    var user by remember { mutableStateOf<UserSession?>(null) }
    var screen by remember { mutableStateOf("login") }
    var message by remember { mutableStateOf("") }

    fun goBack() {
        if (user == null || screen == "login") return
        if (screen == "dashboard") {
            user = null
            screen = "login"
        } else {
            screen = "dashboard"
        }
    }

    BackHandler(enabled = user != null && screen != "login") { goBack() }
    val showBottomBack = user != null && screen != "login" && screen != "dashboard"

    AwsTheme {
        Scaffold(
            containerColor = AwsBackground,
            bottomBar = {
                if (showBottomBack) {
                    Surface(color = Color.White, shadowElevation = 10.dp) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AwsGradientButton(
                                text = "←  Voltar",
                                onClick = { goBack() },
                                modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(AwsBackground)
            ) {
                when {
                    user == null || screen == "login" -> AwsLoginScreen(
                        dbReady = dbReady,
                        message = message,
                        savedCredentials = savedCredentials,
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
                                .onFailure { message = it.message ?: "Erro ao abrir o banco AWS." }
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
                        onLogout = {
                            user = null
                            screen = "login"
                        }
                    )

                    else -> AwsModuleScreen(
                        module = awsModules.firstOrNull { it.id == screen } ?: awsModules.first(),
                        db = db,
                        user = user!!
                    )
                }
            }
        }
    }
}

@Composable
private fun AwsLoginScreen(
    dbReady: Boolean,
    message: String,
    savedCredentials: SavedCredentials?,
    onLogin: (String, String) -> Unit,
    onForgetSaved: () -> Unit
) {
    var login by remember(savedCredentials) { mutableStateOf(savedCredentials?.login.orEmpty()) }
    var pass by remember(savedCredentials) { mutableStateOf(savedCredentials?.password.orEmpty()) }

    Box(
        modifier = Modifier.fillMaxSize().background(AwsHeaderGradient),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 70.dp, y = (-45).dp)
                .size(210.dp)
                .background(Color.White.copy(alpha = 0.035f), RoundedCornerShape(100.dp))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-75).dp, y = 70.dp)
                .size(230.dp)
                .background(AwsPurple.copy(alpha = 0.08f), RoundedCornerShape(110.dp))
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().widthIn(max = 540.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                AwsBrandMark()
                Spacer(Modifier.height(16.dp))
                Text(
                    "AWS",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = AwsPurpleBright,
                    textAlign = TextAlign.Center
                )
                Text(
                    "AWS – Gestão de Estoque",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0xFF0C2146).copy(alpha = 0.92f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AwsPurpleBright.copy(alpha = 0.55f)),
                    shadowElevation = 12.dp
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            "Acesso ao sistema",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            if (dbReady) "Entre com seu usuário para continuar."
                            else "Não foi possível carregar o banco integrado.",
                            color = if (dbReady) Color(0xFFC9D2E3) else Color(0xFFFFB4B4)
                        )

                        val fieldColors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = Color(0xFFD7C7FF),
                            unfocusedLabelColor = Color(0xFFB7C1D5),
                            focusedBorderColor = AwsPurpleBright,
                            unfocusedBorderColor = Color(0xFF5B6B8E),
                            cursorColor = AwsPurpleBright,
                            focusedContainerColor = Color.White.copy(alpha = 0.025f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.015f)
                        )

                        OutlinedTextField(
                            value = login,
                            onValueChange = { login = it },
                            label = { Text("Usuário") },
                            placeholder = { Text("Digite seu usuário", color = Color(0xFF8794AD)) },
                            enabled = dbReady,
                            singleLine = true,
                            shape = awsFieldShape,
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = pass,
                            onValueChange = { pass = it },
                            label = { Text("Senha") },
                            placeholder = { Text("Digite sua senha", color = Color(0xFF8794AD)) },
                            visualTransformation = PasswordVisualTransformation(),
                            enabled = dbReady,
                            singleLine = true,
                            shape = awsFieldShape,
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )

                        AwsGradientButton(
                            text = "Entrar",
                            onClick = { onLogin(login, pass) },
                            enabled = dbReady && login.isNotBlank() && pass.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (savedCredentials != null) {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.16f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(7.dp),
                                    color = AwsPurple
                                ) {
                                    Text("✓", modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp), color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Login salvo neste aparelho", color = Color.White, fontWeight = FontWeight.SemiBold)
                                    Text("Seus dados ficam protegidos neste dispositivo.", color = Color(0xFF9EABC1), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            TextButton(onClick = onForgetSaved, modifier = Modifier.fillMaxWidth()) {
                                Text("Esquecer login salvo", color = Color(0xFFD7C7FF))
                            }
                        }

                        if (message.isNotBlank()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = if (message.contains("inválid", true)) AwsRed.copy(alpha = 0.22f) else AwsPurple.copy(alpha = 0.20f)
                            ) {
                                Text(message, modifier = Modifier.padding(12.dp), color = Color.White, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(18.dp))
                Text("◈  Seus dados estão protegidos", color = Color(0xFF93A2BC), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AwsDashboardScreen(
    user: UserSession,
    onOpen: (String) -> Unit,
    onLogout: () -> Unit
) {
    val visibleModules = awsModules.filter { module -> module.permission?.let { user.can(it) } ?: true }

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
                    .padding(horizontal = 20.dp, vertical = 26.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).align(Alignment.Center)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AwsBrandMark(modifier = Modifier.size(54.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Olá, ${user.name}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                "Escolha uma função para continuar",
                                color = Color(0xFFC6D0E3),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(horizontal = 16.dp)
            ) {
                Text("Módulos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = AwsText)
                Text("Acesso rápido às funções do estoque", color = AwsMuted)
            }
        }

        items(visibleModules.chunked(2)) { rowModules ->
            Row(
                modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowModules.forEach { module ->
                    val visual = awsModuleVisual(module.id)
                    ElevatedCard(
                        onClick = { onOpen(module.id) },
                        modifier = Modifier.weight(1f).heightIn(min = 142.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            AwsModuleIcon(visual)
                            Spacer(Modifier.height(12.dp))
                            Text(module.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = AwsText)
                            Spacer(Modifier.height(4.dp))
                            Text(visual.subtitle, color = AwsMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (rowModules.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        item {
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(horizontal = 16.dp).heightIn(min = 50.dp),
                shape = awsFieldShape,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AwsMuted)
            ) {
                Text("Sair")
            }
        }
    }
}
