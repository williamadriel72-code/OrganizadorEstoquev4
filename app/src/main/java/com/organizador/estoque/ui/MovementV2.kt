package com.organizador.estoque.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        val normalized = value.trim()
        code = normalized
        if (normalized.isBlank()) {
            found = null
            message = "Informe um código ou EAN"
            return
        }
        scope.launch {
            found = withContext(Dispatchers.IO) { repository.findExact(normalized) }
            message = if (found == null) "Produto não encontrado" else null
        }
    }

    fun confirmMovement() {
        val qty = parseNumberBr(quantity)
        if (qty == null || qty <= 0 || found == null) {
            message = "Quantidade inválida"
            return
        }
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    if (entry) repository.stockIn(code, qty, expiry.ifBlank { null })
                    else repository.stockOut(code, qty)
                }
            }.onSuccess {
                found = it
                message = "Operação concluída"
                onChanged()
            }.onFailure {
                message = it.message
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val desktop = maxWidth >= 900.dp

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .fillMaxSize()
                    .widthIn(max = 1040.dp)
                    .padding(horizontal = if (desktop) 28.dp else 16.dp, vertical = if (desktop) 24.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    if (entry) "Entrada de estoque" else "Saída de estoque",
                    fontSize = if (desktop) 31.sp else 27.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    if (entry) "Localize o produto e registre a entrada." else "Localize o produto e confirme a baixa do estoque.",
                    color = Color(0xFF9FB0C4),
                    fontSize = 13.sp
                )

                if (desktop) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Card(
                            Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF102238))
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("Localizar produto", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                OutlinedTextField(
                                    value = code,
                                    onValueChange = {
                                        code = it
                                        found = null
                                        message = null
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Código ou EAN") },
                                    singleLine = true
                                )
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    BarcodeCaptureButton(Modifier.weight(1f)) { locate(it) }
                                    Button(
                                        onClick = { locate(code) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("LOCALIZAR")
                                    }
                                }
                                found?.let { ProductMovementCard(it) }
                            }
                        }

                        Card(
                            Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF102238))
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    if (entry) "Dados da entrada" else "Dados da saída",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                OutlinedTextField(
                                    value = quantity,
                                    onValueChange = { quantity = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Quantidade") },
                                    singleLine = true
                                )
                                if (entry) {
                                    OutlinedTextField(
                                        value = expiry,
                                        onValueChange = { expiry = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("Validade opcional") },
                                        singleLine = true
                                    )
                                }
                                Button(
                                    onClick = ::confirmMovement,
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = found != null
                                ) {
                                    Text(if (entry) "CONFIRMAR ENTRADA" else "CONFIRMAR SAÍDA")
                                }
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = code,
                        onValueChange = {
                            code = it
                            found = null
                            message = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Código ou EAN") },
                        singleLine = true
                    )
                    BarcodeCaptureButton(Modifier.fillMaxWidth()) { locate(it) }
                    Button({ locate(code) }, Modifier.fillMaxWidth()) { Text("LOCALIZAR PRODUTO") }
                    found?.let { ProductMovementCard(it) }
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Quantidade") },
                        singleLine = true
                    )
                    if (entry) {
                        OutlinedTextField(
                            value = expiry,
                            onValueChange = { expiry = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Validade opcional") },
                            singleLine = true
                        )
                    }
                    Button(
                        onClick = ::confirmMovement,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = found != null
                    ) {
                        Text(if (entry) "CONFIRMAR ENTRADA" else "CONFIRMAR SAÍDA")
                    }
                }

                message?.let {
                    Text(
                        it,
                        color = if (it == "Operação concluída") Color(0xFF20C983) else Color(0xFFFFC857),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductMovementCard(product: Product) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1A2B))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(product.description, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                "Código ${product.code} • EAN ${product.ean ?: "-"}",
                color = Color(0xFF9FB0C4),
                fontSize = 12.sp
            )
            Text("Estoque atual: ${formatNumberBr(product.stock)}", fontWeight = FontWeight.SemiBold)
        }
    }
}
