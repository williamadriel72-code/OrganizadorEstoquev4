package com.aws.gestaoestoque.data

import java.text.SimpleDateFormat
import java.util.Locale

/** Retorna as validades de uma mercadoria em ordem FEFO: mais próxima primeiro. */
fun AwsDb.expiryForProduct(productId: String, limit: Int = 100): List<ExpiryRow> {
    val rows = ArrayList<ExpiryRow>()
    open().rawQuery(
        """
        SELECT cv.id,
               CAST(cv.idproduto AS TEXT),
               COALESCE(p.descricaoproduto,''),
               TRIM(COALESCE(cv.validade,'')),
               COALESCE(cv.lote,''),
               COALESCE(cv.qtdecoletada,0)
        FROM controlevalidade cv
        LEFT JOIN produto p ON CAST(p.id AS TEXT)=CAST(cv.idproduto AS TEXT)
        WHERE CAST(cv.idproduto AS TEXT)=?
          AND TRIM(COALESCE(cv.validade,''))<>''
        LIMIT ?
        """.trimIndent(),
        arrayOf(productId.trim(), limit.toString())
    ).use { c ->
        while (c.moveToNext()) {
            rows += ExpiryRow(
                id = c.getLong(0),
                productId = c.getString(1),
                description = c.getString(2),
                expiry = c.getString(3),
                lot = c.getString(4),
                quantity = c.getDouble(5)
            )
        }
    }

    return rows.sortedWith(
        compareBy<ExpiryRow> { parseExpiryMillis(it.expiry) ?: Long.MAX_VALUE }
            .thenBy { it.expiry }
    )
}

/** Converte a validade para DD/MM/AAAA quando estiver no formato ISO. */
fun formatExpiryForDisplay(value: String): String {
    val clean = value.trim()
    val iso = Regex("""(\d{4})-(\d{2})-(\d{2}).*""").matchEntire(clean)
    return if (iso != null) {
        "${iso.groupValues[3]}/${iso.groupValues[2]}/${iso.groupValues[1]}"
    } else {
        clean
    }
}

private fun parseExpiryMillis(value: String): Long? {
    val clean = value.trim()
    val patterns = listOf("yyyy-MM-dd", "dd/MM/yyyy", "yyyy-MM-dd'T'HH:mm:ss")
    for (pattern in patterns) {
        val parsed = runCatching {
            SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(clean)?.time
        }.getOrNull()
        if (parsed != null) return parsed
    }
    return null
}
