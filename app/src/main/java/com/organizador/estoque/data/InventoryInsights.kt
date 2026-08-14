package com.organizador.estoque.data

import android.content.Context
import android.database.DatabaseUtils
import java.time.LocalDate

class InventoryInsights(context: Context) {
    private val dbHelper = InventoryDb(context.applicationContext)

    fun negativeCount(): Long = DatabaseUtils.longForQuery(
        dbHelper.readableDatabase,
        "SELECT COUNT(*) FROM products WHERE active=1 AND stock < 0",
        null
    )

    fun negativeProducts(query: String, limit: Int = 500): List<Product> {
        val q = query.trim()
        val sql: String
        val args: Array<String>
        if (q.isBlank()) {
            sql = "SELECT code,ean,description,group_code,category,stock,controls_expiry,active FROM products WHERE active=1 AND stock < 0 ORDER BY stock ASC, description LIMIT ?"
            args = arrayOf(limit.toString())
        } else {
            sql = "SELECT code,ean,description,group_code,category,stock,controls_expiry,active FROM products WHERE active=1 AND stock < 0 AND (code=? OR ean=? OR description LIKE ?) ORDER BY stock ASC, description LIMIT ?"
            args = arrayOf(q, q, "%$q%", limit.toString())
        }
        return dbHelper.readableDatabase.rawQuery(sql, args).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    Product(
                        c.getString(0), c.getString(1), c.getString(2), c.getString(3),
                        c.getString(4), c.getDouble(5), c.getInt(6) == 1, c.getInt(7) == 1
                    )
                )
            }
        }
    }

    fun expiryDetails(filter: String = "all"): List<ExpiryDetail> {
        val today = LocalDate.now()
        val where = when (filter) {
            "expired" -> " AND e.expiry_date < '${today}'"
            "7" -> " AND e.expiry_date BETWEEN '${today}' AND '${today.plusDays(7)}'"
            "30" -> " AND e.expiry_date > '${today.plusDays(7)}' AND e.expiry_date <= '${today.plusDays(30)}'"
            "60" -> " AND e.expiry_date > '${today.plusDays(30)}' AND e.expiry_date <= '${today.plusDays(60)}'"
            else -> ""
        }
        val sql = """
            SELECT e.id,e.product_code,p.description,p.ean,e.expiry_date,e.quantity,p.stock
            FROM expiry_batches e
            JOIN products p ON p.code=e.product_code
            WHERE e.quantity > 0 AND p.active=1$where
            ORDER BY e.expiry_date ASC,p.description ASC
        """.trimIndent()
        return dbHelper.readableDatabase.rawQuery(sql, null).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    ExpiryDetail(
                        id = c.getLong(0),
                        productCode = c.getString(1),
                        description = c.getString(2),
                        ean = c.getString(3),
                        expiryDate = c.getString(4),
                        quantity = c.getDouble(5),
                        stock = c.getDouble(6)
                    )
                )
            }
        }
    }
}

data class ExpiryDetail(
    val id: Long,
    val productCode: String,
    val description: String,
    val ean: String?,
    val expiryDate: String,
    val quantity: Double,
    val stock: Double
)
