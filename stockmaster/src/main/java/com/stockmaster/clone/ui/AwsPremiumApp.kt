package com.aws.gestaoestoque.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aws.gestaoestoque.data.AwsCredentialStore
import com.aws.gestaoestoque.data.AwsDb
import com.aws.gestaoestoque.data.SavedCredentials
import com.aws.gestaoestoque.data.UserSession

private val PremiumBg = Color(0xFF061936)
private val PremiumBgDeep = Color(0xFF031027)
private val PremiumPanel = Color(0xFF0B2148)
private val PremiumPurple = Color(0xFF8755F4)
private val PremiumPurple2 = Color(0xFFA56FFF)
private val PremiumLine = Color(0xFF5D5AA4)
private val PremiumWhite = Color(0xFFF8FAFF)
private val PremiumMuted = Color(0xFFA9B5CB)
private val PremiumPage = Color(0xFFF6F7FB)

@Composable
fun AwsPremiumApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = remember { AwsDb(context.applicationContext) }
    val store = remember { AwsCredentialStore(context.applicationContext) }
    var saved by remember { mutableStateOf(store.load()) }
    val dbReady = remember {
        runCatching { db.hasDatabase().also { if (it) db.validateSchema() } }.getOrDefault(false)
    }
    var user by remember { mutableStateOf<UserSession?>(null) }
    var screen by remember { mutableStateOf("login") }
    var message by remember { mutableStateOf("") }

    fun goBack() {
        when {
            user == null || screen == "login" -> Unit
            screen == "dashboard" -> {
                user = null
                screen = "login"
            }
            else -> screen = "dashboard"
        }
    }

    BackHandler(enabled = user != null && screen != "login") { goBack() }
    val internal = user != null && screen != "login" && screen != "dashboard"

    AwsTheme {
        Scaffold(
            containerColor = if (screen == "login") PremiumBgDeep else PremiumPage,
            bottomBar = {
                if (internal) {
                    Surface(color = Color.White, shadowElevation = 10.dp) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            PremiumButton(
                                text = "←  Voltar",
                                onClick = { goBack() },
                                modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)
                            )
                        }
                    }
                }
            }
        ) { inset ->
            Box(Modifier.fillMaxSize().padding(inset)) {
                when {
                    user == null || screen == "login" -> PremiumLogin(
                        dbReady = dbReady,
                        saved = saved,
                        message = message,
                        onLogin = { login, password ->
                            runCatching { db.authenticate(login, password) }
                                .onSuccess { found ->
                                    if (found == null) {
                                        message = "Usuário ou senha inválidos."
                                    } else {
                                        runCatching { store.save(login, password) }
                                            .onSuccess { saved = SavedCredentials(login.trim(), password) }
                                        user = found
                                        screen = "dashboard"
                                        message = ""
                                    }
                                }
                                .onFailure { message = it.message ?: "Erro ao abrir o banco AWS." }
                        },
                        onForget = {
                            store.clear()
                            saved = null
                            message = "Login salvo removido."
                        }
                    )

                    screen == "dashboard" -> PremiumDashboard(
                        user = user!!,
                        onOpen = { screen = it },
                        onLogout = {
                            user = null
                            screen = "login"
                        }
                    )

                    screen == "inventory" || screen == "damage" -> AwsInventoryDamageSessionScreen(
                        mode = screen,
                        db = db,
                        user = user!!
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
private fun PremiumLogin(
    dbReady: Boolean,
    saved: SavedCredentials?,
    message: String,
    onLogin: (String, String) -> Unit,
    onForget: () -> Unit
) {
    var login by remember(saved) { mutableStateOf(saved?.login.orEmpty()) }
    var password by remember(saved) { mutableStateOf(saved?.password.orEmpty()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(PremiumBgDeep, PremiumBg))),
        contentAlignment = Alignment.Center
    ) {
        PremiumDecorations()

        LazyColumn(
            modifier = Modifier.fillMaxSize().widthIn(max = 500.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                PremiumCubeLogo()
                Spacer(Modifier.height(18.dp))
                Text(
                    "AWS",
                    color = PremiumPurple2,
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
                Box(
                    Modifier
                        .width(190.dp)
                        .height(3.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, PremiumPurple2, Color.Transparent)
                            ),
                            RoundedCornerShape(50)
                        )
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "AWS – Gestão de Estoque",
                    color = PremiumWhite,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(26.dp))
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = PremiumPanel.copy(alpha = 0.95f),
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, PremiumLine),
                    shadowElevation = 14.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        PremiumField(
                            value = login,
                            onValueChange = { login = it },
                            label = "Usuário",
                            placeholder = "Digite seu usuário",
                            leading = "♙",
                            enabled = dbReady
                        )
                        PremiumField(
                            value = password,
                            onValueChange = { password = it },
                            label = "Senha",
                            placeholder = "Digite sua senha",
                            leading = "▣",
                            password = true,
                            enabled = dbReady
                        )

                        PremiumButton(
                            text = "Entrar",
                            onClick = { onLogin(login, password) },
                            enabled = dbReady && login.isNotBlank() && password.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(Modifier.weight(1f), color = Color.White.copy(alpha = 0.20f))
                            Text("  ou  ", color = PremiumMuted, style = MaterialTheme.typography.bodySmall)
                            HorizontalDivider(Modifier.weight(1f), color = Color.White.copy(alpha = 0.20f))
                        }

                        if (saved != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = PremiumPurple,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("✓", color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontWeight = FontWeight.Black)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Login salvo neste aparelho", color = PremiumWhite, fontWeight = FontWeight.SemiBold)
                                    Text("Você permanecerá conectado neste dispositivo.", color = PremiumMuted, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            TextButton(onClick = onForget, modifier = Modifier.fillMaxWidth()) {
                                Text("Esquecer login salvo", color = Color(0xFFD7C7FF))
                            }
                        } else {
                            Text(
                                "Seu login pode ser salvo com segurança após o primeiro acesso.",
                                color = PremiumMuted,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (!dbReady) {
                            Text("Banco integrado indisponível.", color = Color(0xFFFFA8A8), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        }
                        if (message.isNotBlank()) {
                            Surface(
                                color = if (message.contains("inválid", true)) Color(0xFF5A2430) else Color(0xFF2B2A67),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(message, color = Color.White, modifier = Modifier.padding(12.dp), textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(20.dp))
                Text("♢  Seus dados estão protegidos", color = Color(0xFF92A1BB), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PremiumField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    leading: String,
    password: Boolean = false,
    enabled: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = Color(0xFF8997B1)) },
        leadingIcon = { Text(leading, color = Color.White, style = MaterialTheme.typography.titleLarge) },
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        singleLine = true,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            disabledTextColor = Color(0xFFB3BDD0),
            focusedBorderColor = PremiumPurple2,
            unfocusedBorderColor = Color(0xFF647398),
            focusedLabelColor = Color(0xFFDCCEFF),
            unfocusedLabelColor = Color(0xFFC3CCE0),
            cursorColor = PremiumPurple2,
            focusedContainerColor = Color.White.copy(alpha = 0.018f),
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp)
    )
}

@Composable
private fun PremiumButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        shadowElevation = if (enabled) 6.dp else 0.dp,
        modifier = modifier.heightIn(min = 56.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (enabled) Brush.horizontalGradient(listOf(PremiumPurple, PremiumPurple2))
                    else Brush.horizontalGradient(listOf(Color(0xFF767B8A), Color(0xFF8B8F99)))
                )
                .padding(vertical = 16.dp, horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun PremiumDashboard(
    user: UserSession,
    onOpen: (String) -> Unit,
    onLogout: () -> Unit
) {
    val modules = awsModules.filter { m -> m.permission?.let { user.can(it) } ?: true }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(PremiumPage),
        contentPadding = PaddingValues(bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(PremiumBgDeep, PremiumBg)))
                    .padding(horizontal = 20.dp, vertical = 28.dp)
            ) {
                Column(Modifier.fillMaxWidth().widthIn(max = 760.dp).align(Alignment.Center)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PremiumCubeLogo(Modifier.size(56.dp), compact = true)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Olá, ${user.name}",
                                color = Color.White,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black
                            )
                            Text("Escolha uma função para continuar", color = Color(0xFFC8D2E5))
                        }
                        Surface(color = PremiumPurple, shape = RoundedCornerShape(18.dp)) {
                            Text("3", color = Color.White, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        items(modules.chunked(2)) { pair ->
            Row(
                modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                pair.forEach { module ->
                    val visual = awsModuleVisual(module.id)
                    ElevatedCard(
                        onClick = { onOpen(module.id) },
                        modifier = Modifier.weight(1f).heightIn(min = 150.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            AwsModuleIcon(visual, Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(module.title, color = AwsText, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                            Text(visual.subtitle, color = AwsMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        item {
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(horizontal = 16.dp).heightIn(min = 50.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Sair")
            }
        }
    }
}

@Composable
private fun PremiumCubeLogo(modifier: Modifier = Modifier, compact: Boolean = false) {
    Box(
        modifier = modifier
            .size(if (compact) 58.dp else 104.dp)
            .clip(RoundedCornerShape(if (compact) 20.dp else 30.dp))
            .background(Brush.radialGradient(listOf(Color(0xFF6445C8), Color(0xFF142E67), Color(0xFF08172F)))),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize().padding(if (compact) 11.dp else 20.dp)) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val top = h * 0.18f
            val mid = h * 0.42f
            val bottom = h * 0.80f
            val left = w * 0.20f
            val right = w * 0.80f

            val topPath = Path().apply {
                moveTo(cx, top)
                lineTo(right, mid)
                lineTo(cx, h * 0.62f)
                lineTo(left, mid)
                close()
            }
            val leftPath = Path().apply {
                moveTo(left, mid)
                lineTo(cx, h * 0.62f)
                lineTo(cx, bottom)
                lineTo(left, h * 0.60f)
                close()
            }
            val rightPath = Path().apply {
                moveTo(right, mid)
                lineTo(cx, h * 0.62f)
                lineTo(cx, bottom)
                lineTo(right, h * 0.60f)
                close()
            }
            drawPath(topPath, Color(0xFFB894FF))
            drawPath(leftPath, Color(0xFF7455DE))
            drawPath(rightPath, Color(0xFF4D79F2))
            drawLine(Color(0xFFE4D5FF), Offset(cx, top), Offset(right, mid), strokeWidth = w * 0.035f)
            drawLine(Color(0xFF8FB5FF), Offset(right, mid), Offset(cx, bottom), strokeWidth = w * 0.025f)
            drawLine(Color(0xFFD6C5FF), Offset(left, mid), Offset(cx, bottom), strokeWidth = w * 0.025f)
        }
    }
}

@Composable
private fun PremiumDecorations() {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 70.dp, y = (-55).dp)
                .size(230.dp)
                .background(Color.White.copy(alpha = 0.025f), RoundedCornerShape(115.dp))
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-80).dp, y = 75.dp)
                .size(245.dp)
                .background(PremiumPurple.copy(alpha = 0.07f), RoundedCornerShape(120.dp))
        )
    }
}
