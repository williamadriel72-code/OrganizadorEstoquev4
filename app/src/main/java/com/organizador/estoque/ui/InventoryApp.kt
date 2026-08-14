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
import com.organizador.estoque.data.ExpiryBatch
import com.organizador.estoque.data.InventoryRepository
import com.organizador.estoque.data.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Navy = Color(0xFF071421)
private val Panel = Color(0xFF102238)
private val Blue = Color(0xFF3184EE)
private val Warning = Color(0xFFFFC857)
private val AppColors = darkColorScheme(primary = Blue, background = Navy, surface = Panel)

@Composable
fun InventoryApp(repository: InventoryRepository) {
    MaterialTheme(colorScheme = AppColors) {
        var screen by remember { mutableStateOf("dashboard") }
        var refreshKey by remember { mutableIntStateOf(0) }
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(selected = screen=="dashboard", onClick={screen="dashboard"}, icon={}, label={Text("Painel")})
                    NavigationBarItem(selected = screen=="products", onClick={screen="products"}, icon={}, label={Text("Produtos")})
                    NavigationBarItem(selected = screen=="entry", onClick={screen="entry"}, icon={}, label={Text("Entrada")})
                    NavigationBarItem(selected = screen=="exit", onClick={screen="exit"}, icon={}, label={Text("Saída")})
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when(screen) {
                    "products" -> ProductsScreen(repository, refreshKey) { refreshKey++ }
                    "entry" -> MovementScreen(repository, true) { refreshKey++ }
                    "exit" -> MovementScreen(repository, false) { refreshKey++ }
                    else -> DashboardScreen(repository, refreshKey) { screen="products" }
                }
            }
        }
    }
}

@Composable
private fun DashboardScreen(repository: InventoryRepository, refreshKey: Int, onOpenProducts: (String) -> Unit) {
    var stats by remember { mutableStateOf(DashboardStats()) }
    LaunchedEffect(refreshKey) {
        stats = withContext(Dispatchers.IO) { repository.dashboardStats() }
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Organizador Geral de Estoque", fontSize=25.sp, fontWeight=FontWeight.Bold) }
        item { Text("V4 operacional • estoque local SQLite", color=Color.LightGray) }
        item { StatCard("Produtos", stats.products.toString()) { onOpenProducts("all") } }
        item { StatCard("Estoque total", "%.2f".format(stats.totalStock)) { onOpenProducts("all") } }
        item { StatCard("Estoque baixo", stats.lowStock.toString()) { onOpenProducts("low") } }
        item { StatCard("Estoque zerado", stats.zeroStock.toString()) { onOpenProducts("zero") } }
        item { StatCard("Sem endereço", stats.withoutAddress.toString()) { onOpenProducts("no_address") } }
        item { StatCard("Vencidos", stats.expired.toString()) { onOpenProducts("all") } }
        item { StatCard("Vencem em 7 dias", stats.expiring7.toString()) { onOpenProducts("all") } }
        item { StatCard("Vencem em 30 dias", stats.expiring30.toString()) { onOpenProducts("all") } }
        item { StatCard("Vencem em 60 dias", stats.expiring60.toString()) { onOpenProducts("all") } }
    }
}

@Composable
private fun StatCard(title:String, value:String, onClick:()->Unit) {
    Card(onClick=onClick, modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween) {
            Text(title, fontWeight=FontWeight.SemiBold, fontSize=17.sp)
            Text(value, fontSize=24.sp, fontWeight=FontWeight.Bold, color=Blue)
        }
    }
}

