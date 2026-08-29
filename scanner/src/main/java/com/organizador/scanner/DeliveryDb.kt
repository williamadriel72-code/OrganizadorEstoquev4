package com.organizador.scanner

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate


data class Neighborhood(
    val id: Long,
    val name: String,
    val feeCents: Int,
    val integral: Boolean
)

data class Delivery(
    val id: Long,
    val date: String,
    val neighborhood: String,
    val feeCents: Int,
    val createdAt: Long
)

data class Summary(val count: Int, val totalCents: Int)

data class NeighborhoodStat(val name: String, val count: Int, val totalCents: Int)

class DeliveryDb(context: Context) : SQLiteOpenHelper(context, "bora_maycon.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE settings(
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE neighborhoods(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE COLLATE NOCASE,
                fee_cents INTEGER NOT NULL DEFAULT 0,
                integral INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE deliveries(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT NOT NULL,
                neighborhood TEXT NOT NULL,
                fee_cents INTEGER NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_deliveries_date ON deliveries(date)")
        db.execSQL("CREATE INDEX idx_deliveries_neighborhood ON deliveries(neighborhood)")
        db.insert("settings", null, ContentValues().apply {
            put("key", "integral_fee_cents")
            put("value", "0")
        })
        seedNeighborhoods(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    private fun seedNeighborhoods(db: SQLiteDatabase) {
        val fixed = listOf(
            "Águas Maravilhosas" to 800,
            "Ajuda de Cima" to 700,
            "Ajuda de Baixo" to 600,
            "Aroeira" to 300,
            "Atlântico Norte" to 700,
            "Barra" to 400,
            "Barreto" to 800,
            "Barramares" to 700,
            "Bela Vista" to 400,
            "Bosque Azul" to 700,
            "Botafogo" to 500,
            "Brasília" to 400,
            "Brisa do Vale" to 800,
            "Cabiúnas" to 1500,
            "Cajueiros" to 300,
            "Campo D'Oeste" to 400,
            "Cancela Preta" to 700,
            "Cavaleiros" to 700,
            "Centro" to 300,
            "Costa do Sol" to 400,
            "Engenho da Praia" to 1300,
            "Franco Plaza" to 700,
            "Fronteira" to 400,
            "Glória" to 800,
            "Granja dos Cavaleiros" to 1000,
            "Horto" to 1500,
            "Imbetiba" to 300,
            "Ilha Leocádia" to 1000,
            "Jardim Carioca 1" to 600,
            "Jardim Esperança" to 500,
            "Jardim Carioca 2" to 700,
            "Jardim Guanabara" to 1200,
            "Jardim Maringá" to 500,
            "Jardim Santo Antônio" to 500,
            "Jardim Vitória" to 500,
            "Jardim Franco" to 700,
            "Lagoa" to 1000,
            "Lagomar" to 1300,
            "Maracaibo/Monza" to 700,
            "Marville" to 700,
            "Malvinas" to 500,
            "Miramar" to 300,
            "Mirante da Lagoa" to 1200,
            "Morro de Santa Mônica" to 500,
            "Nova Esperança" to 700,
            "Nova Holanda" to 700,
            "Nova Macaé" to 500,
            "Novo Cavaleiros" to 1000,
            "Novo Horizonte" to 500,
            "Parque Aeroporto" to 700,
            "Parque Atlântico" to 700,
            "Parque União" to 700,
            "Praia do Pecado" to 900,
            "Piracema" to 800,
            "Planalto da Ajuda" to 700,
            "Praia Campista" to 500,
            "Riviera" to 500,
            "São Marcos" to 1100,
            "Sol e Mar" to 500,
            "Santa Mônica" to 500,
            "Vale das Palmeiras" to 1500,
            "Vale dos Cristais" to 1500,
            "Verdes Mares" to 700,
            "Vila Badejo" to 500,
            "Vila Moreira" to 1200,
            "Vill. do Horto" to 1500,
            "Virgem Santa" to 700,
            "Visconde" to 300,
            "Itaparica" to 500
        )
        fixed.forEach { (name, fee) ->
            db.insert("neighborhoods", null, ContentValues().apply {
                put("name", name)
                put("fee_cents", fee)
                put("integral", 0)
            })
        }
        listOf(
            "Imburo",
            "Vale Encantado",
            "Quinta da Boa Vista",
            "Fazenda depois da Virgem Santa",
            "Virgem Santa depois do posto de saúde"
        ).forEach { name ->
            db.insert("neighborhoods", null, ContentValues().apply {
                put("name", name)
                put("fee_cents", 0)
                put("integral", 1)
            })
        }
    }

    fun getIntegralFeeCents(): Int {
        readableDatabase.rawQuery(
            "SELECT value FROM settings WHERE key='integral_fee_cents'",
            null
        ).use { c ->
            return if (c.moveToFirst()) c.getString(0).toIntOrNull() ?: 0 else 0
        }
    }

    fun setIntegralFeeCents(value: Int) {
        writableDatabase.insertWithOnConflict(
            "settings",
            null,
            ContentValues().apply {
                put("key", "integral_fee_cents")
                put("value", value.coerceAtLeast(0).toString())
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun listNeighborhoods(search: String = ""): List<Neighborhood> {
        val query = search.trim()
        val sql: String
        val args: Array<String>?
        if (query.isBlank()) {
            sql = "SELECT id,name,fee_cents,integral FROM neighborhoods ORDER BY name COLLATE NOCASE"
            args = null
        } else {
            sql = "SELECT id,name,fee_cents,integral FROM neighborhoods WHERE name LIKE ? ORDER BY name COLLATE NOCASE"
            args = arrayOf("%$query%")
        }
        return readableDatabase.rawQuery(sql, args).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Neighborhood(c.getLong(0), c.getString(1), c.getInt(2), c.getInt(3) == 1))
                }
            }
        }
    }

    fun saveNeighborhood(id: Long?, name: String, feeCents: Int, integral: Boolean): Long {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Informe o nome do bairro." }
        val values = ContentValues().apply {
            put("name", clean)
            put("fee_cents", feeCents.coerceAtLeast(0))
            put("integral", if (integral) 1 else 0)
        }
        return if (id == null) {
            writableDatabase.insertOrThrow("neighborhoods", null, values)
        } else {
            writableDatabase.update("neighborhoods", values, "id=?", arrayOf(id.toString()))
            id
        }
    }

    fun deleteNeighborhood(id: Long) {
        writableDatabase.delete("neighborhoods", "id=?", arrayOf(id.toString()))
    }

    private fun effectiveFee(n: Neighborhood): Int {
        if (!n.integral) return n.feeCents
        val integral = getIntegralFeeCents()
        require(integral > 0) { "Defina primeiro o valor da Taxa Integral em Bairros e Taxas." }
        return integral
    }

    fun addDelivery(date: String, n: Neighborhood): Long {
        val fee = effectiveFee(n)
        return writableDatabase.insertOrThrow("deliveries", null, ContentValues().apply {
            put("date", date)
            put("neighborhood", n.name)
            put("fee_cents", fee)
            put("created_at", System.currentTimeMillis())
        })
    }

    fun updateDelivery(id: Long, n: Neighborhood) {
        val fee = effectiveFee(n)
        writableDatabase.update(
            "deliveries",
            ContentValues().apply {
                put("neighborhood", n.name)
                put("fee_cents", fee)
            },
            "id=?",
            arrayOf(id.toString())
        )
    }

    fun deleteDelivery(id: Long) {
        writableDatabase.delete("deliveries", "id=?", arrayOf(id.toString()))
    }

    fun deliveriesForDate(date: String): List<Delivery> =
        deliveriesBetween(date, date)

    fun deliveriesBetween(start: String, end: String): List<Delivery> {
        return readableDatabase.rawQuery(
            "SELECT id,date,neighborhood,fee_cents,created_at FROM deliveries WHERE date BETWEEN ? AND ? ORDER BY date DESC, created_at DESC",
            arrayOf(start, end)
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Delivery(c.getLong(0), c.getString(1), c.getString(2), c.getInt(3), c.getLong(4)))
                }
            }
        }
    }

    fun allDeliveries(): List<Delivery> {
        return readableDatabase.rawQuery(
            "SELECT id,date,neighborhood,fee_cents,created_at FROM deliveries ORDER BY date DESC, created_at DESC",
            null
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    Delivery(c.getLong(0), c.getString(1), c.getString(2), c.getInt(3), c.getLong(4))
                )
            }
        }
    }

    fun summaryBetween(start: String, end: String): Summary {
        readableDatabase.rawQuery(
            "SELECT COUNT(*), COALESCE(SUM(fee_cents),0) FROM deliveries WHERE date BETWEEN ? AND ?",
            arrayOf(start, end)
        ).use { c ->
            c.moveToFirst()
            return Summary(c.getInt(0), c.getInt(1))
        }
    }

    fun dailySummaries(year: Int): Map<String, Summary> {
        val start = LocalDate.of(year, 1, 1).toString()
        val end = LocalDate.of(year, 12, 31).toString()
        return readableDatabase.rawQuery(
            "SELECT date, COUNT(*), COALESCE(SUM(fee_cents),0) FROM deliveries WHERE date BETWEEN ? AND ? GROUP BY date ORDER BY date",
            arrayOf(start, end)
        ).use { c ->
            buildMap {
                while (c.moveToNext()) put(c.getString(0), Summary(c.getInt(1), c.getInt(2)))
            }
        }
    }

    fun neighborhoodStats(start: String, end: String): List<NeighborhoodStat> {
        return readableDatabase.rawQuery(
            "SELECT neighborhood, COUNT(*), COALESCE(SUM(fee_cents),0) FROM deliveries WHERE date BETWEEN ? AND ? GROUP BY neighborhood ORDER BY COUNT(*) DESC, neighborhood COLLATE NOCASE",
            arrayOf(start, end)
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(NeighborhoodStat(c.getString(0), c.getInt(1), c.getInt(2)))
            }
        }
    }

    fun exportJson(): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("integral_fee_cents", getIntegralFeeCents())
        val neighborhoods = JSONArray()
        listNeighborhoods().forEach { n ->
            neighborhoods.put(JSONObject().apply {
                put("name", n.name)
                put("fee_cents", n.feeCents)
                put("integral", n.integral)
            })
        }
        root.put("neighborhoods", neighborhoods)
        val deliveries = JSONArray()
        allDeliveries().forEach { d ->
            deliveries.put(JSONObject().apply {
                put("date", d.date)
                put("neighborhood", d.neighborhood)
                put("fee_cents", d.feeCents)
                put("created_at", d.createdAt)
            })
        }
        root.put("deliveries", deliveries)
        return root.toString(2)
    }

    fun importJson(text: String) {
        val root = JSONObject(text)
        val neighborhoods = root.getJSONArray("neighborhoods")
        val deliveries = root.getJSONArray("deliveries")
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("deliveries", null, null)
            db.delete("neighborhoods", null, null)
            setIntegralFeeCents(root.optInt("integral_fee_cents", 0))
            for (i in 0 until neighborhoods.length()) {
                val o = neighborhoods.getJSONObject(i)
                db.insertOrThrow("neighborhoods", null, ContentValues().apply {
                    put("name", o.getString("name"))
                    put("fee_cents", o.optInt("fee_cents", 0))
                    put("integral", if (o.optBoolean("integral", false)) 1 else 0)
                })
            }
            for (i in 0 until deliveries.length()) {
                val o = deliveries.getJSONObject(i)
                db.insertOrThrow("deliveries", null, ContentValues().apply {
                    put("date", o.getString("date"))
                    put("neighborhood", o.getString("neighborhood"))
                    put("fee_cents", o.getInt("fee_cents"))
                    put("created_at", o.optLong("created_at", System.currentTimeMillis()))
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
