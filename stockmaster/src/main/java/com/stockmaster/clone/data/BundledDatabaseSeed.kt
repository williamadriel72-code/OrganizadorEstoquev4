package com.aws.gestaoestoque.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Garante que o banco completo empacotado no APK esteja pronto antes da UI.
 * Se já existir um banco local completo, ele é preservado para não perder
 * registros feitos pelo usuário. Bancos antigos/parciais são substituídos
 * uma única vez pelo seed completo do APK.
 */
internal fun ensureBundledDatabase(context: Context): Boolean {
    val appContext = context.applicationContext
    val target = appContext.getDatabasePath("aws_estoque_local.db")
    val assets = appContext.assets.list("")?.toSet().orEmpty()
    if (SEED_ASSET !in assets) return false

    if (target.exists() && target.length() > 0L && localDatabaseIsComplete(target)) {
        return false
    }

    target.parentFile?.mkdirs()
    val temp = File(target.parentFile, "${target.name}.seed.tmp")
    temp.delete()

    try {
        appContext.assets.open(SEED_ASSET).use { input ->
            temp.outputStream().use { output -> input.copyTo(output) }
        }
        require(temp.length() > 0L) { "Banco integrado vazio." }
        require(localDatabaseIsComplete(temp)) { "Banco integrado incompleto." }

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

        val db = AwsDb(appContext)
        db.validateSchema()
        db.close()
        return true
    } catch (error: Exception) {
        temp.delete()
        if (target.exists() && target.length() == 0L) target.delete()
        throw error
    }
}

private fun localDatabaseIsComplete(file: File): Boolean {
    if (!file.exists() || file.length() <= 0L) return false

    return runCatching {
        SQLiteDatabase.openDatabase(
            file.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        ).use { db ->
            val productCount = db.rawQuery("SELECT COUNT(*) FROM produto", null).use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
            val expiryCount = db.rawQuery("SELECT COUNT(*) FROM controlevalidade", null).use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
            val userCount = db.rawQuery("SELECT COUNT(*) FROM usuario", null).use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }

            productCount >= MIN_PRODUCTS &&
                expiryCount >= MIN_EXPIRIES &&
                userCount >= MIN_USERS
        }
    }.getOrDefault(false)
}

private const val SEED_ASSET = "aws_seed.db"
private const val MIN_PRODUCTS = 12992
private const val MIN_EXPIRIES = 6077
private const val MIN_USERS = 160
