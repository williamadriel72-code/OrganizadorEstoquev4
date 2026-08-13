package com.organizador.estoque.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.organizador.estoque.data.DashboardStats
import com.organizador.estoque.data.InventoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DashboardV2(repository: InventoryRepository, refreshKey: Int, openProducts: () -> Unit) {
    var stats by remember { mutableStateOf(DashboardStats()) }
    LaunchedEffect(refreshKey) { stats = withContext(Dispatchers.IO) { repository.dashboardStats() } }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Organizador Geral de Estoque", fontSize = 28.sp, fontWeight = FontWeight.Bold) }
        item { Text("Controle rápido, organizado e visual", color = Color.LightGray) }
        item { V2Card("Produtos", stats.products.toString(), openProducts) }
        item { V2Card("Estoque total", formatNumberBr(stats.totalStock), openProducts) }
        item { V2Card("Estoque baixo", stats.lowStock.toString(), openProducts) }
        item { V2Card("Estoque zerado", stats.zeroStock.toString(), openProducts) }
        item { V2Card("Sem endereço", stats.withoutAddress.toString(), openProducts) }
        item { V2Card("Vencem em 7 dias", stats.expiring7.toString(), openProducts) }
        item { V2Card("Vencem em 30 dias", stats.expiring30.toString(), openProducts) }
        item { V2Card("Vencem em 60 dias", stats.expiring60.toString(), openProducts) }
    }
}

@Composable
private fun V2Card(title: String, value: String, click: () -> Unit) {
    Card(onClick = click, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF43A0FF))
        }
    }
}
