package com.organizador.estoque.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
    val context = LocalContext.current
    val insights = remember(context) { InventoryInsights(context) }
    var filter by remember { mutableStateOf("all") }
    var rows by remember { mutableStateOf<List<ExpiryDetail>>(emptyList()) }

    LaunchedEffect(filter) {
        rows = withContext(Dispatchers.IO) { insights.expiryDetails(filter) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val desktop = maxWidth >= 900.dp

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .fillMaxSize()
                    .widthIn(max = 1200.dp)
                    .padding(horizontal = if (desktop) 28.dp else 16.dp, vertical = if (desktop) 24.dp else 16.dp)
            ) {
                Text(
                    "Validades",
                    fontSize = if (desktop) 31.sp else 28.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "Veja quais produtos estão vencidos ou próximos do vencimento",
                    color = Color(0xFF9FB0C4)
                )
                Spacer(Modifier.height(14.dp))

                if (desktop) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            FilterChip(filter == "all", { filter = "all" }, { Text("Todas") })
                            FilterChip(filter == "expired", { filter = "expired" }, { Text("Vencidos") })
                            FilterChip(filter == "7", { filter = "7" }, { Text("7 dias") })
                            FilterChip(filter == "30", { filter = "30" }, { Text("8-30 dias") })
                            FilterChip(filter == "60", { filter = "60" }, { Text("31-60 dias") })
                        }
                        Text("${rows.size} validade(s)", color = Color(0xFF9FB0C4), fontSize = 12.sp)
                    }
                } else {
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
                }

                Spacer(Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 18.dp)
                ) {
                    items(rows, key = { it.id }) { item ->
                        ExpiryItemCard(item, desktop)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpiryItemCard(item: ExpiryDetail, desktop: Boolean) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF102238))
    ) {
        if (desktop) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column(Modifier.weight(1.7f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.description, fontWeight = FontWeight.Bold)
                    Text(
                        "Código ${item.productCode} • EAN ${item.ean ?: "-"}",
                        color = Color(0xFF9FB0C4),
                        fontSize = 12.sp
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Validade", color = Color(0xFF9FB0C4), fontSize = 11.sp)
                    Text(item.expiryDate, color = Color(0xFFFFB938), fontWeight = FontWeight.Bold)
                }
                Column(Modifier.width(170.dp), horizontalAlignment = Alignment.End) {
                    Text("Lote ${formatNumberBr(item.quantity)}", fontSize = 13.sp)
                    Text("Estoque ${formatNumberBr(item.stock)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.description, fontWeight = FontWeight.Bold)
                Text(
                    "Código ${item.productCode} • EAN ${item.ean ?: "-"}",
                    color = Color(0xFF9FB0C4),
                    fontSize = 12.sp
                )
                Text("Validade: ${item.expiryDate}", color = Color(0xFFFFB938), fontWeight = FontWeight.Bold)
                Text(
                    "Lote: ${formatNumberBr(item.quantity)} • Estoque: ${formatNumberBr(item.stock)}",
                    fontSize = 13.sp
                )
            }
        }
    }
}
