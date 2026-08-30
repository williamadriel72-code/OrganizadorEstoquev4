package com.organizador.scanner

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val ptBr = Locale("pt", "BR")
private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val moneyFmt = NumberFormat.getCurrencyInstance(ptBr)
private val appColors = darkColorScheme(
    primary = Color(0xFFE13D32),
    onPrimary = Color.White,
    secondary = Color(0xFFFFC7A8),
    background = Color(0xFF101113),
    surface = Color(0xFF1A1C1F),
    surfaceVariant = Color(0xFF25282C),
    onSurface = Color(0xFFF5F2EE),
    onSurfaceVariant = Color(0xFFD1CDC8)
)

private fun brl(v: Double): String = moneyFmt.format(v)
private fun parseDate(v: String): LocalDate? = runCatching { LocalDate.parse(v.trim(), dateFmt) }.getOrNull()

data class Neighborhood(val id: Long, val name: String, val fee: Double, val integral: Boolean)
data class Delivery(val id: Long, val date: LocalDate, val neighborhood: String, val fee: Double, val createdAt: Long)
data class DaySummary(val count: Int, val total: Double)

class MainActivity : ComponentActivity() {
    private lateinit var db: DeliveryDb
    private var refreshToken by mutableIntStateOf(0)
    private var message by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = DeliveryDb(this)

        val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) {
                runCatching {
                    contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(db.exportJson()) }
                        ?: error("Não foi possível abrir o arquivo")
                }.onSuccess { message = "Backup salvo" }
                    .onFailure { message = "Falha ao salvar backup" }
            }
        }

        val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                runCatching {
                    val txt = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Arquivo vazio")
                    db.restoreJson(txt)
                }.onSuccess {
                    refreshToken++
                    message = "Backup restaurado"
                }.onFailure { message = "Backup inválido" }
            }
        }

        setContent {
            MaterialTheme(colorScheme = appColors) {
                MainApp(
                    db = db,
                    refreshToken = refreshToken,
                    message = message,
                    clearMessage = { message = null },
                    refresh = { refreshToken++ },
                    exportBackup = { exportLauncher.launch("Bora-Maycon-${LocalDate.now()}.json") },
                    importBackup = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
                )
            }
        }
    }
}

@Composable
private fun MainApp(
    db: DeliveryDb,
    refreshToken: Int,
    message: String?,
    clearMessage: () -> Unit,
    refresh: () -> Unit,
    exportBackup: () -> Unit,
    importBackup: () -> Unit
) {
    var tab by rememberSaveable { mutableStateOf("Hoje") }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        if (!message.isNullOrBlank()) {
            snackbar.showSnackbar(message)
            clearMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            TopTabs(tab) { tab = it }
            when (tab) {
                "Hoje" -> TodayScreen(db, refreshToken, refresh)
                "Calendário" -> CalendarScreen(db, refreshToken)
                "Histórico" -> HistoryScreen(db, refreshToken, refresh)
                "Bairros" -> NeighborhoodsScreen(db, refreshToken, refresh, exportBackup, importBackup)
                else -> ReportsScreen(db, refreshToken)
            }
        }
    }
}

@Composable
private fun TopTabs(selected: String, onSelect: (String) -> Unit) {
    val tabs = listOf("Hoje", "Calendário", "Histórico", "Bairros", "Relatórios")
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF17191C)).horizontalScroll(rememberScrollState()).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEach { label ->
            if (label == selected) Button(onClick = { onSelect(label) }) { Text(label) }
            else OutlinedButton(onClick = { onSelect(label) }) { Text(label) }
        }
    }
}

