package com.stockmaster.clone.data

import android.content.Context
import java.io.File

/**
 * Cria o banco local a partir do arquivo aws_seed.db empacotado no APK.
 * Se o APK não possuir esse asset (build público), não altera nada.
 */
internal fun ensureBundledDatabase(context: Context): Boolean {
    val appContext = context.applicationContext
    val target = appContext.getDatabasePath("aws_estoque_local.db")
    if (target.exists() && target.length() > 0L) return false

    val assets = appContext.assets.list("")?.toSet().orEmpty()
    if (SEED_ASSET !in assets) return false

    target.parentFile?.mkdirs()
    val temp = File(target.parentFile, "${target.name}.seed.tmp")
    temp.delete()

    try {
        appContext.assets.open(SEED_ASSET).use { input ->
            temp.outputStream().use { output -> input.copyTo(output) }
        }
        require(temp.length() > 0L) { "Banco integrado vazio." }

        listOf("-wal", "-shm", "-journal").forEach {
            File(target.absolutePath + it).delete()
        }

        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        require(target.exists() && target.length() > 0L) {
            "Não foi possível preparar o banco integrado."
        }

        val db = StockMasterDb(appContext)
        db.validateSchema()
        db.close()
        return true
    } catch (error: Exception) {
        temp.delete()
        if (target.exists() && target.length() == 0L) target.delete()
        throw error
    }
}

private const val SEED_ASSET = "aws_seed.db"
