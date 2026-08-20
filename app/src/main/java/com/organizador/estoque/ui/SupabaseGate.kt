package com.organizador.estoque.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.organizador.estoque.data.InventoryRepository
import com.organizador.estoque.data.SupabaseCatalogSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SupabaseGate(repository: InventoryRepository) {
    val context = LocalContext.current.applicationContext
    val sync = remember { SupabaseCatalogSync(context) }

    var screen by remember { mutableStateOf(if (sync.hasSavedSession()) "sync" else "login") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var localAvailable by remember { mutableStateOf(false) }
    var loginRequest by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        localAvailable = withContext(Dispatchers.IO) { repository.dashboardStats().products > 0 }
        if (sync.hasSavedSession()) {
            runCatching { sync.syncSavedSession() }
                .onSuccess { result ->
                    if (result != null) {
                        Toast.makeText(
                            context,
                            "Supabase sincronizado: ${result.products} produtos",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    screen = "app"
                }
                .onFailure { failure ->
                    if (localAvailable) {
                        Toast.makeText(
                            context,
                            "Sem sincronização agora. Usando dados locais.",
                            Toast.LENGTH_LONG
                        ).show()
                        screen = "app"
                    } else {
                        error = failure.message ?: "Não foi possível sincronizar"
                        sync.clearSession()
                        screen = "login"
                    }
                }
        }
    }

    LaunchedEffect(loginRequest) {
        if (loginRequest == 0) return@LaunchedEffect
        busy = true
        error = null
        runCatching { sync.signInAndSync(email, password) }
            .onSuccess { result ->
                Toast.makeText(
                    context,
                    "Sincronizado: ${result.products} produtos e ${result.expiries} validades",
                    Toast.LENGTH_LONG
                ).show()
                password = ""
                screen = "app"
            }
            .onFailure { failure ->
                error = failure.message ?: "Falha ao entrar no Supabase"
            }
        busy = false
    }

    when (screen) {
        "app" -> ModernInventoryApp(repository)
        "sync" -> SupabaseLoading()
        else -> SupabaseLogin(
            email = email,
            onEmailChange = { email = it },
            password = password,
            onPasswordChange = { password = it },
            busy = busy,
            error = error,
            localAvailable = localAvailable,
            onLogin = { loginRequest++ },
            onOffline = { screen = "app" }
        )
    }
}

@Composable
private fun SupabaseLoading() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF1677FF),
            background = Color(0xFF061321),
            surface = Color(0xFF10243A)
        )
    ) {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Sincronizando com o Supabase…")
                }
            }
        }
    }
}

@Composable
private fun SupabaseLogin(
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    busy: Boolean,
    error: String?,
    localAvailable: Boolean,
    onLogin: () -> Unit,
    onOffline: () -> Unit
) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF1677FF),
            background = Color(0xFF061321),
            surface = Color(0xFF10243A)
        )
    ) {
        Surface(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    Modifier.fillMaxWidth().widthIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Organizador Geral de Estoque",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "Entre para sincronizar produtos, estoque, valor e validade com o Supabase.",
                        color = Color(0xFF9FB0C4)
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = onEmailChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("E-mail") },
                        singleLine = true,
                        enabled = !busy
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Senha") },
                        singleLine = true,
                        enabled = !busy,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    if (!error.isNullOrBlank()) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        onClick = onLogin,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy && email.isNotBlank() && password.isNotBlank()
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Entrar e sincronizar")
                        }
                    }
                    if (localAvailable) {
                        OutlinedButton(
                            onClick = onOffline,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy
                        ) {
                            Text("Trabalhar com os dados locais")
                        }
                    }
                }
            }
        }
    }
}