@Composable
private fun TodayScreen(db: DeliveryDb, refreshToken: Int, refresh: () -> Unit) {
    val today = LocalDate.now()
    val deliveries = remember(refreshToken) { db.deliveriesOn(today) }
    var addDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(R.drawable.profile_maycon),
                    contentDescription = "Foto de perfil",
                    modifier = Modifier.size(96.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(8.dp))
                Text("Bora Maycon Hi Hi", fontSize = 27.sp, fontWeight = FontWeight.Black)
                Text("Rotas, entregas e taxas", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(today.format(dateFmt), fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Entregas", deliveries.size.toString(), Modifier.weight(1f))
                StatCard("Total do dia", brl(deliveries.sumOf { it.fee }), Modifier.weight(1f))
            }
        }
        item {
            Button(
                onClick = { addDialog = true },
                modifier = Modifier.fillMaxWidth().height(58.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("+ ADICIONAR ENTREGA", fontSize = 17.sp, fontWeight = FontWeight.Black)
            }
        }
        item { Text("Entregas de hoje", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        if (deliveries.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Text("Nenhuma entrega lançada hoje.", Modifier.padding(16.dp))
                }
            }
        } else {
            items(deliveries, key = { it.id }) { d ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(d.neighborhood, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(brl(d.fee), color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { db.deleteDelivery(d.id); refresh() }) { Text("Excluir") }
                    }
                }
            }
        }
    }

    if (addDialog) {
        AddDeliveryDialog(db, onDismiss = { addDialog = false }, onAdded = refresh)
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF23262A))) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 21.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun AddDeliveryDialog(db: DeliveryDb, onDismiss: () -> Unit, onAdded: () -> Unit) {
    val neighborhoods = remember { db.neighborhoods() }
    var search by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val integralFee = db.getIntegralFee()
    val shown = if (search.isBlank()) neighborhoods else neighborhoods.filter { it.name.contains(search.trim(), true) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Adicionar entrega", fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text("Toque no bairro para lançar. O mesmo bairro pode ser lançado várias vezes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                TextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Pesquisar bairro") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (note.isNotBlank()) Text(note, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(vertical = 8.dp))
                LazyColumn(Modifier.heightIn(max = 430.dp)) {
                    items(shown, key = { it.id }) { n ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                val value = if (n.integral) integralFee else n.fee
                                if (n.integral && value <= 0.0) {
                                    note = "Defina a Taxa Integral na aba Bairros."
                                } else {
                                    db.addDelivery(LocalDate.now(), n.name, value)
                                    note = "${n.name} • ${brl(value)} adicionado"
                                    search = ""
                                    onAdded()
                                }
                            }.padding(vertical = 13.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(n.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            Text(if (n.integral) "Integral" else brl(n.fee), color = MaterialTheme.colorScheme.secondary)
                        }
                        HorizontalDivider(color = Color(0xFF34373B))
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Fechar") }
            }
        }
    }
}

@Composable
private fun CalendarScreen(db: DeliveryDb, refreshToken: Int) {
    var year by rememberSaveable { mutableIntStateOf(LocalDate.now().year) }
    val stats = remember(refreshToken, year) { db.yearStats(year) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(onClick = { year-- }) { Text("‹") }
                Text("CALENDÁRIO $year", fontSize = 22.sp, fontWeight = FontWeight.Black)
                OutlinedButton(onClick = { year++ }) { Text("›") }
            }
        }
        items((1..12).toList()) { month -> MonthGrid(YearMonth.of(year, month), stats) { selectedDay = it } }
    }

    selectedDay?.let { date ->
        val list = db.deliveriesOn(date)
        AlertDialog(
            onDismissRequest = { selectedDay = null },
            title = { Text(date.format(dateFmt)) },
            text = {
                Column {
                    Text("${list.size} entrega(s)", fontSize = 18.sp)
                    Text(brl(list.sumOf { it.fee }), fontSize = 26.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.secondary)
                    if (list.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        list.forEach { Text("• ${it.neighborhood} — ${brl(it.fee)}") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedDay = null }) { Text("OK") } }
        )
    }
}

