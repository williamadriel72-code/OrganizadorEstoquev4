package com.organizador.estoque.data

import android.content.ContentValues
import android.database.DatabaseUtils
import java.time.LocalDate

class InventoryRepository(private val dbHelper: InventoryDb) {
    fun dashboardStats(): DashboardStats {
        val db = dbHelper.readableDatabase
        val today = LocalDate.now().toString()
        val plus7 = LocalDate.now().plusDays(7).toString()
        val plus30 = LocalDate.now().plusDays(30).toString()
        val plus60 = LocalDate.now().plusDays(60).toString()
        val sql = """
            SELECT
                COUNT(*),
                COALESCE(SUM(stock), 0),
                SUM(CASE WHEN stock > 0 AND stock <= 5 THEN 1 ELSE 0 END),
                SUM(CASE WHEN stock = 0 THEN 1 ELSE 0 END),
                SUM(CASE WHEN stock < 0 THEN 1 ELSE 0 END),
                SUM(CASE WHEN NOT EXISTS (SELECT 1 FROM product_addresses pa WHERE pa.product_code = p.code) THEN 1 ELSE 0 END),
                (SELECT COUNT(DISTINCT product_code) FROM expiry_batches WHERE quantity > 0 AND expiry_date < ?),
                (SELECT COUNT(DISTINCT product_code) FROM expiry_batches WHERE quantity > 0 AND expiry_date BETWEEN ? AND ?),
                (SELECT COUNT(DISTINCT product_code) FROM expiry_batches WHERE quantity > 0 AND expiry_date BETWEEN ? AND ?),
                (SELECT COUNT(DISTINCT product_code) FROM expiry_batches WHERE quantity > 0 AND expiry_date BETWEEN ? AND ?)
            FROM products p
            WHERE active = 1
        """.trimIndent()
        return db.rawQuery(sql, arrayOf(today, today, plus7, today, plus30, today, plus60)).use { c ->
            if (!c.moveToFirst()) return@use DashboardStats()
            DashboardStats(
                products = c.getLong(0),
                totalStock = c.getDouble(1),
                lowStock = c.getLong(2),
                zeroStock = c.getLong(3),
                negativeStock = c.getLong(4),
                withoutAddress = c.getLong(5),
                expired = c.getLong(6),
                expiring7 = c.getLong(7),
                expiring30 = c.getLong(8),
                expiring60 = c.getLong(9)
            )
        }
    }

    fun searchProducts(query: String, limit: Int = 100, offset: Int = 0, filter: String = "all"): List<Product> {
        val q = query.trim()
        val whereFilter = when (filter) {
            "zero" -> " AND p.stock = 0"
            "low" -> " AND p.stock > 0 AND p.stock <= 5"
            "negative" -> " AND p.stock < 0"
            "no_address" -> " AND NOT EXISTS (SELECT 1 FROM product_addresses pa WHERE pa.product_code=p.code)"
            else -> ""
        }
        val sql = if (q.isBlank()) {
            "SELECT p.code,p.ean,p.description,p.group_code,p.category,p.stock,p.controls_expiry,p.active FROM products p WHERE p.active=1$whereFilter ORDER BY p.description LIMIT ? OFFSET ?"
        } else {
            "SELECT p.code,p.ean,p.description,p.group_code,p.category,p.stock,p.controls_expiry,p.active FROM products p WHERE p.active=1$whereFilter AND (p.code=? OR p.ean=? OR p.description LIKE ?) ORDER BY CASE WHEN p.code=? THEN 0 WHEN p.ean=? THEN 1 ELSE 2 END, p.description LIMIT ? OFFSET ?"
        }
        val args = if (q.isBlank()) arrayOf(limit.toString(), offset.toString()) else arrayOf(q, q, "%$q%", q, q, limit.toString(), offset.toString())
        return dbHelper.readableDatabase.rawQuery(sql, args).use { c ->
            buildList {
                while (c.moveToNext()) add(Product(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getDouble(5), c.getInt(6) == 1, c.getInt(7) == 1))
            }
        }
    }

    fun findExact(codeOrEan: String): Product? {
        val value = codeOrEan.trim()
        return dbHelper.readableDatabase.rawQuery(
            "SELECT code,ean,description,group_code,category,stock,controls_expiry,active FROM products WHERE active=1 AND (ean=? OR code=?) LIMIT 1",
            arrayOf(value, value)
        ).use { c -> if (!c.moveToFirst()) null else Product(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getDouble(5), c.getInt(6)==1, c.getInt(7)==1) }
    }

