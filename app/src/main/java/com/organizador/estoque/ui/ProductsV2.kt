package com.organizador.estoque.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.organizador.estoque.data.InventoryRepository
import com.organizador.estoque.data.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun ProductsV2(repository: InventoryRepository, refreshKey: Int, initialFilter: String = "all") {
    val listState = rememberLazyListState()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(initialFilter) }
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }

    LaunchedEffect(initialFilter) { filter = initialFilter }
    LaunchedEffect(query, filter, refreshKey) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isNotEmpty()) delay(250)
        products = withContext(Dispatchers.IO) {
            val limit = if (normalizedQuery.isBlank()) 150 else 250
            repository.searchProducts(normalizedQuery, limit, 0, filter)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text("Produtos", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text("Pesquise, confira e localize mercadorias", color = Color(0xFF9FB0C4), fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Pesquisar código, EAN ou descrição") }, singleLine = true, shape = RoundedCornerShape(16.dp))
        Spacer(Modifier.height(10.dp))
        BarcodeCaptureButton(Modifier.fillMaxWidth()) { code -> query = code }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FilterChip(filter == "all", { filter = "all" }, { Text("Todos") })
            FilterChip(filter == "low", { filter = "low" }, { Text("Baixos") })
            FilterChip(filter == "zero", { filter = "zero" }, { Text("Zerados") })
            FilterChip(filter == "negative", { filter = "negative" }, { Text("Negativos") })
        }
        Spacer(Modifier.height(10.dp))
        Text("${products.size} produto(s)", color = Color(0xFF9FB0C4), fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 14.dp)) {
            items(products, key = { it.code }, contentType = { "product" }) { p ->
                val accent = when {
                    p.stock < 0.0 -> Color(0xFFFF7A59)
                    p.stock == 0.0 -> Color(0xFFFF5368)
                    p.stock <= 5.0 -> Color(0xFFFFB938)
                    else -> Color(0xFF20C983)
                }
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF102238))) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(p.description, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Código ${p.code} • EAN ${p.ean ?: "-"}", color = Color(0xFF9FB0C4), fontSize = 12.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Grupo ${p.groupCode ?: "-"}", color = Color(0xFFB8C5D3), fontSize = 13.sp)
                            Text(formatNumberBr(p.stock), color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    }
}
