package com.organizador.estoque.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class CatalogPriceStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DB_NAME,
    null,
    DB_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE product_prices (
                product_code TEXT PRIMARY KEY,
                price REAL NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun replace(prices: Map<String, Double>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("product_prices", null, null)
            val now = System.currentTimeMillis()
            prices.forEach { (code, price) ->
                db.insertOrThrow("product_prices", null, ContentValues().apply {
                    put("product_code", code)
                    put("price", price)
                    put("updated_at", now)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun priceFor(productCode: String): Double? = readableDatabase.rawQuery(
        "SELECT price FROM product_prices WHERE product_code=? LIMIT 1",
        arrayOf(productCode.trim())
    ).use { cursor ->
        if (cursor.moveToFirst()) cursor.getDouble(0) else null
    }

    companion object {
        private const val DB_NAME = "catalog_prices_v1.db"
        private const val DB_VERSION = 1
    }
}
