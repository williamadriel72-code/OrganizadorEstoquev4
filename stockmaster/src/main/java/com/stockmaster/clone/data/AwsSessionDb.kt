package com.aws.gestaoestoque.data

import android.content.ContentValues
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AwsDamageSessionRecord(
    val product: ProductRow,
    val quantity: Double,
    val reason: String
)

fun AwsDb.saveInventorySession(
    items: List<Pair<ProductRow, Double>>,
    user: UserSession
) {
    require(items.isNotEmpty()) { "O inventário está vazio." }
    val database = open()
    val now = Date()
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)
    val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(now)

    database.beginTransaction()
    try {
        items.forEach { (product, qty) ->
            val values = ContentValues().apply {
                put("idempresa", product.companyId)
                put("idproduto", product.id)
                put("ean", product.ean)
                put("descricaoproduto", product.description)
                put("status", "P")
                put("preco", product.price)
                put("data", date)
                put("hora", time)
                put("qtestoque", qty)
                put("idusuario", user.id.toIntOrNull())
            }
            database.insertOrThrow("ivitens", null, values)
        }
        database.setTransactionSuccessful()
    } finally {
        database.endTransaction()
    }
}

fun AwsDb.saveDamageSession(
    items: List<AwsDamageSessionRecord>,
    user: UserSession
) {
    require(items.isNotEmpty()) { "A lista de avarias está vazia." }
    val database = open()
    val now = Date()
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)
    val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(now)
    val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(now)

    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS aws_avaria_motivo (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            idproduto TEXT NOT NULL,
            ean TEXT,
            descricaoproduto TEXT,
            qtde REAL NOT NULL,
            motivo TEXT NOT NULL,
            datahora TEXT NOT NULL,
            idusuario TEXT
        )
        """.trimIndent()
    )

    database.beginTransaction()
    try {
        items.forEach { item ->
            val product = item.product
            val values = ContentValues().apply {
                put("idempresa", product.companyId)
                put("idproduto", product.id)
                put("ean", product.ean)
                put("descricaoproduto", product.description)
                put("status", "P")
                put("preco", product.price)
                put("data", date)
                put("hora", time)
                put("qtde", item.quantity)
                put("idusuario", user.id.toIntOrNull())
                put("vencido", if (item.reason.equals("Vencido", ignoreCase = true)) 1 else 0)
            }
            database.insertOrThrow("avitens", null, values)

            val reasonValues = ContentValues().apply {
                put("idproduto", product.id)
                put("ean", product.ean)
                put("descricaoproduto", product.description)
                put("qtde", item.quantity)
                put("motivo", item.reason)
                put("datahora", iso)
                put("idusuario", user.id)
            }
            database.insertOrThrow("aws_avaria_motivo", null, reasonValues)
        }
        database.setTransactionSuccessful()
    } finally {
        database.endTransaction()
    }
}
