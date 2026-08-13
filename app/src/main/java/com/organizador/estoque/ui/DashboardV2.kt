package com.organizador.estoque.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.organizador.estoque.data.DashboardStats
import com.organizador.estoque.data.InventoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val CardDark = Color(0xFF102238)
private val CardBlue = Color(0xFF0D63E6)
private val Green = Color(0xFF20C983)
private val Yellow = Color(0xFFFFB938)
private val Red = Color(0xFFFF5368)
private val Purple = Color(0xFF8D63F6)
private val Muted = Color(0xFF9FB0C4)

@Composable
fun DashboardV2(repository: InventoryRepository, refreshKey: Int, openProducts: () -> Unit) {
    var stats by remember { mutableStateOf(DashboardStats()) }
    LaunchedEffect(refreshKey) { stats = withContext(Dispatchers.IO) { repository.dashboardStats() } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Olá! 👋", color = Muted, fontSize = 15.sp)
                    Text("Organizador de Estoque", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                }
                Surface(Modifier.size(48.dp), shape = CircleShape, color = CardDark) {
                    Box(contentAlignment = Alignment.Center) { Text("🔔", fontSize = 19.sp) }
                }
            }
        }

        item {
            Button(
                onClick = openProducts,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CardBlue)
            ) { Text("⌕  PESQUISAR PRODUTO", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("▣", "Produtos", formatIntegerBr(stats.products), Color(0xFF40A0FF), Modifier.weight(1f), openProducts)
                MetricCard("⚠", "Estoque Baixo", formatIntegerBr(stats.lowStock), Yellow, Modifier.weight(1f), openProducts)
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("●", "Zerados", formatIntegerBr(stats.zeroStock), Red, Modifier.weight(1f), openProducts)
                MetricCard("⌂", "Sem Endereço", formatIntegerBr(stats.withoutAddress), Purple, Modifier.weight(1f), openProducts)
            }
        }

        item { Text("Validades", fontSize = 19.sp, fontWeight = FontWeight.Bold) }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(42.dp), shape = RoundedCornerShape(12.dp), color = Color(0xFF17314D)) {
                            Box(contentAlignment = Alignment.Center) { Text("◷", color = Color(0xFF58A9FF), fontSize = 22.sp) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Acompanhar validades", color = Muted, fontSize = 13.sp)
                            Text(
                                formatIntegerBr(stats.expiring7 + stats.expiring30 + stats.expiring60 + stats.expired),
                                fontSize = 27.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF17314D)) {
                            Text("Ver tudo  ›", Modifier.padding(horizontal = 13.dp, vertical = 8.dp), color = Color(0xFF62AEFF), fontWeight = FontWeight.SemiBold)
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1B3550))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        ExpiryMini("Vencidos", formatIntegerBr(stats.expired), Red)
                        ExpiryMini("7 dias", formatIntegerBr(stats.expiring7), Yellow)
                        ExpiryMini("30 dias", formatIntegerBr(stats.expiring30), Green)
                        ExpiryMini("60 dias", formatIntegerBr(stats.expiring60), Color(0xFF59A8FF))
                    }
                }
            }
        }

        item { Text("Importar dados", fontSize = 19.sp, fontWeight = FontWeight.Bold) }
        item { PdfImportBar(repository) }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun MetricCard(icon: String, title: String, value: String, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.height(140.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Surface(Modifier.size(42.dp), shape = RoundedCornerShape(12.dp), color = accent.copy(alpha = 0.16f)) {
                Box(contentAlignment = Alignment.Center) { Text(icon, color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            }
            Column {
                Text(title, color = Muted, fontSize = 13.sp)
                Text(value, color = accent, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun ExpiryMini(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Muted, fontSize = 11.sp)
    }
}