    fun findExistingByCodes(codes: Collection<String>): Map<String, Product> {
        if (codes.isEmpty()) return emptyMap()
        val db = dbHelper.readableDatabase
        val result = LinkedHashMap<String, Product>()
        codes.asSequence().map { it.trim() }.filter { it.isNotEmpty() }.distinct().chunked(400).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            val sql = "SELECT code,ean,description,group_code,category,stock,controls_expiry,active FROM products WHERE active=1 AND code IN ($placeholders)"
            db.rawQuery(sql, chunk.toTypedArray()).use { c ->
                while (c.moveToNext()) {
                    val product = Product(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getDouble(5), c.getInt(6)==1, c.getInt(7)==1)
                    result[product.code] = product
                }
            }
        }
        return result
    }

    fun upsertProduct(product: Product) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("ean", product.ean); put("description", product.description); put("group_code", product.groupCode); put("category", product.category); put("stock", product.stock)
            put("controls_expiry", if (product.controlsExpiry) 1 else 0); put("active", if (product.active) 1 else 0); put("updated_at", System.currentTimeMillis())
        }
        val updated = db.update("products", values, "code=?", arrayOf(product.code))
        if (updated == 0) { values.put("code", product.code); db.insertOrThrow("products", null, values) }
    }

    fun replaceInventory(products: List<Product>) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            products.forEach { product ->
                val values = ContentValues().apply {
                    put("ean", product.ean); put("description", product.description); put("group_code", product.groupCode); put("category", product.category); put("stock", product.stock)
                    put("controls_expiry", if (product.controlsExpiry) 1 else 0); put("active", if (product.active) 1 else 0); put("updated_at", now)
                }
                val updated = db.update("products", values, "code=?", arrayOf(product.code))
                if (updated == 0) { values.put("code", product.code); db.insertOrThrow("products", null, values) }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun stockIn(codeOrEan: String, quantity: Double, expiryDate: String? = null): Product {
        require(quantity > 0) { "Quantidade deve ser maior que zero" }
        val product = findExact(codeOrEan) ?: error("Produto não encontrado")
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val after = product.stock + quantity
            db.execSQL("UPDATE products SET stock=?, updated_at=? WHERE code=?", arrayOf<Any?>(after, System.currentTimeMillis(), product.code))
            if (!expiryDate.isNullOrBlank()) db.execSQL(
                "INSERT INTO expiry_batches(product_code,expiry_date,quantity,updated_at) VALUES(?,?,?,?) ON CONFLICT(product_code,expiry_date) DO UPDATE SET quantity=quantity+excluded.quantity, updated_at=excluded.updated_at",
                arrayOf<Any?>(product.code, expiryDate, quantity, System.currentTimeMillis())
            )
            db.insertOrThrow("stock_movements", null, ContentValues().apply {
                put("product_code", product.code); put("movement_type", "IN"); put("quantity", quantity); put("before_stock", product.stock); put("after_stock", after); put("reason", "Entrada manual"); put("created_at", System.currentTimeMillis())
            })
            db.setTransactionSuccessful()
            return product.copy(stock = after)
        } finally { db.endTransaction() }
    }

    fun stockOut(codeOrEan: String, quantity: Double): Product {
        require(quantity > 0) { "Quantidade deve ser maior que zero" }
        val product = findExact(codeOrEan) ?: error("Produto não encontrado")
        require(product.stock >= quantity) { "Estoque insuficiente" }
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            var remaining = quantity
            db.rawQuery("SELECT id,quantity FROM expiry_batches WHERE product_code=? AND quantity>0 ORDER BY expiry_date ASC", arrayOf(product.code)).use { c ->
                while (c.moveToNext() && remaining > 0) {
                    val id = c.getLong(0); val available = c.getDouble(1); val used = minOf(available, remaining)
                    db.execSQL("UPDATE expiry_batches SET quantity=quantity-?, updated_at=? WHERE id=?", arrayOf<Any?>(used, System.currentTimeMillis(), id))
                    remaining -= used
                }
            }
            val after = product.stock - quantity
            db.execSQL("UPDATE products SET stock=?, updated_at=? WHERE code=?", arrayOf<Any?>(after, System.currentTimeMillis(), product.code))
            db.insertOrThrow("stock_movements", null, ContentValues().apply {
                put("product_code", product.code); put("movement_type", "OUT"); put("quantity", quantity); put("before_stock", product.stock); put("after_stock", after); put("reason", "Saída FEFO/manual"); put("created_at", System.currentTimeMillis())
            })
            db.setTransactionSuccessful()
            return product.copy(stock = after)
        } finally { db.endTransaction() }
    }

    fun productAddresses(productCode: String): List<String> = dbHelper.readableDatabase.rawQuery(
        "SELECT a.name FROM addresses a JOIN product_addresses pa ON pa.address_id=a.id WHERE pa.product_code=? ORDER BY pa.is_primary DESC,a.sort_order,a.name",
        arrayOf(productCode)
    ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    fun setAddress(productCode: String, addressName: String) {
        val name = addressName.trim()
        if (name.isBlank()) return
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("INSERT OR IGNORE INTO addresses(name,sort_order) VALUES(?,0)", arrayOf(name))
            val addressId = DatabaseUtils.longForQuery(db, "SELECT id FROM addresses WHERE name=?", arrayOf(name))
            db.execSQL("INSERT OR IGNORE INTO product_addresses(product_code,address_id,is_primary) VALUES(?,?,1)", arrayOf<Any?>(productCode, addressId))
            db.execSQL("UPDATE product_addresses SET is_primary=CASE WHEN address_id=? THEN 1 ELSE 0 END WHERE product_code=?", arrayOf<Any?>(addressId, productCode))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
}
