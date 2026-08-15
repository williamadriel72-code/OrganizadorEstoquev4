package com.aws.gestaoestoque.data

private const val AWS_NOTIFICATION_DAYS = 30

/** Quantidade de alertas de validade vencidos, vencendo hoje ou nos próximos 30 dias. */
fun AwsDb.expiryNotificationCount(daysAhead: Int = AWS_NOTIFICATION_DAYS): Int {
    val safeDays = daysAhead.coerceIn(0, 3650)
    val sql = """
        SELECT COUNT(*)
        FROM controlevalidade cv
        WHERE TRIM(COALESCE(cv.validade,'')) <> ''
          AND UPPER(TRIM(COALESCE(cv.status,'P'))) NOT IN ('C','CANCELADO','FINALIZADO','F')
          AND date(
              CASE
                  WHEN TRIM(cv.validade) LIKE '____-__-__%' THEN substr(TRIM(cv.validade),1,10)
                  WHEN TRIM(cv.validade) LIKE '__/__/____%' THEN
                      substr(TRIM(cv.validade),7,4) || '-' ||
                      substr(TRIM(cv.validade),4,2) || '-' ||
                      substr(TRIM(cv.validade),1,2)
                  ELSE NULL
              END
          ) <= date('now','localtime','+$safeDays day')
    """.trimIndent()

    return open().rawQuery(sql, null).use { cursor ->
        if (cursor.moveToFirst()) cursor.getInt(0) else 0
    }
}

/** Lista os alertas de validade em ordem de urgência, mais antigos primeiro. */
fun AwsDb.expiryNotifications(
    daysAhead: Int = AWS_NOTIFICATION_DAYS,
    limit: Int = 300
): List<ExpiryRow> {
    val safeDays = daysAhead.coerceIn(0, 3650)
    val safeLimit = limit.coerceIn(1, 2000)
    val out = ArrayList<ExpiryRow>()

    open().rawQuery(
        """
        SELECT cv.id,
               CAST(cv.idproduto AS TEXT),
               COALESCE(p.descricaoproduto,''),
               TRIM(COALESCE(cv.validade,'')),
               COALESCE(cv.lote,''),
               COALESCE(cv.qtdecoletada,0),
               CASE
                   WHEN TRIM(cv.validade) LIKE '____-__-__%' THEN substr(TRIM(cv.validade),1,10)
                   WHEN TRIM(cv.validade) LIKE '__/__/____%' THEN
                       substr(TRIM(cv.validade),7,4) || '-' ||
                       substr(TRIM(cv.validade),4,2) || '-' ||
                       substr(TRIM(cv.validade),1,2)
                   ELSE NULL
               END AS validade_normalizada
        FROM controlevalidade cv
        LEFT JOIN produto p ON CAST(p.id AS TEXT)=CAST(cv.idproduto AS TEXT)
        WHERE TRIM(COALESCE(cv.validade,'')) <> ''
          AND UPPER(TRIM(COALESCE(cv.status,'P'))) NOT IN ('C','CANCELADO','FINALIZADO','F')
          AND date(
              CASE
                  WHEN TRIM(cv.validade) LIKE '____-__-__%' THEN substr(TRIM(cv.validade),1,10)
                  WHEN TRIM(cv.validade) LIKE '__/__/____%' THEN
                      substr(TRIM(cv.validade),7,4) || '-' ||
                      substr(TRIM(cv.validade),4,2) || '-' ||
                      substr(TRIM(cv.validade),1,2)
                  ELSE NULL
              END
          ) <= date('now','localtime','+$safeDays day')
        ORDER BY date(validade_normalizada) ASC, COALESCE(p.descricaoproduto,'') COLLATE NOCASE
        LIMIT ?
        """.trimIndent(),
        arrayOf(safeLimit.toString())
    ).use { cursor ->
        while (cursor.moveToNext()) {
            out += ExpiryRow(
                id = cursor.getLong(0),
                productId = cursor.getString(1),
                description = cursor.getString(2),
                expiry = cursor.getString(3),
                lot = cursor.getString(4),
                quantity = cursor.getDouble(5)
            )
        }
    }
    return out
}
