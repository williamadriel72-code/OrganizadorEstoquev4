package com.stockmaster.clone.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.FileObserver
import java.util.concurrent.Executors

/**
 * Garante o banco integrado na primeira abertura e dispara a instalação
 * complementar do conjunto de mercadorias/validades quando necessário.
 */
class AwsBundledDataProvider : ContentProvider() {
    private val executor = Executors.newSingleThreadExecutor()
    private var observer: FileObserver? = null

    override fun onCreate(): Boolean {
        val ctx = context?.applicationContext ?: return true
        val dbFile = ctx.getDatabasePath("aws_estoque_local.db")
        dbFile.parentFile?.mkdirs()

        // O ContentProvider inicia antes da Activity. Assim, no build privado,
        // o banco aws_seed.db já fica pronto antes da tela de login ser criada.
        runCatching { ensureBundledDatabase(ctx) }

        val parentPath = dbFile.parentFile?.absolutePath ?: return true
        observer = object : FileObserver(
            parentPath,
            CLOSE_WRITE or MOVED_TO or CREATE
        ) {
            override fun onEvent(event: Int, path: String?) {
                if (path == dbFile.name) installAsync()
            }
        }.also { it.startWatching() }

        installAsync()
        return true
    }

    private fun installAsync() {
        val ctx = context?.applicationContext ?: return
        executor.execute {
            repeat(5) { attempt ->
                val db = StockMasterDb(ctx)
                try {
                    if (!db.hasDatabase()) return@execute
                    val database = db.open()
                    AwsBundledDataInstaller(ctx).installIfNeeded(database)
                    db.close()
                    return@execute
                } catch (_: Exception) {
                    db.close()
                    if (attempt < 4) Thread.sleep(400L)
                }
            }
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    override fun shutdown() {
        observer?.stopWatching()
        executor.shutdownNow()
        super.shutdown()
    }
}
