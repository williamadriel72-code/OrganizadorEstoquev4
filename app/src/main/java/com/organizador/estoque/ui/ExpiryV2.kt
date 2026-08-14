package com.organizador.estoque.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.organizador.estoque.data.ExpiryDetail
import com.organizador.estoque.data.InventoryInsights
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ExpiryV2() {
    val insights = remember { InventoryInsights(LocalContext.current) }
    var filter by remember { mutableStateOf("all") }
    var rows by remember { mutableStateOf<List<ExpiryDetail>>(emptyList()) }
    LaunchedEffect(filter) { rows = withContext(Dispatchers.IO) { insights.expiryDetails(filter) } }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Validades", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text("Veja quais produtos estão vencidos ou próximos do vencimento", color = Color(0xFF9FB0C4))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(filter == "all", { filter = "all" }, { Text("Todas") })
            FilterChip(filter == "expired", { filter = "expired" }, { Text("Vencidos") })
            FilterChip(filter == "7", { filter = "7" }, { Text("7 dias") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(filter == "30", { filter = "30" }, { Text("8-30 dias") })
            FilterChip(filter == "60", { filter = "60" }, { Text("31-60 dias") })
        }
        Spacer(Modifier.height(10.dp))
        Text("${rows.size} validade(s)", color = Color(0xFF9FB0C4))
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF102238))) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.description, fontWeight = FontWeight.Bold)
                        Text("Código ${item.productCode} • EAN ${item.ean ?: "-"}", color = Color(0xFF9FB0C4), fontSize = 12.sp)
                        Text("Validade: ${item.expiryDate}", color = Color(0xFFFFB938), fontWeight = FontWeight.Bold)
                        Text("Lote: ${formatNumberBr(item.quantity)} • Estoque: ${formatNumberBr(item.stock)}", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
