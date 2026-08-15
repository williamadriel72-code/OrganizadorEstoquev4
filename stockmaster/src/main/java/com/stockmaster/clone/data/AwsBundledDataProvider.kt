package com.stockmaster.clone.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.FileObserver
import java.io.File
import java.util.concurrent.Executors

/**
 * Dispara a instalação do conjunto integrado em segundo plano quando o app abre
 * e também quando o banco aws_estoque_local.db é criado/substituído.
 */
class AwsBundledDataProvider : ContentProvider() {
    private val executor = Executors.newSingleThreadExecutor()
    private var observer: FileObserver? = null

    override fun onCreate(): Boolean {
        val ctx = context?.applicationContext ?: return true
        val dbFile = ctx.getDatabasePath("aws_estoque_local.db")
        dbFile.parentFile?.mkdirs()

        observer = object : FileObserver(
            dbFile.parentFile?.absolutePath ?: return true,
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
