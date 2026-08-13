package com.organizador.estoque.data

import android.content.Context

class ExpiryImportStore(context: Context) {
    private val dbHelper = InventoryDb(context.applicationContext)

    fun replace(rows: List<ExpiryImportRow>): Pair<Int, Int> = 0 to rows.size
}
