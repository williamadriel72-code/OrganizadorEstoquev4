package com.organizador.estoque.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.organizador.estoque.data.CatalogPriceStore
import com.organizador.estoque.data.ExpiryBatch
import com.organizador.estoque.data.InventoryRepository
import com.organizador.estoque.data.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ProductsV2(repository: InventoryRepository, refreshKey: Int, initialFilter: String = "all") {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val priceStore = remember { CatalogPriceStore(context) }
    DisposableEffect(priceStore) { onDispose { priceStore.close() } }

    val listState = rememberLazyListState()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(initialFilter) }
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var scannedRef by rememberSaveable { mutableStateOf<String?>(null) }
    var scannedProductCode by remember { mutableStateOf<String?>(null) }
    var scannedExpiries by remember { mutableStateOf<List<ExpiryBatch>>(emptyList()) }
    var scannedPrice by remember { mutableStateOf<Double?>(null) }

    LaunchedEffect(initialFilter) { filter = initialFilter }

    LaunchedEffect(query, filter, refreshKey) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isNotEmpty()) delay(250)
        products = withContext(Dispatchers.IO) {
            val limit = if (normalizedQuery.isBlank()) 150 else 250
            repository.searchProducts(normalizedQuery, limit, 0, filter)
        }
    }

    LaunchedEffect(scannedRef, refreshKey) {
        val ref = scannedRef?.trim().orEmpty()
        if (ref.isBlank()) {
            scannedProductCode = null
            scannedExpiries = emptyList()
            scannedPrice = null
            return@LaunchedEffect
        }

        val result = withContext(Dispatchers.IO) {
            val product = repository.findExact(ref)
            val expiries = product?.let { repository.expiryBatchesForProduct(it.code) }.orEmpty()
            val price = product?.let { priceStore.priceFor(it.code) }
            Triple(product?.code, expiries, price)
        }
        scannedProductCode = result.first
        scannedExpiries = result.second
        scannedPrice = result.third
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val desktop = maxWidth >= 900.dp
        val horizontalPadding = if (desktop) 28.dp else 16.dp
        val verticalPadding = if (desktop) 24.dp else 14.dp

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .fillMaxSize()
                    .widthIn(max = 1240.dp)
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding)
            ) {
                Text(
                    "Produtos",
                    fontSize = if (desktop) 31.sp else 28.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "Pesquise, confira e localize mercadorias",
                    color = Color(0xFF9FB0C4),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(if (desktop) 18.dp else 14.dp))

                if (desktop) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = {
                                query = it
                                scannedRef = null
                                scannedProductCode = null
                                scannedExpiries = emptyList()
                                scannedPrice = null
                            },
                            modifier = Modifier.weight(1f),
                            label = { Text("Pesquisar código, EAN ou descrição") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )
                        BarcodeCaptureButton(Modifier.width(240.dp)) { code ->
                            query = code
                            scannedRef = code
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            scannedRef = null
                            scannedProductCode = null
                            scannedExpiries = emptyList()
                            scannedPrice = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Pesquisar código, EAN ou descrição") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    BarcodeCaptureButton(Modifier.fillMaxWidth()) { code ->
                        query = code
                        scannedRef = code
                    }
                }

                Spacer(Modifier.height(14.dp))

                if (desktop) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            FilterChip(filter == "all", { filter = "all" }, { Text("Todos") })
                            FilterChip(filter == "low", { filter = "low" }, { Text("Baixos") })
                            FilterChip(filter == "zero", { filter = "zero" }, { Text("Zerados") })
                            FilterChip(filter == "negative", { filter = "negative" }, { Text("Negativos") })
                        }
                        Text(
                            "${products.size} produto(s)",
                            color = Color(0xFF9FB0C4),
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        FilterChip(filter == "all", { filter = "all" }, { Text("Todos") })
                        FilterChip(filter == "low", { filter = "low" }, { Text("Baixos") })
                        FilterChip(filter == "zero", { filter = "zero" }, { Text("Zerados") })
                        FilterChip(filter == "negative", { filter = "negative" }, { Text("Negativos") })
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "${products.size} produto(s)",
                        color = Color(0xFF9FB0C4),
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 18.dp)
                ) {
                    items(products, key = { it.code }, contentType = { "product" }) { p ->
                        val scanned = scannedProductCode == p.code
                        ProductCardV2(
                            product = p,
                            desktop = desktop,
                            isScannedProduct = scanned,
                            scannedExpiries = if (scanned) scannedExpiries else emptyList(),
                            scannedPrice = if (scanned) scannedPrice else null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductCardV2(
    product: Product,
    desktop: Boolean,
    isScannedProduct: Boolean,
    scannedExpiries: List<ExpiryBatch>,
    scannedPrice: Double?
) {
    val accent = when {
        product.stock < 0.0 -> Color(0xFFFF7A59)
        product.stock == 0.0 -> Color(0xFFFF5368)
        product.stock <= 5.0 -> Color(0xFFFFB938)
        else -> Color(0xFF20C983)
    }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF102238))
    ) {
        if (desktop) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1.8f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(product.description, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        "Código ${product.code} • EAN ${product.ean ?: "-"}",
                        color = Color(0xFF9FB0C4),
                        fontSize = 12.sp
                    )
                }

                Column(Modifier.weight(1.25f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Grupo ${product.groupCode ?: "-"}",
                        color = Color(0xFFB8C5D3),
                        fontSize = 13.sp
                    )
                    if (isScannedProduct) ExpiryLines(scannedExpiries)
                }

                Column(
                    Modifier.width(150.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (isScannedProduct) {
                        Text("Valor", color = Color(0xFF9FB0C4), fontSize = 11.sp)
                        Text(
                            scannedPrice?.let(::formatCurrencyBr) ?: "Não informado",
                            color = Color(0xFFB8C5D3),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text("Estoque", color = Color(0xFF9FB0C4), fontSize = 11.sp)
                    Text(
                        formatNumberBr(product.stock),
                        color = accent,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp
                    )
                }
            }
        } else {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(product.description, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "Código ${product.code} • EAN ${product.ean ?: "-"}",
                    color = Color(0xFF9FB0C4),
                    fontSize = 12.sp
                )

                if (isScannedProduct) {
                    Text(
                        "Valor: ${scannedPrice?.let(::formatCurrencyBr) ?: "Não informado"}",
                        color = Color(0xFFB8C5D3),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    ExpiryLines(scannedExpiries)
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "Grupo ${product.groupCode ?: "-"}",
                        color = Color(0xFFB8C5D3),
                        fontSize = 13.sp
                    )
                    Text(
                        formatNumberBr(product.stock),
                        color = accent,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpiryLines(expiries: List<ExpiryBatch>) {
    if (expiries.isEmpty()) {
        Text(
            "Validade: Não informada",
            color = Color(0xFFFFC857),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    } else {
        expiries.forEachIndexed { index, batch ->
            Text(
                text = if (index == 0) {
                    "Validade: ${formatExpiryDateBr(batch.expiryDate)} • Qtd. ${formatNumberBr(batch.quantity)}"
                } else {
                    "Outra validade: ${formatExpiryDateBr(batch.expiryDate)} • Qtd. ${formatNumberBr(batch.quantity)}"
                },
                color = if (index == 0) Color(0xFFFFC857) else Color(0xFFB8C5D3),
                fontSize = 13.sp,
                fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

private fun formatExpiryDateBr(value: String): String = runCatching {
    LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
}.getOrElse { value }
