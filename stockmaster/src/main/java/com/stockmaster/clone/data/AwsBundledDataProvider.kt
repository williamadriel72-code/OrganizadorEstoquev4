package com.aws.gestaoestoque.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * Prepara o banco integrado antes da primeira Activity abrir.
 * O APK final já contém mercadorias, validades e usuários completos, portanto
 * não existe mais uma segunda importação pesada em segundo plano.
 */
class AwsBundledDataProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val ctx = context?.applicationContext ?: return true

        runCatching {
            ensureBundledDatabase(ctx)

            // Busca de preço deve ficar disponível no AWS independentemente
            // da permissão antiga gravada no banco original.
            val db = AwsDb(ctx)
            if (db.hasDatabase()) {
                val database = db.open()
                database.execSQL(
                    "UPDATE usuario SET acessosUser = REPLACE(COALESCE(acessosUser,'{}'), '" +
                        "\"acessoBuscaPreco\":false', '\"acessoBuscaPreco\":true')"
                )
                db.close()
            }
        }

        return true
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
}
