package com.organizador.estoque.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.organizador.estoque.data.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Navy = Color(0xFF071421)
private val Panel = Color(0xFF102238)
private val Blue = Color(0xFF3184EE)

@Composable
fun InventoryApp(repository: InventoryRepository) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Blue, background = Navy, surface = Panel)) {
        var screen by remember { mutableStateOf("dashboard") }
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(selected = screen=="dashboard", onClick={screen="dashboard"}, icon={}, label={Text("Dashboard")})
                    NavigationBarItem(selected = screen=="products", onClick={screen="products"}, icon={}, label={Text("Produtos")})
                    NavigationBarItem(selected = screen=="entry", onClick={screen="entry"}, icon={}, label={Text("Entrada")})
                    NavigationBarItem(selected = screen=="exit", onClick={screen="exit"}, icon={}, label={Text("Saída")})
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when(screen) {
                    "products" -> ProductsScreen(repository)
                    "entry" -> PlaceholderScreen("Entrada de estoque", "Bip + quantidade + validade quando houver")
                    "exit" -> PlaceholderScreen("Saída de estoque", "Baixa direta ou FEFO automático")
                    else -> DashboardScreen(repository, onOpenProducts={screen="products"})
                }
            }
        }
    }
}

@Composable
private fun DashboardScreen(repository: InventoryRepository, onOpenProducts: () -> Unit) {
    var stats by remember { mutableStateOf(DashboardStats()) }
    LaunchedEffect(Unit) { stats = withContext(Dispatchers.IO) { repository.dashboardStats() } }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Organizador Geral de Estoque", fontSize=24.sp, fontWeight=FontWeight.Bold) }
        item { Text("V4 nativa • banco SQLite • preparada para grandes volumes", color=Color.LightGray) }
        item { StatCard("Produtos", stats.products.toString(), onOpenProducts) }
        item { StatCard("Estoque total", "%.2f".format(stats.totalStock), onOpenProducts) }
        item { StatCard("Estoque baixo", stats.lowStock.toString(), onOpenProducts) }
        item { StatCard("Estoque zerado", stats.zeroStock.toString(), onOpenProducts) }
        item { StatCard("Sem endereço", stats.withoutAddress.toString(), onOpenProducts) }
        item { StatCard("Vencidos", stats.expired.toString(), onOpenProducts) }
        item { StatCard("Vencem em 7 dias", stats.expiring7.toString(), onOpenProducts) }
        item { StatCard("Vencem em 30 dias", stats.expiring30.toString(), onOpenProducts) }
        item { StatCard("Vencem em 60 dias", stats.expiring60.toString(), onOpenProducts) }
    }
}

@Composable
private fun StatCard(title:String, value:String, onClick:()->Unit) {
    Card(onClick=onClick, modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween) {
            Text(title, fontWeight=FontWeight.SemiBold)
            Text(value, fontSize=24.sp, fontWeight=FontWeight.Bold, color=Blue)
        }
    }
}

@Composable
private fun ProductsScreen(repository: InventoryRepository) {
    var query by remember { mutableStateOf("") }
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    LaunchedEffect(query) { products = withContext(Dispatchers.IO) { repository.searchProducts(query, 50, 0) } }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Produtos", fontSize=24.sp, fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(query, {query=it}, modifier=Modifier.fillMaxWidth(), label={Text("Código, EAN ou descrição")}, singleLine=true)
        Spacer(Modifier.height(8.dp))
        Text("Carregando no máximo 50 por consulta para manter o app leve.", fontSize=12.sp, color=Color.LightGray)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            items(products, key={it.code}) { p ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(p.description, fontWeight=FontWeight.Bold)
                        Text("Código ${p.code} • EAN ${p.ean ?: "-"}", fontSize=12.sp)
                        Text("Estoque: ${p.stock} • Grupo: ${p.groupCode ?: "-"}", fontSize=12.sp)
                        if (p.controlsExpiry) Text("Controle de validade ativo", color=Color(0xFFFFC857), fontSize=12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title:String, subtitle:String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment=Alignment.Center) {
        Column(horizontalAlignment=Alignment.CenterHorizontally) {
            Text(title, fontSize=24.sp, fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(8.dp)); Text(subtitle, color=Color.LightGray)
        }
    }
}
