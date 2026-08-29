package com.organizador.scanner

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val brLocale = Locale("pt", "BR")
private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy", brLocale)
private fun money(cents: Int): String = NumberFormat.getCurrencyInstance(brLocale).format(cents / 100.0)

data class Neighborhood(
    val id: Long,
    val name: String,
    val feeCents: Int,
    val integral: Boolean
)

data class DeliveryEntry(
    val id: Long,
    val neighborhoodName: String,
    val feeCents: Int,
    val date: String,
    val createdAt: Long
)

data class DaySummary(val count: Int, val totalCents: Int)

class MainActivity : ComponentActivity() {
    private lateinit var db: DeliveryDb
    private var refreshTick by mutableIntStateOf(0)

    private val createBackup = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(db.exportJson()) }
            }.onSuccess {
                Toast.makeText(this, "Backup salvo com sucesso", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this, "Falha ao salvar backup", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val openBackup = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Arquivo vazio")
                db.importJson(json)
            }.onSuccess {
                refreshTick++
                Toast.makeText(this, "Backup restaurado", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this, "Backup inválido", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = DeliveryDb(this)
        setContent {
            BoraMayconApp(
                db = db,
                refreshTick = refreshTick,
                onChanged = { refreshTick++ },
                onExport = { createBackup.launch("bora-maycon-backup-${LocalDate.now()}.json") },
                onImport = { openBackup.launch(arrayOf("application/json", "text/plain")) }
            )
        }
    }
}

class DeliveryDb(context: Context) : SQLiteOpenHelper(context, "bora_maycon.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE neighborhoods(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE COLLATE NOCASE,
                fee_cents INTEGER NOT NULL,
                integral INTEGER NOT NULL DEFAULT 0
            )""".trimIndent()
        )
        db.execSQL(
            """CREATE TABLE deliveries(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                neighborhood_id INTEGER,
                neighborhood_name TEXT NOT NULL,
                fee_cents INTEGER NOT NULL,
                delivery_date TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )""".trimIndent()
        )
        db.execSQL("CREATE INDEX idx_delivery_date ON deliveries(delivery_date)")
        db.execSQL("CREATE INDEX idx_delivery_name ON deliveries(neighborhood_name)")
        db.execSQL("CREATE TABLE settings(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        db.execSQL("INSERT INTO settings(key,value) VALUES('integral_fee','0')")
        seedNeighborhoods(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    private fun seedNeighborhoods(db: SQLiteDatabase) {
        val rows = listOf(
            Triple("Águas Maravilhosas", 800, false),
            Triple("Ajuda de Cima", 700, false),
            Triple("Ajuda de Baixo", 600, false),
            Triple("Aroeira", 300, false),
            Triple("Atlântico Norte", 700, false),
            Triple("Barra", 400, false),
            Triple("Barreto", 800, false),
            Triple("Barramares", 700, false),
            Triple("Bela Vista", 400, false),
            Triple("Bosque Azul", 700, false),
            Triple("Botafogo", 500, false),
            Triple("Brasília", 400, false),
            Triple("Brisa do Vale", 800, false),
            Triple("Cabiúnas", 1500, false),
            Triple("Cajueiros", 300, false),
            Triple("Campo D'Oeste", 400, false),
            Triple("Cancela Preta", 700, false),
            Triple("Cavaleiros", 700, false),
            Triple("Centro", 300, false),
            Triple("Costa do Sol", 400, false),
            Triple("Engenho da Praia", 1300, false),
            Triple("Franco Plaza", 700, false),
            Triple("Fronteira", 400, false),
            Triple("Glória", 800, false),
            Triple("Granja dos Cavaleiros", 1000, false),
            Triple("Horto", 1500, false),
            Triple("Imbetiba", 300, false),
            Triple("Ilha Leocádia", 1000, false),
            Triple("Jardim Carioca 1", 600, false),
            Triple("Jardim Esperança", 500, false),
            Triple("Jardim Carioca 2", 700, false),
            Triple("Jardim Guanabara", 1200, false),
            Triple("Jardim Maringá", 500, false),
            Triple("Jardim Santo Antônio", 500, false),
            Triple("Jardim Vitória", 500, false),
            Triple("Jardim Franco", 700, false),
            Triple("Lagoa", 1000, false),
            Triple("Lagomar", 1300, false),
            Triple("Malvinas", 500, false),
            Triple("Maracaibo/Monza", 700, false),
            Triple("Marville", 700, false),
            Triple("Miramar", 300, false),
            Triple("Mirante da Lagoa", 1200, false),
            Triple("Morro de Santa Mônica", 500, false),
            Triple("Nova Esperança", 700, false),
            Triple("Nova Holanda", 700, false),
            Triple("Nova Macaé", 500, false),
            Triple("Novo Cavaleiros", 1000, false),
            Triple("Novo Horizonte", 500, false),
            Triple("Parque Aeroporto", 700, false),
            Triple("Parque Atlântico", 700, false),
            Triple("Parque União", 700, false),
            Triple("Praia do Pecado", 900, false),
            Triple("Piracema", 800, false),
            Triple("Planalto da Ajuda", 700, false),
            Triple("Praia Campista", 500, false),
            Triple("Riviera", 500, false),
            Triple("São Marcos", 1100, false),
            Triple("Sol e Mar", 500, false),
            Triple("Santa Mônica", 500, false),
            Triple("Vale das Palmeiras", 1500, false),
            Triple("Vale dos Cristais", 1500, false),
            Triple("Vale Encantado", 0, true),
            Triple("Verdes Mares", 700, false),
            Triple("Vila Badejo", 500, false),
            Triple("Vila Moreira", 1200, false),
            Triple("Vill. do Horto", 1500, false),
            Triple("Virgem Santa", 700, false),
            Triple("Visconde", 300, false),
            Triple("Imburo", 0, true),
            Triple("Quinta da Boa Vista", 0, true),
            Triple("Fazenda depois da Virgem Santa", 0, true),
            Triple("Virgem Santa depois do posto de saúde", 0, true),
            Triple("Itaparica", 500, false)
        )
        rows.forEach { (name, fee, integral) ->
            db.execSQL(
                "INSERT OR IGNORE INTO neighborhoods(name,fee_cents,integral) VALUES(?,?,?)",
                arrayOf(name, fee, if (integral) 1 else 0)
            )
        }
    }

    fun allNeighborhoods(): List<Neighborhood> {
        val out = mutableListOf<Neighborhood>()
        readableDatabase.rawQuery(
            "SELECT id,name,fee_cents,integral FROM neighborhoods ORDER BY name COLLATE NOCASE",
            null
        ).use { c ->
            while (c.moveToNext()) {
                out += Neighborhood(c.getLong(0), c.getString(1), c.getInt(2), c.getInt(3) == 1)
            }
        }
        return out
    }

    fun addNeighborhood(name: String, feeCents: Int, integral: Boolean): Boolean {
        return runCatching {
            writableDatabase.execSQL(
                "INSERT INTO neighborhoods(name,fee_cents,integral) VALUES(?,?,?)",
                arrayOf(name.trim(), feeCents.coerceAtLeast(0), if (integral) 1 else 0)
            )
            true
        }.getOrDefault(false)
    }

    fun updateNeighborhood(id: Long, name: String, feeCents: Int, integral: Boolean) {
        writableDatabase.execSQL(
            "UPDATE neighborhoods SET name=?,fee_cents=?,integral=? WHERE id=?",
            arrayOf(name.trim(), feeCents.coerceAtLeast(0), if (integral) 1 else 0, id)
        )
    }

    fun deleteNeighborhood(id: Long) {
        writableDatabase.execSQL("DELETE FROM neighborhoods WHERE id=?", arrayOf(id))
    }

    fun integralFee(): Int {
        readableDatabase.rawQuery("SELECT value FROM settings WHERE key='integral_fee'", null).use { c ->
            return if (c.moveToFirst()) c.getString(0).toIntOrNull() ?: 0 else 0
        }
    }

    fun setIntegralFee(cents: Int) {
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO settings(key,value) VALUES('integral_fee',?)",
            arrayOf(cents.coerceAtLeast(0).toString())
        )
    }

    fun addDelivery(n: Neighborhood, date: LocalDate = LocalDate.now()): Boolean {
        val fee = if (n.integral) integralFee() else n.feeCents
        if (n.integral && fee <= 0) return false
        writableDatabase.execSQL(
            "INSERT INTO deliveries(neighborhood_id,neighborhood_name,fee_cents,delivery_date,created_at) VALUES(?,?,?,?,?)",
            arrayOf(n.id, n.name, fee, date.toString(), System.currentTimeMillis())
        )
        return true
    }

    fun deleteDelivery(id: Long) {
        writableDatabase.execSQL("DELETE FROM deliveries WHERE id=?", arrayOf(id))
    }

    fun deliveriesFor(date: LocalDate): List<DeliveryEntry> = deliveriesFor(date.toString())

    private fun deliveriesFor(date: String): List<DeliveryEntry> {
        val out = mutableListOf<DeliveryEntry>()
        readableDatabase.rawQuery(
            "SELECT id,neighborhood_name,fee_cents,delivery_date,created_at FROM deliveries WHERE delivery_date=? ORDER BY created_at DESC",
            arrayOf(date)
        ).use { c ->
            while (c.moveToNext()) {
                out += DeliveryEntry(c.getLong(0), c.getString(1), c.getInt(2), c.getString(3), c.getLong(4))
            }
        }
        return out
    }

    fun history(query: String): List<DeliveryEntry> {
        val out = mutableListOf<DeliveryEntry>()
        val q = query.trim()
        val sql: String
        val args: Array<String>?
        if (q.isBlank()) {
            sql = "SELECT id,neighborhood_name,fee_cents,delivery_date,created_at FROM deliveries ORDER BY delivery_date DESC,created_at DESC LIMIT 1000"
            args = null
        } else {
            sql = "SELECT id,neighborhood_name,fee_cents,delivery_date,created_at FROM deliveries WHERE neighborhood_name LIKE ? OR delivery_date LIKE ? ORDER BY delivery_date DESC,created_at DESC LIMIT 1000"
            args = arrayOf("%$q%", "%$q%")
        }
        readableDatabase.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) {
                out += DeliveryEntry(c.getLong(0), c.getString(1), c.getInt(2), c.getString(3), c.getLong(4))
            }
        }
        return out
    }

    fun daySummaries(year: Int): Map<LocalDate, DaySummary> {
        val out = mutableMapOf<LocalDate, DaySummary>()
        readableDatabase.rawQuery(
            "SELECT delivery_date,COUNT(*),COALESCE(SUM(fee_cents),0) FROM deliveries WHERE delivery_date LIKE ? GROUP BY delivery_date",
            arrayOf("$year-%")
        ).use { c ->
            while (c.moveToNext()) {
                runCatching { LocalDate.parse(c.getString(0)) }.getOrNull()?.let {
                    out[it] = DaySummary(c.getInt(1), c.getInt(2))
                }
            }
        }
        return out
    }

    fun exportJson(): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("integralFee", integralFee())
        val ns = JSONArray()
        allNeighborhoods().forEach { n ->
            ns.put(JSONObject().put("name", n.name).put("fee", n.feeCents).put("integral", n.integral))
        }
        root.put("neighborhoods", ns)
        val ds = JSONArray()
        history("").forEach { d ->
            ds.put(
                JSONObject()
                    .put("name", d.neighborhoodName)
                    .put("fee", d.feeCents)
                    .put("date", d.date)
                    .put("createdAt", d.createdAt)
            )
        }
        root.put("deliveries", ds)
        return root.toString(2)
    }

    fun importJson(text: String) {
        val root = JSONObject(text)
        val ns = root.getJSONArray("neighborhoods")
        val ds = root.getJSONArray("deliveries")
        writableDatabase.beginTransaction()
        try {
            writableDatabase.execSQL("DELETE FROM deliveries")
            writableDatabase.execSQL("DELETE FROM neighborhoods")
            for (i in 0 until ns.length()) {
                val o = ns.getJSONObject(i)
                writableDatabase.execSQL(
                    "INSERT INTO neighborhoods(name,fee_cents,integral) VALUES(?,?,?)",
                    arrayOf(o.getString("name"), o.getInt("fee"), if (o.optBoolean("integral")) 1 else 0)
                )
            }
            for (i in 0 until ds.length()) {
                val o = ds.getJSONObject(i)
                writableDatabase.execSQL(
                    "INSERT INTO deliveries(neighborhood_id,neighborhood_name,fee_cents,delivery_date,created_at) VALUES(NULL,?,?,?,?)",
                    arrayOf(o.getString("name"), o.getInt("fee"), o.getString("date"), o.optLong("createdAt", System.currentTimeMillis()))
                )
            }
            setIntegralFee(root.optInt("integralFee", 0))
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }
}

enum class AppScreen { TODAY, CALENDAR, HISTORY, NEIGHBORHOODS }

@Composable
private fun BoraMayconApp(
    db: DeliveryDb,
    refreshTick: Int,
    onChanged: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    val colors = darkColorScheme(
        primary = Color(0xFFFFC107),
        onPrimary = Color(0xFF171717),
        background = Color(0xFF0F1115),
        surface = Color(0xFF191C22),
        surfaceVariant = Color(0xFF242933),
        onBackground = Color(0xFFF5F5F5),
        onSurface = Color(0xFFF5F5F5)
    )
    var screen by remember { mutableStateOf(AppScreen.TODAY) }

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF15181E)) {
                    NavigationBarItem(
                        selected = screen == AppScreen.TODAY,
                        onClick = { screen = AppScreen.TODAY },
                        icon = { Text("🏍") },
                        label = { Text("Hoje") }
                    )
                    NavigationBarItem(
                        selected = screen == AppScreen.CALENDAR,
                        onClick = { screen = AppScreen.CALENDAR },
                        icon = { Text("📅") },
                        label = { Text("Calendário") }
                    )
                    NavigationBarItem(
                        selected = screen == AppScreen.HISTORY,
                        onClick = { screen = AppScreen.HISTORY },
                        icon = { Text("🧾") },
                        label = { Text("Histórico") }
                    )
                    NavigationBarItem(
                        selected = screen == AppScreen.NEIGHBORHOODS,
                        onClick = { screen = AppScreen.NEIGHBORHOODS },
                        icon = { Text("⚙") },
                        label = { Text("Taxas") }
                    )
                }
            }
        ) { pad ->
            Surface(
                Modifier.fillMaxSize().padding(pad),
                color = MaterialTheme.colorScheme.background
            ) {
                when (screen) {
                    AppScreen.TODAY -> TodayScreen(db, refreshTick, onChanged)
                    AppScreen.CALENDAR -> CalendarScreen(db, refreshTick)
                    AppScreen.HISTORY -> HistoryScreen(db, refreshTick, onChanged)
                    AppScreen.NEIGHBORHOODS -> NeighborhoodsScreen(
                        db, refreshTick, onChanged, onExport, onImport
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayScreen(db: DeliveryDb, refreshTick: Int, onChanged: () -> Unit) {
    val today = LocalDate.now()
    val deliveries = remember(refreshTick) { db.deliveriesFor(today) }
    val total = deliveries.sumOf { it.feeCents }
    var addOpen by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Image(
                    painter = painterResource(id = R.drawable.profile_maycon),
                    contentDescription = "Foto de perfil",
                    modifier = Modifier.size(94.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(8.dp))
                Text("Bora Maycon Hi Hi", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                Text("Rotas, entregas e taxas", color = Color(0xFFADB4C0))
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1F27)),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text(today.format(dateFmt), color = Color(0xFFFFC107), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SummaryValue("Entregas", deliveries.size.toString())
                        SummaryValue("Total do dia", money(total))
                    }
                }
            }
        }
        item {
            Button(
                onClick = { addOpen = true },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("+ ADICIONAR ENTREGA", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
        }
        item {
            Text("Entregas de hoje", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
        if (deliveries.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF191C22))) {
                    Text(
                        "Nenhuma entrega registrada hoje.",
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        color = Color(0xFF9EA6B3),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(deliveries, key = { it.id }) { d ->
                DeliveryRow(d) {
                    db.deleteDelivery(d.id)
                    onChanged()
                }
            }
        }
    }

    if (addOpen) {
        AddDeliveryDialog(
            neighborhoods = db.allNeighborhoods(),
            integralFee = db.integralFee(),
            onDismiss = { addOpen = false },
            onChoose = { n ->
                if (db.addDelivery(n)) {
                    addOpen = false
                    onChanged()
                }
            }
        )
    }
}

@Composable
private fun SummaryValue(label: String, value: String) {
    Column {
        Text(label, color = Color(0xFF99A2AF), fontSize = 13.sp)
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun DeliveryRow(d: DeliveryEntry, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF191C22)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(d.neighborhoodName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(money(d.feeCents), color = Color(0xFFFFC107), fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = onDelete) { Text("Excluir", color = Color(0xFFFF7B7B)) }
        }
    }
}

@Composable
private fun AddDeliveryDialog(
    neighborhoods: List<Neighborhood>,
    integralFee: Int,
    onDismiss: () -> Unit,
    onChoose: (Neighborhood) -> Unit
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(search, neighborhoods) {
        neighborhoods.filter { it.name.contains(search, ignoreCase = true) }.take(30)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar entrega") },
        text = {
            Column {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Digite o bairro") }
                )
                Spacer(Modifier.height(10.dp))
                LazyColumn(modifier = Modifier.height(360.dp)) {
                    items(filtered, key = { it.id }) { n ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (n.integral && integralFee <= 0) return@clickable
                                onChoose(n)
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(n.name, fontWeight = FontWeight.SemiBold)
                                if (n.integral && integralFee <= 0) {
                                    Text("Taxa integral não configurada", color = Color(0xFFFF7B7B), fontSize = 12.sp)
                                }
                            }
                            Text(
                                if (n.integral) {
                                    if (integralFee > 0) money(integralFee) else "Integral"
                                } else money(n.feeCents),
                                color = Color(0xFFFFC107),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        HorizontalDivider(color = Color(0xFF2D323B))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}

@Composable
private fun CalendarScreen(db: DeliveryDb, refreshTick: Int) {
    var year by remember { mutableIntStateOf(LocalDate.now().year) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    val summaries = remember(refreshTick, year) { db.daySummaries(year) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { year-- }) { Text("‹") }
                Text(
                    year.toString(),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                OutlinedButton(onClick = { year++ }) { Text("›") }
            }
        }
        items((1..12).toList()) { month ->
            MonthCard(year, month, summaries) { selected = it }
        }
    }

    selected?.let { day ->
        val rows = db.deliveriesFor(day)
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(day.format(dateFmt)) },
            text = {
                Column {
                    Text("${rows.size} entrega(s)", fontWeight = FontWeight.Bold)
                    Text("Total: ${money(rows.sumOf { it.feeCents })}", color = Color(0xFFFFC107), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(8.dp))
                    rows.take(12).forEach { Text("• ${it.neighborhoodName} — ${money(it.feeCents)}") }
                    if (rows.size > 12) Text("+ ${rows.size - 12} entrega(s)", color = Color(0xFF9EA6B3))
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Fechar") } }
        )
    }
}

@Composable
private fun MonthCard(
    year: Int,
    month: Int,
    summaries: Map<LocalDate, DaySummary>,
    onDay: (LocalDate) -> Unit
) {
    val ym = YearMonth.of(year, month)
    val monthName = ym.month.getDisplayName(java.time.format.TextStyle.FULL, brLocale)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(brLocale) else it.toString() }
    val firstOffset = ym.atDay(1).dayOfWeek.value - 1
    val totalCells = firstOffset + ym.lengthOfMonth()
    val rows = (totalCells + 6) / 7

    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF191C22)), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(monthName, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                listOf("S", "T", "Q", "Q", "S", "S", "D").forEach {
                    Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = Color(0xFF8E97A5), fontSize = 12.sp)
                }
            }
            repeat(rows) { r ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { c ->
                        val cell = r * 7 + c
                        val dayNum = cell - firstOffset + 1
                        if (dayNum in 1..ym.lengthOfMonth()) {
                            val date = ym.atDay(dayNum)
                            val sum = summaries[date]
                            Box(
                                Modifier.weight(1f).padding(2.dp).clip(RoundedCornerShape(10.dp))
                                    .background(if (sum != null) Color(0xFF3B3315) else Color.Transparent)
                                    .clickable { onDay(date) }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(dayNum.toString(), fontWeight = if (sum != null) FontWeight.Bold else FontWeight.Normal)
                                    if (sum != null) Text(sum.count.toString(), color = Color(0xFFFFC107), fontSize = 10.sp)
                                }
                            }
                        } else {
                            Spacer(Modifier.weight(1f).height(38.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(db: DeliveryDb, refreshTick: Int, onChanged: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val rows = remember(refreshTick, query) { db.history(query) }
    val total = rows.sumOf { it.feeCents }
    val top = rows.groupingBy { it.neighborhoodName }.eachCount().maxByOrNull { it.value }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Histórico", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Pesquisar bairro ou data") },
                singleLine = true
            )
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF191C22))) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("${rows.size} entrega(s)", fontWeight = FontWeight.Bold)
                    Text("Total: ${money(total)}", color = Color(0xFFFFC107), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    if (rows.isNotEmpty()) {
                        Text("Média: ${money(total / rows.size)}", color = Color(0xFFADB4C0))
                        top?.let { Text("Mais frequente: ${it.key} (${it.value})", color = Color(0xFFADB4C0)) }
                    }
                }
            }
        }
        items(rows, key = { it.id }) { d ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF191C22))) {
                Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(d.neighborhoodName, fontWeight = FontWeight.Bold)
                        Text(runCatching { LocalDate.parse(d.date).format(dateFmt) }.getOrDefault(d.date), color = Color(0xFF9EA6B3), fontSize = 12.sp)
                    }
                    Text(money(d.feeCents), color = Color(0xFFFFC107), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = { db.deleteDelivery(d.id); onChanged() }) { Text("×", color = Color(0xFFFF7B7B), fontSize = 20.sp) }
                }
            }
        }
    }
}

@Composable
private fun NeighborhoodsScreen(
    db: DeliveryDb,
    refreshTick: Int,
    onChanged: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    val neighborhoods = remember(refreshTick) { db.allNeighborhoods() }
    var search by remember { mutableStateOf("") }
    var edit by remember { mutableStateOf<Neighborhood?>(null) }
    var addNew by remember { mutableStateOf(false) }
    var integralText by remember(refreshTick) { mutableStateOf(if (db.integralFee() > 0) (db.integralFee() / 100.0).toString().replace('.', ',') else "") }
    val filtered = neighborhoods.filter { it.name.contains(search, true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Bairros e taxas", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text("Edite valores, cadastre bairros e configure a taxa integral.", color = Color(0xFF9EA6B3))
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF191C22))) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("Taxa Integral", fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = integralText,
                            onValueChange = { integralText = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' } },
                            modifier = Modifier.weight(1f),
                            label = { Text("Valor em R$") },
                            singleLine = true
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            val value = integralText.replace(',', '.').toDoubleOrNull() ?: 0.0
                            db.setIntegralFee((value * 100).toInt())
                            onChanged()
                        }) { Text("Salvar") }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { addNew = true }, modifier = Modifier.weight(1f)) { Text("+ BAIRRO") }
                OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) { Text("BACKUP") }
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("RESTAURAR") }
            }
        }
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Pesquisar bairro") },
                singleLine = true
            )
        }
        items(filtered, key = { it.id }) { n ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF191C22)),
                modifier = Modifier.fillMaxWidth().clickable { edit = n }
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(n.name, fontWeight = FontWeight.Bold)
                        if (n.integral) Text("TAXA INTEGRAL", color = Color(0xFFFFC107), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(if (n.integral) "Integral" else money(n.feeCents), fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (addNew) {
        NeighborhoodDialog(
            original = null,
            onDismiss = { addNew = false },
            onSave = { name, fee, integral ->
                if (db.addNeighborhood(name, fee, integral)) {
                    addNew = false
                    onChanged()
                }
            },
            onDelete = null
        )
    }
    edit?.let { n ->
        NeighborhoodDialog(
            original = n,
            onDismiss = { edit = null },
            onSave = { name, fee, integral ->
                db.updateNeighborhood(n.id, name, fee, integral)
                edit = null
                onChanged()
            },
            onDelete = {
                db.deleteNeighborhood(n.id)
                edit = null
                onChanged()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NeighborhoodDialog(
    original: Neighborhood?,
    onDismiss: () -> Unit,
    onSave: (String, Int, Boolean) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember { mutableStateOf(original?.name ?: "") }
    var fee by remember { mutableStateOf(original?.let { (it.feeCents / 100.0).toString().replace('.', ',') } ?: "") }
    var integral by remember { mutableStateOf(original?.integral ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original == null) "Novo bairro" else "Editar bairro") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Bairro") }, singleLine = true)
                OutlinedTextField(
                    value = fee,
                    onValueChange = { fee = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' } },
                    label = { Text("Taxa em R$") },
                    enabled = !integral,
                    singleLine = true
                )
                OutlinedButton(onClick = { integral = !integral }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (integral) "✓ TAXA INTEGRAL" else "MARCAR COMO TAXA INTEGRAL")
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("EXCLUIR BAIRRO", color = Color(0xFFFF7B7B)) }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cents = if (integral) 0 else ((fee.replace(',', '.').toDoubleOrNull() ?: 0.0) * 100).toInt()
                    if (name.isNotBlank()) onSave(name.trim(), cents, integral)
                }
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