@Composable
private fun MonthGrid(month: YearMonth, stats: Map<LocalDate, DaySummary>, onDay: (LocalDate) -> Unit) {
    val name = month.month.getDisplayName(TextStyle.FULL, ptBr).replaceFirstChar { it.uppercase() }
    val offset = month.atDay(1).dayOfWeek.value - 1
    val labels = listOf("S", "T", "Q", "Q", "S", "S", "D")

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp)) {
            Text(name, fontSize = 19.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                labels.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
            }
            repeat(6) { row ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { col ->
                        val cell = row * 7 + col
                        val day = cell - offset + 1
                        if (day in 1..month.lengthOfMonth()) {
                            val date = month.atDay(day)
                            val stat = stats[date]
                            val active = stat != null && stat.count > 0
                            Box(
                                Modifier.weight(1f).padding(2.dp).clip(RoundedCornerShape(8.dp))
                                    .background(if (active) Color(0xFF8E302A) else Color.Transparent)
                                    .clickable { onDay(date) }.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(day.toString(), fontWeight = if (active) FontWeight.Black else FontWeight.Normal)
                                    if (active) Text(stat!!.count.toString(), fontSize = 9.sp, color = Color.White)
                                }
                            }
                        } else Spacer(Modifier.weight(1f).height(40.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(db: DeliveryDb, refreshToken: Int, refresh: () -> Unit) {
    var search by remember { mutableStateOf("") }
    val all = remember(refreshToken) { db.allDeliveries() }
    val shown = if (search.isBlank()) all else all.filter {
        it.neighborhood.contains(search.trim(), true) || it.date.format(dateFmt).contains(search.trim())
    }
    val grouped = shown.groupBy { it.date }.toSortedMap(compareByDescending { it })

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Histórico", fontSize = 26.sp, fontWeight = FontWeight.Black)
            TextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Buscar bairro ou data") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true
            )
        }
        if (grouped.isEmpty()) item { Text("Nenhum registro encontrado.") }
        grouped.forEach { (date, list) ->
            item(key = date.toString()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(date.format(dateFmt), fontSize = 19.sp, fontWeight = FontWeight.Black)
                        Text("${list.size} entregas • ${brl(list.sumOf { it.fee })}", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        list.forEach { d ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("${d.neighborhood} — ${brl(d.fee)}", Modifier.weight(1f), fontSize = 14.sp)
                                TextButton(onClick = { db.deleteDelivery(d.id); refresh() }) { Text("Excluir") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NeighborhoodsScreen(
    db: DeliveryDb,
    refreshToken: Int,
    refresh: () -> Unit,
    exportBackup: () -> Unit,
    importBackup: () -> Unit
) {
    val neighborhoods = remember(refreshToken) { db.neighborhoods() }
    var search by remember { mutableStateOf("") }
    var integralText by remember(refreshToken) { mutableStateOf(db.getIntegralFee().takeIf { it > 0 }?.toString()?.replace('.', ',') ?: "") }
    var editor by remember { mutableStateOf<Neighborhood?>(null) }
    var newItem by remember { mutableStateOf(false) }
    val shown = if (search.isBlank()) neighborhoods else neighborhoods.filter { it.name.contains(search.trim(), true) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Bairros e taxas", fontSize = 26.sp, fontWeight = FontWeight.Black)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Taxa Integral", fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text("Usada nos locais marcados como Taxa Integral.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextField(
                        value = integralText,
                        onValueChange = { integralText = it },
                        label = { Text("Valor R$") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        singleLine = true
                    )
                    Button(onClick = {
                        integralText.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0.0 }?.let {
                            db.setIntegralFee(it); refresh()
                        }
                    }, modifier = Modifier.padding(top = 8.dp)) { Text("Salvar Taxa Integral") }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { newItem = true }, modifier = Modifier.weight(1f)) { Text("+ Bairro") }
                OutlinedButton(onClick = exportBackup, modifier = Modifier.weight(1f)) { Text("Backup") }
                OutlinedButton(onClick = importBackup, modifier = Modifier.weight(1f)) { Text("Restaurar") }
            }
        }
        item {
            TextField(value = search, onValueChange = { search = it }, label = { Text("Pesquisar bairro") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        items(shown, key = { it.id }) { n ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(n.name, fontWeight = FontWeight.Bold)
                        Text(if (n.integral) "Taxa Integral" else brl(n.fee), color = MaterialTheme.colorScheme.secondary)
                    }
                    TextButton(onClick = { editor = n }) { Text("Editar") }
                    TextButton(onClick = { db.deleteNeighborhood(n.id); refresh() }) { Text("Excluir") }
                }
            }
        }
    }

    if (newItem) {
        NeighborhoodEditor(null, { newItem = false }) { name, fee, integral ->
            if (db.addNeighborhood(name, fee, integral)) { newItem = false; refresh() }
        }
    }
    editor?.let { current ->
        NeighborhoodEditor(current, { editor = null }) { name, fee, integral ->
            if (db.updateNeighborhood(current.id, name, fee, integral)) { editor = null; refresh() }
        }
    }
}

@Composable
private fun NeighborhoodEditor(existing: Neighborhood?, dismiss: () -> Unit, save: (String, Double, Boolean) -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var fee by remember { mutableStateOf(existing?.fee?.toString()?.replace('.', ',') ?: "") }
    var integral by remember { mutableStateOf(existing?.integral ?: false) }

    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (existing == null) "Novo bairro" else "Editar bairro") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(value = name, onValueChange = { name = it }, label = { Text("Nome") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = integral, onCheckedChange = { integral = it })
                    Text("Usar Taxa Integral")
                }
                if (!integral) TextField(value = fee, onValueChange = { fee = it }, label = { Text("Taxa R$") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsed = if (integral) 0.0 else fee.replace(',', '.').toDoubleOrNull() ?: return@Button
                if (name.isNotBlank()) save(name.trim(), parsed, integral)
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun ReportsScreen(db: DeliveryDb, refreshToken: Int) {
    var mode by rememberSaveable { mutableStateOf("Hoje") }
    var startText by rememberSaveable { mutableStateOf(LocalDate.now().withDayOfMonth(1).format(dateFmt)) }
    var endText by rememberSaveable { mutableStateOf(LocalDate.now().format(dateFmt)) }
    val today = LocalDate.now()
    val range = when (mode) {
        "Hoje" -> today to today
        "Ontem" -> today.minusDays(1) to today.minusDays(1)
        "7 dias" -> today.minusDays(6) to today
        "Mês" -> today.withDayOfMonth(1) to today
        "Ano" -> today.withDayOfYear(1) to today
        else -> (parseDate(startText) ?: today) to (parseDate(endText) ?: today)
    }
    val deliveries = remember(refreshToken, range.first, range.second) { db.deliveriesBetween(range.first, range.second) }
    val total = deliveries.sumOf { it.fee }
    val avg = if (deliveries.isEmpty()) 0.0 else total / deliveries.size
    val counts = deliveries.groupingBy { it.neighborhood }.eachCount().entries.sortedByDescending { it.value }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Relatórios", fontSize = 26.sp, fontWeight = FontWeight.Black)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Hoje", "Ontem", "7 dias", "Mês", "Ano", "Período").forEach { label ->
                    if (mode == label) Button(onClick = { mode = label }) { Text(label) }
                    else OutlinedButton(onClick = { mode = label }) { Text(label) }
                }
            }
        }
        if (mode == "Período") {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(value = startText, onValueChange = { startText = it }, modifier = Modifier.weight(1f), label = { Text("Início") }, singleLine = true)
                    TextField(value = endText, onValueChange = { endText = it }, modifier = Modifier.weight(1f), label = { Text("Fim") }, singleLine = true)
                }
                Text("Formato: dd/mm/aaaa", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Text("${range.first.format(dateFmt)} até ${range.second.format(dateFmt)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Entregas", deliveries.size.toString(), Modifier.weight(1f))
                StatCard("Total", brl(total), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Média", brl(avg), Modifier.weight(1f))
                StatCard("Mais frequente", counts.firstOrNull()?.key ?: "—", Modifier.weight(1f))
            }
        }
        item { Text("Entregas por bairro", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        if (counts.isEmpty()) item { Text("Sem entregas no período.") }
        items(counts) { e ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(e.key, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("${e.value} entrega(s)", color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

class DeliveryDb(context: Context) : SQLiteOpenHelper(context, "bora_maycon.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE neighborhoods (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE COLLATE NOCASE, fee REAL NOT NULL DEFAULT 0, integral INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE deliveries (id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, neighborhood TEXT NOT NULL, fee REAL NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX idx_delivery_date ON deliveries(date)")
        db.execSQL("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        seed(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    private fun seed(db: SQLiteDatabase) {
        val data = listOf(
            Triple("Águas Maravilhosas",8.0,false), Triple("Ajuda de Cima",7.0,false), Triple("Ajuda de Baixo",6.0,false), Triple("Aroeira",3.0,false),
            Triple("Atlântico Norte",7.0,false), Triple("Barra",4.0,false), Triple("Barreto",8.0,false), Triple("Barramares",7.0,false), Triple("Bela Vista",4.0,false),
            Triple("Bosque Azul",7.0,false), Triple("Botafogo",5.0,false), Triple("Brasília",4.0,false), Triple("Brisa do Vale",8.0,false), Triple("Cabiúnas",15.0,false),
            Triple("Cajueiros",3.0,false), Triple("Campo D'Oeste",4.0,false), Triple("Cancela Preta",7.0,false), Triple("Cavaleiros",7.0,false), Triple("Centro",3.0,false),
            Triple("Costa do Sol",4.0,false), Triple("Engenho da Praia",13.0,false), Triple("Franco Plaza",7.0,false), Triple("Fronteira",4.0,false), Triple("Glória",8.0,false),
            Triple("Granja dos Cavaleiros",10.0,false), Triple("Horto",15.0,false), Triple("Imbetiba",3.0,false), Triple("Ilha Leocádia",10.0,false), Triple("Imburo",0.0,true),
            Triple("Jardim Carioca 1",6.0,false), Triple("Jardim Esperança",5.0,false), Triple("Jardim Carioca 2",7.0,false), Triple("Jardim Guanabara",12.0,false),
            Triple("Jardim Maringá",5.0,false), Triple("Jardim Santo Antônio",5.0,false), Triple("Jardim Vitória",5.0,false), Triple("Jardim Franco",7.0,false),
            Triple("Lagoa",10.0,false), Triple("Lagomar",13.0,false), Triple("Maracaibo/Monza",7.0,false), Triple("Marville",7.0,false), Triple("Malvinas",5.0,false),
            Triple("Miramar",3.0,false), Triple("Mirante da Lagoa",12.0,false), Triple("Morro de Santa Mônica",5.0,false), Triple("Nova Esperança",7.0,false),
            Triple("Nova Holanda",7.0,false), Triple("Nova Macaé",5.0,false), Triple("Novo Cavaleiros",10.0,false), Triple("Novo Horizonte",5.0,false),
            Triple("Parque Aeroporto",7.0,false), Triple("Parque Atlântico",7.0,false), Triple("Parque União",7.0,false), Triple("Praia do Pecado",9.0,false),
            Triple("Piracema",8.0,false), Triple("Planalto da Ajuda",7.0,false), Triple("Praia Campista",5.0,false), Triple("Riviera",5.0,false),
            Triple("São Marcos",11.0,false), Triple("Sol e Mar",5.0,false), Triple("Santa Mônica",5.0,false), Triple("Vale das Palmeiras",15.0,false),
            Triple("Vale dos Cristais",15.0,false), Triple("Vale Encantado",0.0,true), Triple("Verdes Mares",7.0,false), Triple("Vila Badejo",5.0,false),
            Triple("Vila Moreira",12.0,false), Triple("Vill. do Horto",15.0,false), Triple("Virgem Santa",7.0,false), Triple("Visconde",3.0,false),
            Triple("Itaparica",5.0,false), Triple("Quinta da Boa Vista",0.0,true), Triple("Fazenda depois da Virgem Santa",0.0,true),
            Triple("Virgem Santa depois do posto de saúde",0.0,true)
        )
        data.forEach { (name, fee, integral) ->
            db.insert("neighborhoods", null, ContentValues().apply {
                put("name", name); put("fee", fee); put("integral", if (integral) 1 else 0)
            })
        }
        db.insert("settings", null, ContentValues().apply { put("key", "integral_fee"); put("value", "0") })
    }

    fun neighborhoods(): List<Neighborhood> {
        val result = mutableListOf<Neighborhood>()
        readableDatabase.query("neighborhoods", arrayOf("id","name","fee","integral"), null, null, null, null, "name COLLATE NOCASE").use { c ->
            while (c.moveToNext()) result += Neighborhood(c.getLong(0), c.getString(1), c.getDouble(2), c.getInt(3) == 1)
        }
        return result
    }

    fun addNeighborhood(name: String, fee: Double, integral: Boolean): Boolean = runCatching {
        writableDatabase.insertOrThrow("neighborhoods", null, ContentValues().apply {
            put("name", name.trim()); put("fee", fee); put("integral", if (integral) 1 else 0)
        }); true
    }.getOrDefault(false)

    fun updateNeighborhood(id: Long, name: String, fee: Double, integral: Boolean): Boolean = runCatching {
        writableDatabase.update("neighborhoods", ContentValues().apply {
            put("name", name.trim()); put("fee", fee); put("integral", if (integral) 1 else 0)
        }, "id=?", arrayOf(id.toString())) > 0
    }.getOrDefault(false)

    fun deleteNeighborhood(id: Long) { writableDatabase.delete("neighborhoods", "id=?", arrayOf(id.toString())) }

    fun addDelivery(date: LocalDate, neighborhood: String, fee: Double) {
        writableDatabase.insert("deliveries", null, ContentValues().apply {
            put("date", date.toString()); put("neighborhood", neighborhood); put("fee", fee); put("created_at", System.currentTimeMillis())
        })
    }

    fun deleteDelivery(id: Long) { writableDatabase.delete("deliveries", "id=?", arrayOf(id.toString())) }
    fun deliveriesOn(date: LocalDate): List<Delivery> = deliveriesBetween(date, date)

    fun deliveriesBetween(start: LocalDate, end: LocalDate): List<Delivery> {
        val result = mutableListOf<Delivery>()
        readableDatabase.query(
            "deliveries", arrayOf("id","date","neighborhood","fee","created_at"),
            "date>=? AND date<=?", arrayOf(start.toString(), end.toString()), null, null, "date DESC, created_at DESC"
        ).use { c ->
            while (c.moveToNext()) result += Delivery(c.getLong(0), LocalDate.parse(c.getString(1)), c.getString(2), c.getDouble(3), c.getLong(4))
        }
        return result
    }

    fun allDeliveries(): List<Delivery> {
        val result = mutableListOf<Delivery>()
        readableDatabase.query("deliveries", arrayOf("id","date","neighborhood","fee","created_at"), null, null, null, null, "date DESC, created_at DESC").use { c ->
            while (c.moveToNext()) result += Delivery(c.getLong(0), LocalDate.parse(c.getString(1)), c.getString(2), c.getDouble(3), c.getLong(4))
        }
        return result
    }

    fun yearStats(year: Int): Map<LocalDate, DaySummary> {
        val result = linkedMapOf<LocalDate, DaySummary>()
        val start = LocalDate.of(year, 1, 1).toString()
        val end = LocalDate.of(year, 12, 31).toString()
        readableDatabase.rawQuery("SELECT date, COUNT(*), SUM(fee) FROM deliveries WHERE date>=? AND date<=? GROUP BY date", arrayOf(start, end)).use { c ->
            while (c.moveToNext()) result[LocalDate.parse(c.getString(0))] = DaySummary(c.getInt(1), c.getDouble(2))
        }
        return result
    }

    fun getIntegralFee(): Double {
        readableDatabase.rawQuery("SELECT value FROM settings WHERE key='integral_fee'", null).use { c ->
            return if (c.moveToFirst()) c.getString(0).toDoubleOrNull() ?: 0.0 else 0.0
        }
    }

    fun setIntegralFee(value: Double) {
        writableDatabase.insertWithOnConflict("settings", null, ContentValues().apply {
            put("key", "integral_fee"); put("value", value.toString())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun exportJson(): String {
        val root = JSONObject()
        val ns = JSONArray()
        neighborhoods().forEach { n -> ns.put(JSONObject().apply {
            put("name", n.name); put("fee", n.fee); put("integral", n.integral)
        }) }
        val ds = JSONArray()
        allDeliveries().forEach { d -> ds.put(JSONObject().apply {
            put("date", d.date.toString()); put("neighborhood", d.neighborhood); put("fee", d.fee); put("created_at", d.createdAt)
        }) }
        root.put("version", 1)
        root.put("integral_fee", getIntegralFee())
        root.put("neighborhoods", ns)
        root.put("deliveries", ds)
        return root.toString(2)
    }

    fun restoreJson(text: String) {
        val root = JSONObject(text)
        val ns = root.getJSONArray("neighborhoods")
        val ds = root.getJSONArray("deliveries")
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("deliveries", null, null)
            db.delete("neighborhoods", null, null)
            for (i in 0 until ns.length()) {
                val o = ns.getJSONObject(i)
                db.insertOrThrow("neighborhoods", null, ContentValues().apply {
                    put("name", o.getString("name")); put("fee", o.optDouble("fee", 0.0)); put("integral", if (o.optBoolean("integral", false)) 1 else 0)
                })
            }
            for (i in 0 until ds.length()) {
                val o = ds.getJSONObject(i)
                db.insertOrThrow("deliveries", null, ContentValues().apply {
                    put("date", o.getString("date")); put("neighborhood", o.getString("neighborhood")); put("fee", o.getDouble("fee")); put("created_at", o.optLong("created_at", System.currentTimeMillis()))
                })
            }
            db.insertWithOnConflict("settings", null, ContentValues().apply {
                put("key", "integral_fee"); put("value", root.optDouble("integral_fee", 0.0).toString())
            }, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
