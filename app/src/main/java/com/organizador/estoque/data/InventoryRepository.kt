package com.organizador.estoque.data

import android.content.ContentValues
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import java.time.LocalDate

class InventoryRepository(private val dbHelper: InventoryDb) {

    fun dashboardStats(): DashboardStats {
        val db = dbHelper.readableDatabase
        val today = LocalDate.now()
        fun scalarLong(sql: String, args: Array<String> = emptyArray()): Long =
            DatabaseUtils.longForQuery(db, sql, args)
        fun scalarDouble(sql: String): Double =
            db.rawQuery(sql, null).use { c -> if (c.moveToFirst()) c.getDouble(0) else 0.0 }

        return DashboardStats(
            products = scalarLong("SELECT COUNT(*) FROM products WHERE active=1"),
            totalStock = scalarDouble("SELECT COALESCE(SUM(stock),0) FROM products WHERE active=1"),
            lowStock = scalarLong("SELECT COUNT(*) FROM products WHERE active=1 AND stock > 0 AND stock <= 5"),
            zeroStock = scalarLong("SELECT COUNT(*) FROM products WHERE active=1 AND stock = 0"),
            withoutAddress = scalarLong("SELECT COUNT(*) FROM products p WHERE p.active=1 AND NOT EXISTS (SELECT 1 FROM product_addresses pa WHERE pa.product_code=p.code)"),
            expired = scalarLong("SELECT COUNT(DISTINCT product_code) FROM expiry_batches WHERE quantity > 0 AND expiry_date < ?", arrayOf(today.toString())),
            expiring7 = scalarLong("SELECT COUNT(DISTINCT product_code) FROM expiry_batches WHERE quantity > 0 AND expiry_date BETWEEN ? AND ?", arrayOf(today.toString(), today.plusDays(7).toString())),
            expiring30 = scalarLong("SELECT COUNT(DISTINCT product_code) FROM expiry_batches WHERE quantity > 0 AND expiry_date BETWEEN ? AND ?", arrayOf(today.toString(), today.plusDays(30).toString())),
            expiring60 = scalarLong("SELECT COUNT(DISTINCT product_code) FROM expiry_batches WHERE quantity > 0 AND expiry_date BETWEEN ? AND ?", arrayOf(today.toString(), today.plusDays(60).toString()))
        )
    }

    fun searchProducts(query: String, limit: Int = 50, offset: Int = 0): List<Product> {
        val q = query.trim()
        val args = if (q.isBlank()) arrayOf(limit.toString(), offset.toString())
        else arrayOf(q, q, "%$q%", limit.toString(), offset.toString())
        val sql = if (q.isBlank()) {
            "SELECT code,ean,description,group_code,category,stock,controls_expiry,active FROM products WHERE active=1 ORDER BY description LIMIT ? OFFSET ?"
        } else {
            "SELECT code,ean,description,group_code,category,stock,controls_expiry,active FROM products WHERE active=1 AND (code=? OR ean=? OR description LIKE ?) ORDER BY CASE WHEN code=? THEN 0 WHEN ean=? THEN 1 ELSE 2 END, description LIMIT ? OFFSET ?"
        }
        val finalArgs = if (q.isBlank()) args else arrayOf(q, q, "%$q%", q, q, limit.toString(), offset.toString())
        return dbHelper.readableDatabase.rawQuery(sql, finalArgs).use { c ->
            buildList {
                while (c.moveToNext()) add(Product(
                    code = c.getString(0), ean = c.getString(1), description = c.getString(2),
                    groupCode = c.getString(3), category = c.getString(4), stock = c.getDouble(5),
                    controlsExpiry = c.getInt(6) == 1, active = c.getInt(7) == 1
                ))
            }
        }
    }

    fun findExact(codeOrEan: String): Product? {
        val value = codeOrEan.trim()
        return dbHelper.readableDatabase.rawQuery(
            "SELECT code,ean,description,group_code,category,stock,controls_expiry,active FROM products WHERE active=1 AND (ean=? OR code=?) LIMIT 1",
            arrayOf(value, value)
        ).use { c ->
            if (!c.moveToFirst()) null else Product(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getDouble(5), c.getInt(6)==1, c.getInt(7)==1)
        }
    }

    fun upsertProduct(product: Product) {
        val values = ContentValues().apply {
            put("code", product.code); put("ean", product.ean); put("description", product.description)
            put("group_code", product.groupCode); put("category", product.category); put("stock", product.stock)
            put("controls_expiry", if (product.controlsExpiry) 1 else 0); put("active", if (product.active) 1 else 0)
            put("updated_at", System.currentTimeMillis())
        }
        dbHelper.writableDatabase.insertWithOnConflict("products", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }
}
