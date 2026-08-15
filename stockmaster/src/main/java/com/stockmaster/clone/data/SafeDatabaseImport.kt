package com.aws.gestaoestoque.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import java.io.File

private val requiredAwsTables = setOf(
    "produto",
    "usuario",
    "ivitens",
    "conferencia",
    "avitens",
    "impressao",
    "controlevalidade"
)

/**
 * Valida o novo SQLite antes de substituir o banco em uso.
 * Se qualquer etapa falhar, restaura automaticamente o banco anterior.
 */
fun AwsDb.importDatabaseSafely(context: Context, uri: Uri) {
    val appContext = context.applicationContext
    val liveFile = appContext.getDatabasePath("aws_estoque_local.db")
    liveFile.parentFile?.mkdirs()

    val candidate = File.createTempFile("aws_db_candidate_", ".db", liveFile.parentFile)
    val backup = File(liveFile.parentFile, "aws_estoque_local.backup")

    try {
        appContext.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Não foi possível abrir o arquivo .db selecionado." }
            candidate.outputStream().use { output -> input.copyTo(output) }
        }

        require(candidate.length() > 0L) { "O arquivo .db selecionado está vazio." }
        validateCandidateDatabase(candidate)

        close()
        deleteSidecars(liveFile)
        backup.delete()

        if (liveFile.exists()) {
            liveFile.copyTo(backup, overwrite = true)
        }

        try {
            candidate.copyTo(liveFile, overwrite = true)
            deleteSidecars(liveFile)
            open()
            validateSchema()
        } catch (error: Exception) {
            close()
            deleteSidecars(liveFile)
            liveFile.delete()

            if (backup.exists()) {
                backup.copyTo(liveFile, overwrite = true)
                runCatching {
                    open()
                    validateSchema()
                }
            }

            throw IllegalArgumentException(
                "O novo banco não pôde ser aplicado. O banco anterior foi preservado.",
                error
            )
        }
    } finally {
        candidate.delete()
        backup.delete()
    }
}

private fun validateCandidateDatabase(file: File) {
    val candidateDb = try {
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
    } catch (error: Exception) {
        throw IllegalArgumentException("O arquivo selecionado não é um banco SQLite válido.", error)
    }

    candidateDb.use { database ->
        val integrity = database.rawQuery("PRAGMA quick_check(1)", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else ""
        }
        require(integrity.equals("ok", ignoreCase = true)) {
            "O banco selecionado falhou na verificação de integridade."
        }

        val found = mutableSetOf<String>()
        database.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { cursor ->
            while (cursor.moveToNext()) found += cursor.getString(0)
        }

        val missing = requiredAwsTables - found
        require(missing.isEmpty()) {
            "Banco incompatível. Tabelas ausentes: ${missing.joinToString()}"
        }
    }
}

private fun deleteSidecars(databaseFile: File) {
    File(databaseFile.absolutePath + "-wal").delete()
    File(databaseFile.absolutePath + "-shm").delete()
    File(databaseFile.absolutePath + "-journal").delete()
}
