package com.organizador.estoque.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.organizador.estoque.data.InventoryRepository
import com.organizador.estoque.data.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MovementV2(repository: InventoryRepository, entry: Boolean, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var code by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf("1,00") }
    var expiry by rememberSaveable { mutableStateOf("") }
    var found by remember { mutableStateOf<Product?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun locate(value: String) {
        code = value
        scope.launch { found = withContext(Dispatchers.IO) { repository.findExact(value) }; message = if (found == null) "Produto não encontrado" else null }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (entry) "Entrada de estoque" else "Saída de estoque", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(code, { code = it }, Modifier.fillMaxWidth(), label = { Text("Código ou EAN") })
        BarcodeCaptureButton(Modifier.fillMaxWidth()) { locate(it) }
        Button({ locate(code) }, Modifier.fillMaxWidth()) { Text("LOCALIZAR PRODUTO") }
        found?.let { Text("${it.description} • Estoque ${formatNumberBr(it.stock)}") }
        OutlinedTextField(quantity, { quantity = it }, Modifier.fillMaxWidth(), label = { Text("Quantidade") })
        if (entry) OutlinedTextField(expiry, { expiry = it }, Modifier.fillMaxWidth(), label = { Text("Validade opcional") })
        Button({
            val qty = parseNumberBr(quantity)
            if (qty == null || qty <= 0 || found == null) message = "Quantidade inválida"
            else scope.launch {
                runCatching { withContext(Dispatchers.IO) { if (entry) repository.stockIn(code, qty, expiry.ifBlank { null }) else repository.stockOut(code, qty) } }
                    .onSuccess { found = it; message = "Operação concluída"; onChanged() }
                    .onFailure { message = it.message }
            }
        }, Modifier.fillMaxWidth(), enabled = found != null) { Text(if (entry) "CONFIRMAR ENTRADA" else "CONFIRMAR SAÍDA") }
        message?.let { Text(it) }
    }
}
