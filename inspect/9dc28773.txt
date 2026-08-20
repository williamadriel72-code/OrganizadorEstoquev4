package com.organizador.estoque.data

import android.content.ContentValues
import android.content.Context

class InventorySnapshotInstaller(context: Context) {
    private val dbHelper = InventoryDb(context.applicationContext)

    fun replace(products: List<Product>) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()

            // A nova relação é a fonte oficial. Mantemos os registros antigos apenas
            // para não quebrar históricos/endereço, mas eles deixam de ficar ativos.
            // O EAN é limpo antes da carga para evitar conflito do índice UNIQUE caso
            // um código de barras tenha mudado de código interno entre relatórios.
            db.execSQL(
                "UPDATE products SET active=0, ean=NULL, updated_at=?",
                arrayOf<Any?>(now)
            )

            products.forEach { product ->
                val values = ContentValues().apply {
                    put("ean", product.ean)
                    put("description", product.description)
                    put("stock", product.stock)
                    put("controls_expiry", if (product.controlsExpiry) 1 else 0)
                    put("active", 1)
                    put("updated_at", now)
                }
                val updated = db.update("products", values, "code=?", arrayOf(product.code))
                if (updated == 0) {
                    values.put("code", product.code)
                    values.put("group_code", product.groupCode)
                    values.put("category", product.category)
                    db.insertOrThrow("products", null, values)
                }
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
