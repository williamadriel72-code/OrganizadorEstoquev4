package com.organizador.scanner

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

private val PtBr = Locale("pt", "BR")
private val DateBr = DateTimeFormatter.ofPattern("dd/MM/yyyy", PtBr)

private enum class AppPage(val title: String, val symbol: String) {
    HOME("Hoje", "●"),
    CALENDAR("Calendário", "▦"),
    HISTORY("Histórico", "≡"),
    REPORTS("Relatórios", "Σ"),
    RATES("Taxas", "R$")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = DeliveryDb(this)
        setContent {
            BoraMayconTheme {
                BoraMayconApp(db)
            }
        }
    }
}

@Composable
private fun BoraMayconTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Color(0xFFFF5A36),
        onPrimary = Color.White,
        secondary = Color(0xFFFFB24A),
        background = Color(0xFF101113),
        surface = Color(0xFF1A1C1F),
        surfaceVariant = Color(0xFF24272B),
        onBackground = Color(0xFFF7F7F7),
        onSurface = Color(0xFFF7F7F7),
        onSurfaceVariant = Color(0xFFC7C9CE),
        outline = Color(0xFF44484F)
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
private fun BoraMayconApp(db: DeliveryDb) {
    var page by remember { mutableStateOf(AppPage.HOME) }
    var revision by remember { mutableIntStateOf(0) }
    val refresh: () -> Unit = { revision += 1 }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF17191C)) {
                AppPage.entries.forEach { item ->
                    NavigationBarItem(
                        selected = page == item,
                        onClick = { page = item },
                        icon = { Text(item.symbol, fontWeight = FontWeight.Black) },
                        label = { Text(item.title, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (page) {
                AppPage.HOME -> HomeScreen(db, revision, refresh)
                AppPage.CALENDAR -> CalendarScreen(db, revision)
                AppPage.HISTORY -> HistoryScreen(db, revision, refresh)
                AppPage.REPORTS -> ReportsScreen(db, revision)
                AppPage.RATES -> RatesScreen(db, revision, refresh)
            }
        }
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
        if (subtitle != null) {
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ProfileAvatar() {
    Box(
        modifier = Modifier
            .size(92.dp)
            .clip(CircleShape)
            .background(Color(0xFF171719)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension
            drawCircle(Color(0xFF202124), radius = r * .49f)
            for (i in 0..7) {
                drawArc(
                    color = Color(0xFF303238),
                    startAngle = 200f + i * 10,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(r * .08f, r * (.12f + i * .025f)),
                    size = androidx.compose.ui.geometry.Size(r * .84f, r * .55f),
                    style = Stroke(width = 1.2f)
                )
            }
            drawCircle(Color(0xFFE23C33), radius = r * .30f, center = androidx.compose.ui.geometry.Offset(r * .43f, r * .53f))
            val p1 = Path().apply {
                moveTo(r * .23f, r * .79f)
                lineTo(r * .50f, r * .50f)
                lineTo(r * .29f, r * .80f)
                close()
            }
            val p2 = Path().apply {
                moveTo(r * .35f, r * .80f)
                lineTo(r * .52f, r * .49f)
                lineTo(r * .42f, r * .81f)
                close()
            }
            drawPath(p1, Color(0xFFFFF6E9))
            drawPath(p2, Color(0xFFFFF6E9))
            repeat(4) { i ->
                drawArc(
                    color = Color(0xFFFFF6E9),
                    startAngle = 220f + i * 12,
                    sweepAngle = 85f,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(r * (.38f + i * .04f), r * (.24f + i * .015f)),
                    size = androidx.compose.ui.geometry.Size(r * .33f, r * .35f),
                    style = Stroke(width = r * .05f, cap = StrokeCap.Round)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(db: DeliveryDb, revision: Int, refresh: () -> Unit) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val dateKey = today.toString()
    val deliveries = remember(revision, dateKey) { db.deliveriesForDate(dateKey) }
    val summary = remember(deliveries) { Summary(deliveries.size, deliveries.sumOf { it.feeCents }) }
    var addSheet by remember { mutableStateOf(false) }
    var editDelivery by remember { mutableStateOf<Delivery?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, 30.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatar()
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Bora Maycon Hi Hi", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Rotas, Entregas e Taxas", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                    Text(today.format(DateBr), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("ENTREGAS", summary.count.toString(), Modifier.weight(1f))
                MetricCard("TOTAL DO DIA", money(summary.totalCents), Modifier.weight(1f))
            }
        }
        item {
            Button(
                onClick = { addSheet = true },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("+  ADICIONAR ENTREGA", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        item { Text("Entregas de hoje", fontSize = 19.sp, fontWeight = FontWeight.Bold) }
        if (deliveries.isEmpty()) {
            item { EmptyCard("Nenhuma entrega lançada hoje.") }
        } else {
            items(deliveries, key = { it.id }) { d ->
                DeliveryRow(
                    delivery = d,
                    onEdit = { editDelivery = d },
                    onDelete = {
                        db.deleteDelivery(d.id)
                        refresh()
                        Toast.makeText(context, "Entrega excluída", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    if (addSheet) {
        NeighborhoodPickerSheet(
            db = db,
            title = "Adicionar entrega",
            onDismiss = { addSheet = false },
            onPick = { n ->
                runCatching { db.addDelivery(dateKey, n) }
                    .onSuccess {
                        refresh()
                        Toast.makeText(context, "${n.name} adicionada", Toast.LENGTH_SHORT).show()
                    }
                    .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
            }
        )
    }

    editDelivery?.let { delivery ->
        NeighborhoodPickerSheet(
            db = db,
            title = "Corrigir entrega",
            onDismiss = { editDelivery = null },
            onPick = { n ->
                runCatching { db.updateDelivery(delivery.id, n) }
                    .onSuccess {
                        editDelivery = null
                        refresh()
                        Toast.makeText(context, "Entrega corrigida", Toast.LENGTH_SHORT).show()
                    }
                    .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
            }
        )
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun EmptyCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Text(text, Modifier.fillMaxWidth().padding(22.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DeliveryRow(delivery: Delivery, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(delivery.neighborhood, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(money(delivery.feeCents), color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onEdit) { Text("CORRIGIR") }
            TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF7676))) { Text("EXCLUIR") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NeighborhoodPickerSheet(db: DeliveryDb, title: String, onDismiss: () -> Unit, onPick: (Neighborhood) -> Unit) {
    var search by remember { mutableStateOf("") }
    val neighborhoods = remember(search) { db.listNeighborhoods(search) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 28.dp)) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Pesquisar bairro") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 470.dp)) {
                items(neighborhoods, key = { it.id }) { n ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(n) }.padding(vertical = 13.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(n.name, fontWeight = FontWeight.SemiBold)
                            Text(if (n.integral) "Taxa Integral" else money(n.feeCents), color = if (n.integral) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                        }
                        Text("+", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .4f))
                }
            }
        }
    }
}

@Composable
private fun CalendarScreen(db: DeliveryDb, revision: Int) {
    var year by remember { mutableIntStateOf(LocalDate.now().year) }
    val summaries = remember(revision, year) { db.dailySummaries(year) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ScreenHeader("Calendário anual", "Toque em um dia para ver o total de entregas.")
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(onClick = { year-- }) { Text("‹") }
                Text(year.toString(), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                OutlinedButton(onClick = { year++ }) { Text("›") }
            }
        }
        items((1..12).toList()) { month -> MonthCard(YearMonth.of(year, month), summaries) { selectedDate = it } }
    }

    selectedDate?.let { date ->
        val deliveries = remember(revision, date) { db.deliveriesForDate(date.toString()) }
        val total = deliveries.sumOf { it.feeCents }
        AlertDialog(
            onDismissRequest = { selectedDate = null },
            confirmButton = { TextButton(onClick = { selectedDate = null }) { Text("FECHAR") } },
            title = { Text(date.format(DateBr), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${deliveries.size} entrega(s)", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(money(total), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.secondary)
                    if (deliveries.isNotEmpty()) {
                        HorizontalDivider()
                        deliveries.take(12).forEach { Text("• ${it.neighborhood} — ${money(it.feeCents)}", fontSize = 13.sp) }
                        if (deliveries.size > 12) Text("+ ${deliveries.size - 12} entrega(s)")
                    }
                }
            }
        )
    }
}

@Composable
private fun MonthCard(month: YearMonth, summaries: Map<String, Summary>, onDay: (LocalDate) -> Unit) {
    val name = month.month.getDisplayName(TextStyle.FULL, PtBr).replaceFirstChar { it.uppercase() }
    val first = month.atDay(1)
    val offset = first.dayOfWeek.value % 7
    val cells = offset + month.lengthOfMonth()
    val rows = (cells + 6) / 7
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(name, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) { listOf("D", "S", "T", "Q", "Q", "S", "S").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            repeat(rows) { row ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { col ->
                        val cell = row * 7 + col
                        val day = cell - offset + 1
                        if (day in 1..month.lengthOfMonth()) {
                            val date = month.atDay(day)
                            val sum = summaries[date.toString()]
                            Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp).clip(RoundedCornerShape(8.dp)).background(if (sum != null) MaterialTheme.colorScheme.primary.copy(alpha = .17f) else Color.Transparent).clickable { onDay(date) }, contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(day.toString(), fontSize = 12.sp, fontWeight = if (sum != null) FontWeight.Bold else FontWeight.Normal)
                                    if (sum != null) Box(Modifier.size(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                                }
                            }
                        } else Spacer(Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(db: DeliveryDb, revision: Int, refresh: () -> Unit) {
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }
    val all = remember(revision) { db.allDeliveries() }
    val filtered = remember(all, search) {
        val q = search.trim()
        if (q.isBlank()) all else all.filter { it.neighborhood.contains(q, ignoreCase = true) || it.date.contains(q) || runCatching { LocalDate.parse(it.date).format(DateBr).contains(q) }.getOrDefault(false) }
    }
    val total = filtered.sumOf { it.feeCents }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, 30.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            ScreenHeader("Histórico", "Pesquise por bairro ou data.")
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { Text("Bairro ou data") }, singleLine = true)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("ENTREGAS", filtered.size.toString(), Modifier.weight(1f))
                MetricCard("TOTAL", money(total), Modifier.weight(1f))
            }
        }
        if (filtered.isEmpty()) item { EmptyCard("Nenhum lançamento encontrado.") }
        else items(filtered.take(500), key = { it.id }) { d ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(d.neighborhood, fontWeight = FontWeight.Bold)
                        Text("${formatDate(d.date)}  •  ${money(d.feeCents)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { db.deleteDelivery(d.id); refresh(); Toast.makeText(context, "Entrega excluída", Toast.LENGTH_SHORT).show() }, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF7676))) { Text("EXCLUIR") }
                }
            }
        }
    }
}

private enum class ReportRange(val label: String) { TODAY("Hoje"), YESTERDAY("Ontem"), WEEK("Semana"), MONTH("Mês"), YEAR("Ano"), CUSTOM("Período") }

@Composable
private fun ReportsScreen(db: DeliveryDb, revision: Int) {
    val context = LocalContext.current
    var range by remember { mutableStateOf(ReportRange.TODAY) }
    var customStart by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var customEnd by remember { mutableStateOf(LocalDate.now()) }
    val today = LocalDate.now()
    val period = remember(range, customStart, customEnd, today) {
        when (range) {
            ReportRange.TODAY -> today to today
            ReportRange.YESTERDAY -> today.minusDays(1) to today.minusDays(1)
            ReportRange.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) to today
            ReportRange.MONTH -> today.withDayOfMonth(1) to today
            ReportRange.YEAR -> today.withDayOfYear(1) to today
            ReportRange.CUSTOM -> if (customStart <= customEnd) customStart to customEnd else customEnd to customStart
        }
    }
    val summary = remember(revision, period) { db.summaryBetween(period.first.toString(), period.second.toString()) }
    val stats = remember(revision, period) { db.neighborhoodStats(period.first.toString(), period.second.toString()) }
    val average = if (summary.count == 0) 0 else summary.totalCents / summary.count

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, 30.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            ScreenHeader("Relatórios", "Resumo por dia, semana, mês, ano ou período.")
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                ReportRange.entries.forEach { item -> FilterChip(selected = range == item, onClick = { range = item }, label = { Text(item.label) }) }
            }
            if (range == ReportRange.CUSTOM) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showDatePicker(context, customStart) { customStart = it } }, Modifier.weight(1f)) { Text("DE ${customStart.format(DateBr)}") }
                    OutlinedButton(onClick = { showDatePicker(context, customEnd) { customEnd = it } }, Modifier.weight(1f)) { Text("ATÉ ${customEnd.format(DateBr)}") }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("${period.first.format(DateBr)} a ${period.second.format(DateBr)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MetricCard("ENTREGAS", summary.count.toString(), Modifier.weight(1f)); MetricCard("TOTAL", money(summary.totalCents), Modifier.weight(1f)) } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MetricCard("MÉDIA", money(average), Modifier.weight(1f)); MetricCard("MAIS ENTREGAS", stats.firstOrNull()?.name ?: "—", Modifier.weight(1f)) } }
        item { Text("Entregas por bairro", fontSize = 19.sp, fontWeight = FontWeight.Bold) }
        if (stats.isEmpty()) item { EmptyCard("Nenhuma entrega no período.") }
        else items(stats) { s ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(13.dp)) {
                    Column(Modifier.weight(1f)) { Text(s.name, fontWeight = FontWeight.Bold); Text("${s.count} entrega(s)", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text(money(s.totalCents), color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RatesScreen(db: DeliveryDb, revision: Int, refresh: () -> Unit) {
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }
    val neighborhoods = remember(revision, search) { db.listNeighborhoods(search) }
    var editing by remember { mutableStateOf<Neighborhood?>(null) }
    var adding by remember { mutableStateOf(false) }
    var integralText by remember(revision) { mutableStateOf(centsToInput(db.getIntegralFeeCents())) }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(db.exportJson()) } ?: error("Não foi possível abrir o arquivo.") }
                .onSuccess { Toast.makeText(context, "Backup salvo", Toast.LENGTH_LONG).show() }
                .onFailure { Toast.makeText(context, "Erro no backup: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Não foi possível ler o arquivo.")
                db.importJson(text)
            }.onSuccess {
                integralText = centsToInput(db.getIntegralFeeCents())
                refresh()
                Toast.makeText(context, "Backup restaurado", Toast.LENGTH_LONG).show()
            }.onFailure { Toast.makeText(context, "Backup inválido: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, 30.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            ScreenHeader("Bairros e taxas", "Edite os valores sempre que a tabela mudar.")
            Spacer(Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("VALOR DA TAXA INTEGRAL", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    Spacer(Modifier.height(7.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(integralText, { integralText = it }, Modifier.weight(1f), prefix = { Text("R$ ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        Button(onClick = {
                            val cents = parseMoney(integralText)
                            if (cents == null || cents <= 0) Toast.makeText(context, "Digite um valor válido", Toast.LENGTH_SHORT).show()
                            else { db.setIntegralFeeCents(cents); refresh(); Toast.makeText(context, "Taxa Integral salva", Toast.LENGTH_SHORT).show() }
                        }) { Text("SALVAR") }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { adding = true }, Modifier.weight(1f)) { Text("+ NOVO BAIRRO") }
                OutlinedButton(onClick = { backupLauncher.launch("bora-maycon-backup-${LocalDate.now()}.json") }, Modifier.weight(1f)) { Text("BACKUP") }
                OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("application/json", "text/plain")) }, Modifier.weight(1f)) { Text("RESTAURAR") }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { Text("Pesquisar bairro") }, singleLine = true)
        }
        items(neighborhoods, key = { it.id }) { n ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(n.name, fontWeight = FontWeight.Bold); Text(if (n.integral) "Taxa Integral" else money(n.feeCents), color = if (n.integral) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary) }
                    TextButton(onClick = { editing = n }) { Text("EDITAR") }
                    TextButton(onClick = { db.deleteNeighborhood(n.id); refresh() }, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF7676))) { Text("EXCLUIR") }
                }
            }
        }
    }

    if (adding) {
        NeighborhoodDialog(null, onDismiss = { adding = false }) { name, fee, integral ->
            runCatching { db.saveNeighborhood(null, name, fee, integral) }.onSuccess { adding = false; refresh() }.onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
        }
    }
    editing?.let { n ->
        NeighborhoodDialog(n, onDismiss = { editing = null }) { name, fee, integral ->
            runCatching { db.saveNeighborhood(n.id, name, fee, integral) }.onSuccess { editing = null; refresh() }.onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
        }
    }
}

@Composable
private fun NeighborhoodDialog(initial: Neighborhood?, onDismiss: () -> Unit, onSave: (String, Int, Boolean) -> Unit) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var integral by remember(initial) { mutableStateOf(initial?.integral ?: false) }
    var fee by remember(initial) { mutableStateOf(centsToInput(initial?.feeCents ?: 0)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Novo bairro" else "Editar bairro") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Usar Taxa Integral", Modifier.weight(1f)); Switch(checked = integral, onCheckedChange = { integral = it }) }
                if (!integral) OutlinedTextField(fee, { fee = it }, label = { Text("Taxa") }, prefix = { Text("R$ ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                val cents = if (integral) 0 else parseMoney(fee)
                if (name.isNotBlank() && cents != null) onSave(name.trim(), cents, integral)
            }) { Text("SALVAR") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCELAR") } }
    )
}

private fun showDatePicker(context: android.content.Context, initial: LocalDate, onDate: (LocalDate) -> Unit) {
    DatePickerDialog(context, { _, y, m, d -> onDate(LocalDate.of(y, m + 1, d)) }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
}

private fun money(cents: Int): String = NumberFormat.getCurrencyInstance(PtBr).format(cents / 100.0)
private fun centsToInput(cents: Int): String = String.format(PtBr, "%.2f", cents / 100.0)
private fun parseMoney(text: String): Int? {
    val clean = text.trim().replace("R$", "").replace(" ", "")
    if (clean.isBlank()) return 0
    val normalized = if (clean.contains(',')) clean.replace(".", "").replace(',', '.') else clean
    return normalized.toDoubleOrNull()?.let { (it * 100.0).roundToInt().coerceAtLeast(0) }
}
private fun formatDate(iso: String): String = runCatching { LocalDate.parse(iso).format(DateBr) }.getOrDefault(iso)
