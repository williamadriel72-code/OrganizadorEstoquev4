package com.stockmaster.clone.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


data class UserSession(
    val id: String,
    val name: String,
    val login: String,
    val permissionsJson: String
) {
    fun can(permission: String): Boolean =
        permissionsJson.replace(" ", "").contains("\"$permission\":true", ignoreCase = true)
}

data class ProductRow(
    val id: String,
    val companyId: Int,
    val ean: String,
    val description: String,
    val groupId: String,
    val sectorId: Int?,
    val price: Double,
    val cashPrice: Double,
    val stock: Double?
)

data class ExpiryRow(
    val id: Long,
    val productId: String,
    val description: String,
    val expiry: String,
    val lot: String,
    val quantity: Double
)

class StockMasterDb(private val context: Context) {
    private val dbFile get() = context.getDatabasePath("aws_estoque_local.db")
    private var db: SQLiteDatabase? = null

    fun hasDatabase(): Boolean = dbFile.exists() && dbFile.length() > 0

    fun importDatabase(uri: Uri) {
        close()
        java.io.File(dbFile.absolutePath + "-wal").delete()
        java.io.File(dbFile.absolutePath + "-shm").delete()
        java.io.File(dbFile.absolutePath + "-journal").delete()
        dbFile.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Não foi possível abrir o arquivo selecionado." }
            dbFile.outputStream().use { output -> input.copyTo(output) }
        }
        open()
        validateSchema()
    }

    fun open(): SQLiteDatabase {
        db?.let { if (it.isOpen) return it }
        check(hasDatabase()) { "Importe o arquivo .db primeiro." }
        return SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).also { database ->
            db = database
            runCatching { ensureSearchIndexes(database) }
        }
    }

    fun close() {
        db?.close()
        db = null
    }

    private fun ensureSearchIndexes(database: SQLiteDatabase) {
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_aws_produto_ean ON produto(ean)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_aws_produto_descricao ON produto(descricaoproduto COLLATE NOCASE)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_aws_produto_ativo_descricao ON produto(ativo, descricaoproduto COLLATE NOCASE)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_aws_validade_produto_data ON controlevalidade(idproduto, validade)")
    }

    fun validateSchema() {
        val database = open()
        val required = setOf("produto", "usuario", "ivitens", "conferencia", "avitens", "impressao", "controlevalidade")
        val found = mutableSetOf<String>()
        database.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c ->
            while (c.moveToNext()) found += c.getString(0)
        }
        val missing = required - found
        require(missing.isEmpty()) { "Banco incompatível. Tabelas ausentes: ${missing.joinToString()}" }
        ensureSearchIndexes(database)
    }

    fun authenticate(login: String, password: String): UserSession? {
        val database = open()
        val cleanLogin = login.trim()
        val cleanPassword = password.trim()
        database.rawQuery(
            """
            SELECT idusr,nome,login,COALESCE(acessosUser,'{}')
            FROM usuario
            WHERE (
                UPPER(TRIM(COALESCE(login,''))) = UPPER(?)
                OR UPPER(TRIM(COALESCE(nome,''))) = UPPER(?)
                OR TRIM(CAST(idusr AS TEXT)) = ?
            )
              AND TRIM(COALESCE(senha,'')) = ?
              AND UPPER(TRIM(COALESCE(ativo,''))) = 'S'
            LIMIT 1
            """.trimIndent(),
            arrayOf(cleanLogin, cleanLogin, cleanLogin, cleanPassword)
        ).use { c ->
            return if (c.moveToFirst()) {
                UserSession(c.getString(0), c.getString(1), c.getString(2), c.getString(3))
            } else null
        }
    }

    fun searchProducts(query: String, limit: Int = 100): List<ProductRow> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val database = open()
        val out = LinkedHashMap<String, ProductRow>()

        fun collect(sql: String, args: Array<String>) {
            if (out.size >= limit) return
            database.rawQuery(sql, args).use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val row = productFromCursor(c)
                    out.putIfAbsent(row.id, row)
                }
            }
        }

        if (q.all { it.isDigit() }) {
            val prefix = "$q%"
            collect(
                """
                SELECT CAST(id AS TEXT), idempresa, COALESCE(ean,''), COALESCE(descricaoproduto,''),
                       CAST(COALESCE(idgrupo,'') AS TEXT), idsetor,
                       COALESCE(precogondola,0), COALESCE(precoavista,0), qtestoque
                FROM produto
                WHERE COALESCE(ativo,'S')='S'
                  AND (CAST(id AS TEXT) LIKE ? OR COALESCE(ean,'') LIKE ?)
                ORDER BY descricaoproduto COLLATE NOCASE
                LIMIT ?
                """.trimIndent(),
                arrayOf(prefix, prefix, limit.toString())
            )
        }

        val prefixText = "$q%"
        collect(
            """
            SELECT CAST(id AS TEXT), idempresa, COALESCE(ean,''), COALESCE(descricaoproduto,''),
                   CAST(COALESCE(idgrupo,'') AS TEXT), idsetor,
                   COALESCE(precogondola,0), COALESCE(precoavista,0), qtestoque
            FROM produto
            WHERE COALESCE(ativo,'S')='S'
              AND COALESCE(descricaoproduto,'') LIKE ? COLLATE NOCASE
            ORDER BY descricaoproduto COLLATE NOCASE
            LIMIT ?
            """.trimIndent(),
            arrayOf(prefixText, limit.toString())
        )

        if (out.size < limit) {
            val contains = "%$q%"
            collect(
                """
                SELECT CAST(id AS TEXT), idempresa, COALESCE(ean,''), COALESCE(descricaoproduto,''),
                       CAST(COALESCE(idgrupo,'') AS TEXT), idsetor,
                       COALESCE(precogondola,0), COALESCE(precoavista,0), qtestoque
                FROM produto
                WHERE COALESCE(ativo,'S')='S'
                  AND COALESCE(descricaoproduto,'') LIKE ? COLLATE NOCASE
                ORDER BY descricaoproduto COLLATE NOCASE
                LIMIT ?
                """.trimIndent(),
                arrayOf(contains, limit.toString())
            )
        }

        return out.values.toList()
    }

    fun findExact(codeOrEan: String): ProductRow? {
        val q = codeOrEan.trim()
        if (q.isEmpty()) return null
        val database = open()
        database.rawQuery(
            """
            SELECT CAST(id AS TEXT), idempresa, COALESCE(ean,''), COALESCE(descricaoproduto,''),
                   CAST(COALESCE(idgrupo,'') AS TEXT), idsetor,
                   COALESCE(precogondola,0), COALESCE(precoavista,0), qtestoque
            FROM produto
            WHERE COALESCE(ativo,'S')='S' AND (CAST(id AS TEXT)=? OR TRIM(COALESCE(ean,''))=?)
            LIMIT 1
            """.trimIndent(),
            arrayOf(q, q)
        ).use { c ->
            if (!c.moveToFirst()) return null
            return productFromCursor(c)
        }
    }

    private fun productFromCursor(c: android.database.Cursor): ProductRow = ProductRow(
        id = c.getString(0),
        companyId = c.getInt(1),
        ean = c.getString(2),
        description = c.getString(3),
        groupId = c.getString(4),
        sectorId = if (c.isNull(5)) null else c.getInt(5),
        price = c.getDouble(6),
        cashPrice = c.getDouble(7),
        stock = if (c.isNull(8)) null else c.getDouble(8)
    )

    private fun nowParts(): Triple<String, String, String> {
        val now = Date()
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(now)
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(now)
        return Triple(date, time, iso)
    }

    fun addInventory(product: ProductRow, qty: Double, user: UserSession) {
        val (date, time) = nowParts()
        val values = android.content.ContentValues().apply {
            put("idempresa", product.companyId); put("idproduto", product.id); put("ean", product.ean)
            put("descricaoproduto", product.description); put("status", "P"); put("preco", product.price)
            put("data", date); put("hora", time); put("qtestoque", qty); put("idusuario", user.id.toIntOrNull())
        }
        open().insertOrThrow("ivitens", null, values)
    }

    fun addConference(product: ProductRow, qty: Double, user: UserSession) {
        val date = nowParts().first
        val values = android.content.ContentValues().apply {
            put("idproduto", product.id); put("idempresa", product.companyId); put("ean", product.ean)
            put("data", date); put("status", "P"); put("qtde", qty); put("tipo", "PRODUTO")
            put("idusr", user.id.toIntOrNull())
        }
        open().insertOrThrow("conferencia", null, values)
    }

    fun addDamage(product: ProductRow, qty: Double, user: UserSession) {
        val (date, time) = nowParts()
        val values = android.content.ContentValues().apply {
            put("idempresa", product.companyId); put("idproduto", product.id); put("ean", product.ean)
            put("descricaoproduto", product.description); put("status", "P"); put("preco", product.price)
            put("data", date); put("hora", time); put("qtde", qty); put("idusuario", user.id.toIntOrNull()); put("vencido", 0)
        }
        open().insertOrThrow("avitens", null, values)
    }

    fun addPrint(product: ProductRow, qty: Int) {
        val values = android.content.ContentValues().apply {
            put("idempresa", product.companyId); put("idproduto", product.id.toIntOrNull())
            put("descricaoproduto", product.description); put("preco", product.price); put("status", "P"); put("qtde", qty)
        }
        open().insertOrThrow("impressao", null, values)
    }

    fun addExpiry(product: ProductRow, expiry: String, lot: String, qty: Double, user: UserSession) {
        val (_, _, iso) = nowParts()
        val values = android.content.ContentValues().apply {
            put("idproduto", product.id); put("idempresa", product.companyId); put("dataemissao", iso.take(10))
            put("lote", lot); put("validade", expiry); put("status", "P"); put("qtdecoletada", qty)
            put("datahoraemissao", iso); put("datahoracoletado", iso); put("idusr", user.id.toIntOrNull())
        }
        open().insertOrThrow("controlevalidade", null, values)
    }

    fun listExpiry(limit: Int = 200): List<ExpiryRow> {
        val out = ArrayList<ExpiryRow>()
        open().rawQuery(
            """
            SELECT cv.id, cv.idproduto, COALESCE(p.descricaoproduto,''), COALESCE(cv.validade,''),
                   COALESCE(cv.lote,''), COALESCE(cv.qtdecoletada,0)
            FROM controlevalidade cv
            LEFT JOIN produto p ON CAST(p.id AS TEXT)=cv.idproduto
            ORDER BY cv.validade ASC
            LIMIT ?
            """.trimIndent(), arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) out += ExpiryRow(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getDouble(5))
        }
        return out
    }

    fun addMovement(product: ProductRow, qty: Double, user: UserSession, observation: String) {
        val (_, _, iso) = nowParts()
        val database = open()
        database.beginTransaction()
        try {
            val header = android.content.ContentValues().apply {
                put("idsolicitante", user.id.toIntOrNull()); put("nomesolicitante", user.name)
                put("idempresaorigem", product.companyId); put("nomeempresaorigem", "EMPRESA ${product.companyId}")
                put("idempresadestino", product.companyId); put("nomeempresadestino", "EMPRESA ${product.companyId}")
                put("idsetor", product.sectorId); put("nomesetor", ""); put("total", qty)
                put("observacao", observation); put("datacriado", iso)
            }
            val movementId = database.insertOrThrow("movimentacao", null, header)
            val (date, time) = nowParts()
            val item = android.content.ContentValues().apply {
                put("idmovimentacao", movementId); put("idempresa", product.companyId); put("idproduto", product.id)
                put("ean", product.ean); put("descricaoproduto", product.description); put("status", "P")
                put("preco", product.price); put("data", date); put("hora", time); put("qtde", qty); put("idusuario", user.id.toIntOrNull())
            }
            database.insertOrThrow("itemmovimentacao", null, item)
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }
}
