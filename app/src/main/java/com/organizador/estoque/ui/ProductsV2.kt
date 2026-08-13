package com.organizador.estoque.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.organizador.estoque.data.InventoryRepository
import com.organizador.estoque.data.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProductsV2(repository: InventoryRepository, refreshKey: Int) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }

    fun reload() {
        scope.launch { products = withContext(Dispatchers.IO) { repository.searchProducts(query, 200, 0, filter) } }
    }
    LaunchedEffect(query, filter, refreshKey) { reload() }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Produtos", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Pesquisar código, EAN ou descrição") }, singleLine = true)
        Spacer(Modifier.height(8.dp))
        BarcodeCaptureButton(Modifier.fillMaxWidth()) { code -> query = code }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(filter == "all", { filter = "all" }, { Text("Todos") })
            FilterChip(filter == "low", { filter = "low" }, { Text("Baixos") })
            FilterChip(filter == "zero", { filter = "zero" }, { Text("Zerados") })
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(products, key = { it.code }) { p ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(p.description, fontWeight = FontWeight.Bold)
                        Text("Código ${p.code} • EAN ${p.ean ?: "-"}", color = Color.LightGray)
                        Text("Estoque: ${formatNumberBr(p.stock)} • Grupo: ${p.groupCode ?: "-"}")
                        if (p.stock == 0.0) Text("ZERADO", color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
