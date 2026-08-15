package com.stockmaster.clone.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExpiryPdfImportReport(
    val found: Int,
    val inserted: Int,
    val updated: Int,
    val ignored: Int,
    val errors: Int
)

private data class ImportedExpiry(
    val productCode: Int,
    val ean: String,
    val description: String,
    val quantity: Double,
    val expiryIso: String
)

class ExpiryPdfImporter(
    private val context: Context,
    private val db: StockMasterDb
) {
    fun import(uri: Uri, user: UserSession): ExpiryPdfImportReport {
        PDFBoxResourceLoader.init(context.applicationContext)
        val temp = File.createTempFile("aws_validades_", ".pdf", context.cacheDir)

        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Não foi possível abrir o PDF selecionado." }
                temp.outputStream().use { output -> input.copyTo(output) }
            }

            val text = PDDocument.load(temp).use { document ->
                require(document.numberOfPages > 0) { "O PDF está vazio." }
                PDFTextStripper().apply { sortByPosition = true }.getText(document)
            }

            require(text.isNotBlank()) {
                "O PDF não possui texto pesquisável. Use um relatório PDF gerado pelo sistema."
            }

            val parsed = parseReport(text)
            require(parsed.rows.isNotEmpty()) {
                "Nenhuma validade foi reconhecida. O PDF precisa conter Código, EAN, Descrição, Estoque Atual e Data Validade."
            }

            return applyToDatabase(parsed.rows, parsed.errors, user)
        } finally {
            temp.delete()
        }
    }

    private data class ParseResult(
        val rows: List<ImportedExpiry>,
        val errors: Int
    )

    private data class Pending(
        val code: Int,
        val ean: String,
        val descriptionParts: MutableList<String>
    )

    private fun parseReport(rawText: String): ParseResult {
        val rows = ArrayList<ImportedExpiry>()
        var pending: Pending? = null
        var errors = 0
        var currentGroupedExpiry: String? = null

        fun addPending(quantity: Double, dateText: String) {
            val item = pending ?: return
            val expiry = toIsoDate(dateText)
            val description = item.descriptionParts
                .joinToString(" ")
                .replace(Regex("\\s+"), " ")
                .trim()

            if (expiry == null || description.isBlank()) {
                errors++
            } else {
                rows += ImportedExpiry(
                    productCode = item.code,
                    ean = item.ean,
                    description = description,
                    quantity = quantity,
                    expiryIso = expiry
                )
            }
            pending = null
        }

        rawText.lineSequence().forEach { original ->
            var line = cleanLine(original)
            if (line.isBlank()) return@forEach

            groupedExpiryRegex.find(line)?.let { match ->
                currentGroupedExpiry = match.groupValues[1]
                if (line.trim().startsWith("Data Validade", ignoreCase = true)) return@forEach
            }

            if (isNoise(line)) return@forEach

            val productMatch = productStartRegex.matchEntire(line)
            if (productMatch != null) {
                if (pending != null) {
                    errors++
                    pending = null
                }

                val code = productMatch.groupValues[1].toIntOrNull()
                val ean = productMatch.groupValues[2].trim()
                val remainder = productMatch.groupValues[3].trim()
                if (code == null || ean.isBlank() || remainder.isBlank()) {
                    errors++
                    return@forEach
                }

                val fullTail = qtyDateAtEndRegex.matchEntire(remainder)
                if (fullTail != null) {
                    val description = fullTail.groupValues[1]
                        .replace(Regex("\\s+"), " ")
                        .trim()
                    val quantity = parsePtBrNumber(fullTail.groupValues[2])
                    val expiry = toIsoDate(fullTail.groupValues[3])
                    if (description.isBlank() || quantity == null || expiry == null) {
                        errors++
                    } else {
                        rows += ImportedExpiry(code, ean, description, quantity, expiry)
                    }
                } else {
                    pending = Pending(code, ean, mutableListOf(remainder))
                }
                return@forEach
            }

            pending?.let { item ->
                val qtyDate = standaloneQtyDateRegex.matchEntire(line)
                if (qtyDate != null) {
                    val quantity = parsePtBrNumber(qtyDate.groupValues[1])
                    val expiryText = qtyDate.groupValues[2].ifBlank { currentGroupedExpiry.orEmpty() }
                    if (quantity == null || expiryText.isBlank()) {
                        errors++
                        pending = null
                    } else {
                        addPending(quantity, expiryText)
                    }
                } else if (!isNoise(line)) {
                    item.descriptionParts += line
                }
            }
        }

        if (pending != null) errors++
        return ParseResult(rows, errors)
    }

    private fun cleanLine(input: String): String {
        var line = input
            .replace('\u00A0', ' ')
            .replace(Regex("[\\t ]+"), " ")
            .trim()

        line = damFooterRegex.replace(line, "").trim()
        return line
    }

    private fun isNoise(line: String): Boolean {
        val normalized = line.uppercase(Locale.ROOT)
        return normalized.startsWith("GRUPO :") ||
            normalized.startsWith("CÓDIGO EAN DESCRIÇÃO ESTOQUE ATUAL DATA VALIDADE") ||
            normalized.startsWith("CODIGO EAN DESCRICAO ESTOQUE ATUAL DATA VALIDADE") ||
            normalized.startsWith("QUANTIDADE DE ITENS AGRUPADOS") ||
            normalized.matches(Regex("\\d+ DE \\d+"))
    }

    private fun applyToDatabase(
        rows: List<ImportedExpiry>,
        parseErrors: Int,
        user: UserSession
    ): ExpiryPdfImportReport {
        val database = db.open()
        runCatching {
            database.execSQL("CREATE INDEX IF NOT EXISTS idx_aws_validade_produto_data ON controlevalidade(idproduto, validade)")
            database.execSQL("CREATE INDEX IF NOT EXISTS idx_aws_produto_ean ON produto(ean)")
        }

        var inserted = 0
        var updated = 0
        var ignored = 0
        var errors = parseErrors
        val seen = HashSet<String>()
        val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())

        database.beginTransaction()
        try {
            rows.forEach { row ->
                val dedupKey = "${row.productCode}|${row.ean}|${row.expiryIso}"
                if (!seen.add(dedupKey)) {
                    ignored++
                    return@forEach
                }

                try {
                    val product = findProduct(database, row)
                    if (product == null) {
                        ignored++
                        return@forEach
                    }

                    val productId = product.first
                    val companyId = product.second
                    val existingId = findExistingExpiry(database, productId, row.expiryIso)

                    if (existingId != null) {
                        val values = ContentValues().apply {
                            put("qtdecoletada", row.quantity)
                            put("status", "P")
                            put("datahoracoletado", nowIso)
                            put("idusr", user.id.toIntOrNull())
                        }
                        val changed = database.update(
                            "controlevalidade",
                            values,
                            "id=?",
                            arrayOf(existingId.toString())
                        )
                        if (changed > 0) updated++ else errors++
                    } else {
                        val values = ContentValues().apply {
                            put("idproduto", productId.toString())
                            put("idempresa", companyId)
                            put("dataemissao", nowIso.take(10))
                            put("lote", "")
                            put("validade", row.expiryIso)
                            put("status", "P")
                            put("qtdecoletada", row.quantity)
                            put("datahoraemissao", nowIso)
                            put("datahoracoletado", nowIso)
                            put("idusr", user.id.toIntOrNull())
                        }
                        val id = database.insert("controlevalidade", null, values)
                        if (id == -1L) errors++ else inserted++
                    }
                } catch (_: Exception) {
                    errors++
                }
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }

        return ExpiryPdfImportReport(
            found = rows.size,
            inserted = inserted,
            updated = updated,
            ignored = ignored,
            errors = errors
        )
    }

    private fun findProduct(
        database: android.database.sqlite.SQLiteDatabase,
        row: ImportedExpiry
    ): Pair<Int, Int>? {
        database.rawQuery(
            "SELECT id, idempresa FROM produto WHERE id=? LIMIT 1",
            arrayOf(row.productCode.toString())
        ).use { c ->
            if (c.moveToFirst()) return c.getInt(0) to c.getInt(1)
        }

        database.rawQuery(
            "SELECT id, idempresa FROM produto WHERE TRIM(COALESCE(ean,''))=? LIMIT 1",
            arrayOf(row.ean.trim())
        ).use { c ->
            if (c.moveToFirst()) return c.getInt(0) to c.getInt(1)
        }
        return null
    }

    private fun findExistingExpiry(
        database: android.database.sqlite.SQLiteDatabase,
        productId: Int,
        expiryIso: String
    ): Long? {
        database.rawQuery(
            """
            SELECT id FROM controlevalidade
            WHERE CAST(idproduto AS TEXT)=?
              AND TRIM(COALESCE(validade,''))=?
              AND TRIM(COALESCE(lote,''))=''
            LIMIT 1
            """.trimIndent(),
            arrayOf(productId.toString(), expiryIso)
        ).use { c ->
            if (c.moveToFirst()) return c.getLong(0)
        }
        return null
    }

    private fun parsePtBrNumber(value: String): Double? = value
        .replace(".", "")
        .replace(",", ".")
        .trim()
        .toDoubleOrNull()

    private fun toIsoDate(value: String): String? {
        val match = dateRegex.matchEntire(value.trim()) ?: return null
        val day = match.groupValues[1].padStart(2, '0')
        val month = match.groupValues[2].padStart(2, '0')
        val year = match.groupValues[3]
        return "$year-$month-$day"
    }

    companion object {
        private val dateRegex = Regex("""(\d{1,2})/(\d{1,2})/(\d{4})""")
        private val groupedExpiryRegex = Regex("""Data\s+Validade\s+(\d{1,2}/\d{1,2}/\d{4})""", RegexOption.IGNORE_CASE)
        private val productStartRegex = Regex("""^(\d{1,9})\s+([0-9]{8,20})\s+(.+)$""")
        private val qtyDateAtEndRegex = Regex("""^(.*?)\s+(-?\d[\d.]*,\d{2,4})\s+(\d{1,2}/\d{1,2}/\d{4})$""")
        private val standaloneQtyDateRegex = Regex("""^(-?\d[\d.]*,\d{2,4})(?:\s+(\d{1,2}/\d{1,2}/\d{4}))?$""")
        private val damFooterRegex = Regex(
            """D\.A\.M\s+Soluções\s+Data\s+\d{2}\.\d{2}\.\d{4}\s+\d{2}:\d{2}:\d{2}\s+Versão:.*?\d+\s+De\s+\d+""",
            RegexOption.IGNORE_CASE
        )
    }
}