@Composable
private fun ProductsScreen(repository: InventoryRepository, refreshKey: Int, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var editing by remember { mutableStateOf<Product?>(null) }
    var creating by remember { mutableStateOf(false) }
    var scannedProduct by remember { mutableStateOf<Product?>(null) }
    var scannedExpiries by remember { mutableStateOf<List<ExpiryBatch>>(emptyList()) }

    suspend fun loadProducts(): List<Product> {
        val normalized = query.trim()
        if (normalized.isNotEmpty()) delay(250)
        val limit = if (normalized.isBlank()) 100 else 200
        return withContext(Dispatchers.IO) { repository.searchProducts(normalized, limit, 0, filter) }
    }

    fun reload() {
        scope.launch { products = loadProducts() }
    }

    fun handleScan(barcode: String) {
        query = barcode
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val product = repository.findExact(barcode)
                val expiries = product?.let { repository.expiryBatchesForProduct(it.code) }.orEmpty()
                product to expiries
            }
            scannedProduct = result.first
            scannedExpiries = result.second
        }
    }

    LaunchedEffect(query, filter, refreshKey) {
        products = loadProducts()
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("Produtos", fontSize=25.sp, fontWeight=FontWeight.Bold, modifier=Modifier.weight(1f))
            Button(onClick={ creating=true }) { Text("+ NOVO") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(query, {
            query=it
            scannedProduct=null
            scannedExpiries=emptyList()
        }, modifier=Modifier.fillMaxWidth(), label={Text("Código, EAN ou descrição")}, singleLine=true)
        Spacer(Modifier.height(8.dp))
        BarcodeCaptureButton(Modifier.fillMaxWidth(), onBarcode = ::handleScan)
        scannedProduct?.let { p ->
            Spacer(Modifier.height(8.dp))
            ExpiryScanCard(p, scannedExpiries)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            FilterChip(selected=filter=="all", onClick={filter="all"}, label={Text("Todos")})
            FilterChip(selected=filter=="low", onClick={filter="low"}, label={Text("Baixos")})
            FilterChip(selected=filter=="zero", onClick={filter="zero"}, label={Text("Zerados")})
            FilterChip(selected=filter=="no_address", onClick={filter="no_address"}, label={Text("Sem end.")})
        }
        Spacer(Modifier.height(8.dp))
        Text("${products.size} produto(s) visível(is)", fontSize=12.sp, color=Color.LightGray)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            items(products, key={it.code}, contentType={"product"}) { p ->
                Card(onClick={ editing=p }, modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(p.description, fontWeight=FontWeight.Bold, fontSize=17.sp)
                        Text("Código ${p.code} • EAN ${p.ean ?: "-"}", fontSize=13.sp)
                        Text("Estoque: ${formatQty(p.stock)} • Grupo: ${p.groupCode ?: "-"}", fontSize=13.sp)
                        if (p.stock == 0.0) Text("ZERADO", color=Color.Red, fontWeight=FontWeight.Bold)
                        else if (p.stock <= 5) Text("ESTOQUE BAIXO", color=Warning, fontWeight=FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (creating) ProductEditor(repository, null, onDismiss={creating=false}, onSaved={ creating=false; reload(); onChanged() })
    editing?.let { p -> ProductEditor(repository, p, onDismiss={editing=null}, onSaved={ editing=null; reload(); onChanged() }) }
}

@Composable
private fun ExpiryScanCard(product: Product, expiries: List<ExpiryBatch>) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF10243A))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(product.description, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text("Validades", color = Color(0xFF9FB0C4), fontWeight = FontWeight.SemiBold)
            if (expiries.isEmpty()) {
                Text("Nenhuma validade cadastrada para este produto.", color = Color.LightGray, fontSize = 13.sp)
            } else {
                expiries.forEach { batch ->
                    Text("${batch.expiryDate}  •  Qtd. ${formatQty(batch.quantity)}", fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun ProductEditor(repository: InventoryRepository, original: Product?, onDismiss:()->Unit, onSaved:()->Unit) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf(original?.code ?: "") }
    var ean by remember { mutableStateOf(original?.ean ?: "") }
    var description by remember { mutableStateOf(original?.description ?: "") }
    var group by remember { mutableStateOf(original?.groupCode ?: "") }
    var category by remember { mutableStateOf(original?.category ?: "") }
    var stock by remember { mutableStateOf(original?.stock?.toString() ?: "0") }
    var address by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(original?.code) {
        if (original != null) address = withContext(Dispatchers.IO) { repository.productAddresses(original.code).firstOrNull().orEmpty() }
    }

    AlertDialog(
        onDismissRequest=onDismiss,
        title={Text(if (original == null) "Novo produto" else "Editar produto")},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(code,{code=it},label={Text("Código")},enabled=original==null,singleLine=true)
                OutlinedTextField(ean,{ean=it},label={Text("EAN / código de barras")},singleLine=true)
                OutlinedTextField(description,{description=it},label={Text("Descrição")})
                OutlinedTextField(group,{group=it},label={Text("Grupo")},singleLine=true)
                OutlinedTextField(category,{category=it},label={Text("Categoria")},singleLine=true)
                OutlinedTextField(stock,{stock=it},label={Text("Estoque atual")},singleLine=true)
                OutlinedTextField(address,{address=it},label={Text("Endereço")},singleLine=true)
                error?.let { Text(it, color=MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton={
            Button(onClick={
                val qty = stock.replace(",", ".").toDoubleOrNull()
                if (code.isBlank() || description.isBlank() || qty == null || qty < 0) {
                    error = "Informe código, descrição e estoque válido."
                } else scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            repository.upsertProduct(Product(code.trim(), ean.trim().ifBlank{null}, description.trim(), group.trim().ifBlank{null}, category.trim().ifBlank{null}, qty, false, true))
                            repository.setAddress(code.trim(), address)
                        }
                    }.onSuccess { onSaved() }.onFailure { error=it.message ?: "Erro ao salvar" }
                }
            }) { Text("SALVAR") }
        },
        dismissButton={TextButton(onClick=onDismiss){Text("CANCELAR")}}
    )
}

@Composable
private fun MovementScreen(repository: InventoryRepository, isEntry: Boolean, onChanged:()->Unit) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var expiry by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<Product?>(null) }
    var foundAddress by remember { mutableStateOf<String?>(null) }
    var foundExpiries by remember { mutableStateOf<List<ExpiryBatch>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }

    fun lookup(value: String = code) {
        val normalized = value.trim()
        if (normalized.isBlank()) return
        code = normalized
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val product = repository.findExact(normalized)
                val address = product?.let { repository.productAddresses(it.code).firstOrNull() }
                val expiries = product?.let { repository.expiryBatchesForProduct(it.code) }.orEmpty()
                Triple(product, address, expiries)
            }
            found = result.first
            foundAddress = result.second
            foundExpiries = result.third
            if (found == null) { error=true; message="Produto não encontrado" }
            else { error=false; message=null }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(if (isEntry) "Entrada de estoque" else "Saída de estoque", fontSize=25.sp, fontWeight=FontWeight.Bold)
        Text(if (isEntry) "Aumenta o estoque e registra validade quando informada." else "Baixa o estoque e consome primeiro as validades mais próximas (FEFO).", color=Color.LightGray)
        OutlinedTextField(code,{code=it; found=null; foundAddress=null; foundExpiries=emptyList(); message=null},modifier=Modifier.fillMaxWidth(),label={Text("Código ou EAN")},singleLine=true)
        BarcodeCaptureButton(Modifier.fillMaxWidth()) { scanned -> lookup(scanned) }
        Button(onClick={lookup()}, modifier=Modifier.fillMaxWidth()) { Text("LOCALIZAR PRODUTO") }
        found?.let { p ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(p.description, fontWeight=FontWeight.Bold, fontSize=18.sp)
                    Text("Estoque atual: ${formatQty(p.stock)}")
                    foundAddress?.let { Text("Endereço: $it") }
                    Text("Validades", color = Color(0xFF9FB0C4), fontWeight = FontWeight.SemiBold)
                    if (foundExpiries.isEmpty()) {
                        Text("Nenhuma validade cadastrada.", color = Color.LightGray, fontSize = 13.sp)
                    } else {
                        foundExpiries.forEach { batch ->
                            Text("${batch.expiryDate}  •  Qtd. ${formatQty(batch.quantity)}", fontSize = 14.sp)
                        }
                    }
                }
            }
        }
        OutlinedTextField(quantity,{quantity=it},modifier=Modifier.fillMaxWidth(),label={Text("Quantidade")},singleLine=true)
        if (isEntry) OutlinedTextField(expiry,{expiry=it},modifier=Modifier.fillMaxWidth(),label={Text("Validade opcional: AAAA-MM-DD")},singleLine=true)
        Button(
            onClick={
                val qty = quantity.replace(",", ".").toDoubleOrNull()
                if (code.isBlank() || qty == null || qty <= 0) { error=true; message="Informe produto e quantidade válida." }
                else scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            if (isEntry) repository.stockIn(code, qty, expiry.ifBlank{null}) else repository.stockOut(code, qty)
                        }
                    }.onSuccess { updated ->
                        found=updated
                        scope.launch {
                            foundExpiries = withContext(Dispatchers.IO) { repository.expiryBatchesForProduct(updated.code) }
                        }
                        error=false
                        message=if (isEntry) "Entrada registrada com sucesso." else "Saída registrada com sucesso."
                        onChanged()
                    }.onFailure { error=true; message=it.message ?: "Não foi possível concluir." }
                }
            }, modifier=Modifier.fillMaxWidth(), enabled=found != null
        ) { Text(if (isEntry) "CONFIRMAR ENTRADA" else "CONFIRMAR SAÍDA") }
        message?.let { Text(it, color=if(error) MaterialTheme.colorScheme.error else Color(0xFF7BD389), fontWeight=FontWeight.Bold) }
    }
}

private fun formatQty(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(value)
