package com.organizador.estoque.data

import android.content.Context

class ExpiryImportStore(context: Context) {
    private val dbHelper = InventoryDb(context.applicationContext)

    fun replace(rows: List<ExpiryImportRow>): Pair<Int, Int> {
        val db = dbHelper.writableDatabase
        var skipped = 0
        db.beginTransaction()
        try {
            val byRef = HashMap<String, String>()
            db.rawQuery("SELECT code, ean FROM products WHERE active=1", null).use { c ->
                while (c.moveToNext()) {
                    val code = c.getString(0)
                    byRef[code] = code
                    c.getString(1)?.takeIf { it.isNotBlank() }?.let { byRef[it] = code }
                }
            }

            val consolidated = LinkedHashMap<Pair<String, String>, Double>()
            for (row in rows) {
                val code = byRef[row.productRef.trim()]
                if (code == null) {
                    skipped++
                    continue
                }
                val key = code to row.expiryDate
                consolidated[key] = (consolidated[key] ?: 0.0) + row.quantity
            }

            db.delete("expiry_batches", null, null)
            val now = System.currentTimeMillis()
            val productsWithExpiry = LinkedHashSet<String>()
            consolidated.forEach { (key, quantity) ->
                val (code, date) = key
                db.execSQL(
                    "INSERT INTO expiry_batches(product_code,expiry_date,quantity,updated_at) VALUES(?,?,?,?)",
                    arrayOf<Any?>(code, date, quantity, now)
                )
                productsWithExpiry += code
            }

            db.execSQL("UPDATE products SET controls_expiry=0")
            productsWithExpiry.chunked(400).forEach { chunk ->
                val placeholders = chunk.joinToString(",") { "?" }
                db.execSQL(
                    "UPDATE products SET controls_expiry=1 WHERE code IN ($placeholders)",
                    chunk.toTypedArray()
                )
            }

            db.setTransactionSuccessful()
            return consolidated.size to skipped
        } finally {
            db.endTransaction()
        }
    }
}
