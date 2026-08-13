package com.organizador.estoque.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class InventoryDb(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE products (
                code TEXT PRIMARY KEY,
                ean TEXT,
                description TEXT NOT NULL,
                group_code TEXT,
                category TEXT,
                stock REAL NOT NULL DEFAULT 0,
                controls_expiry INTEGER NOT NULL DEFAULT 0,
                active INTEGER NOT NULL DEFAULT 1,
                updated_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX idx_products_ean ON products(ean) WHERE ean IS NOT NULL AND ean <> ''")
        db.execSQL("CREATE INDEX idx_products_group ON products(group_code)")
        db.execSQL("CREATE INDEX idx_products_category ON products(category)")
        db.execSQL("CREATE INDEX idx_products_stock ON products(stock)")
        db.execSQL("CREATE INDEX idx_products_description ON products(description)")

        db.execSQL("""
            CREATE TABLE addresses (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                city TEXT,
                street TEXT,
                sort_order INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE product_addresses (
                product_code TEXT NOT NULL,
                address_id INTEGER NOT NULL,
                is_primary INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(product_code, address_id),
                FOREIGN KEY(product_code) REFERENCES products(code) ON DELETE CASCADE,
                FOREIGN KEY(address_id) REFERENCES addresses(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_product_addresses_product ON product_addresses(product_code)")

        db.execSQL("""
            CREATE TABLE expiry_batches (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                product_code TEXT NOT NULL,
                expiry_date TEXT NOT NULL,
                quantity REAL NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL,
                UNIQUE(product_code, expiry_date),
                FOREIGN KEY(product_code) REFERENCES products(code) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_expiry_product_date ON expiry_batches(product_code, expiry_date)")
        db.execSQL("CREATE INDEX idx_expiry_date ON expiry_batches(expiry_date)")

        db.execSQL("""
            CREATE TABLE stock_movements (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                product_code TEXT NOT NULL,
                movement_type TEXT NOT NULL,
                quantity REAL NOT NULL,
                before_stock REAL NOT NULL,
                after_stock REAL NOT NULL,
                reason TEXT,
                created_at INTEGER NOT NULL,
                reversed_at INTEGER,
                FOREIGN KEY(product_code) REFERENCES products(code)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_movements_product_date ON stock_movements(product_code, created_at DESC)")

        db.execSQL("""
            CREATE TABLE movement_batches (
                movement_id INTEGER NOT NULL,
                batch_id INTEGER NOT NULL,
                quantity REAL NOT NULL,
                expiry_date TEXT NOT NULL,
                PRIMARY KEY(movement_id, batch_id),
                FOREIGN KEY(movement_id) REFERENCES stock_movements(id) ON DELETE CASCADE
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE imports (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                file_name TEXT NOT NULL,
                import_type TEXT NOT NULL,
                status TEXT NOT NULL,
                processed_rows INTEGER NOT NULL DEFAULT 0,
                total_rows INTEGER,
                message TEXT,
                created_at INTEGER NOT NULL,
                finished_at INTEGER
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Toda mudança futura deve entrar aqui como migração incremental.
        // Nunca apagar tabelas em produção.
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
        db.enableWriteAheadLogging()
    }

    companion object {
        private const val DB_NAME = "inventory_v4.db"
        private const val DB_VERSION = 1
    }
}
