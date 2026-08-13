package com.organizador.estoque.data

import android.content.Context

class ExpiryImportStore(context: Context) {
    private val dbHelper = InventoryDb(context.applicationContext)

    fun replace(rows: List<ExpiryImportRow>): Pair<Int, Int> {
        val db = dbHelper.writableDatabase
        var imported = 0
        var skipped = 0
        db.beginTransaction()
        try {
            db.delete("expiry_batches", null, null)
            for (row in rows) {
                val code = db.rawQuery(
                    "SELECT code FROM products WHERE active=1 AND (code=? OR ean=?) LIMIT 1",
                    arrayOf(row.productRef, row.productRef)
                ).use { c -> if (c.moveToFirst()) c.getString(0) else null }
                if (code == null) {
                    skipped++
                    continue
                }
                db.execSQL(
                    "INSERT OR REPLACE INTO expiry_batches(product_code,expiry_date,quantity,updated_at) VALUES(?,?,?,?)",
                    arrayOf<Any?>(code, row.expiryDate, row.quantity, System.currentTimeMillis())
                )
                db.execSQL("UPDATE products SET controls_expiry=1 WHERE code=?", arrayOf(code))
                imported++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return imported to skipped
    }
}
