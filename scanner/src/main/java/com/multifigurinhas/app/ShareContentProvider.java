package com.multifigurinhas.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

public class ShareContentProvider extends ContentProvider {
    public static final String AUTHORITY = "com.multifigurinhas.app.share";
    public static final String PREFS = "share_prefs";
    public static final String KEY_MIME = "share_mime";

    private static final int STICKER = 1;
    private final UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);

    public ShareContentProvider() {
        matcher.addURI(AUTHORITY, "sticker/*", STICKER);
    }

    public static File getShareDirectory(Context context) {
        File dir = new File(context.getCacheDir(), "share_stickers");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        if (matcher.match(uri) != STICKER) return null;
        Context context = getContext();
        if (context == null) return "image/webp";
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_MIME, "image/webp");
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (matcher.match(uri) != STICKER || !"r".equals(mode)) {
            throw new FileNotFoundException("URI inválida");
        }
        Context context = getContext();
        if (context == null) throw new FileNotFoundException("Contexto indisponível");

        String name = uri.getLastPathSegment();
        if (name == null || !name.matches("sticker_[0-9]{3}\\.(webp|png|jpg|gif)")) {
            throw new FileNotFoundException("Arquivo inválido");
        }

        File file = new File(getShareDirectory(context), name);
        if (!file.exists()) throw new FileNotFoundException(name);
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        if (matcher.match(uri) != STICKER) return null;
        Context context = getContext();
        if (context == null) return null;

        String name = uri.getLastPathSegment();
        if (name == null) return null;
        File file = new File(getShareDirectory(context), name);

        String[] cols = projection != null ? projection
                : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor cursor = new MatrixCursor(cols, 1);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = name;
            else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = file.length();
            else row[i] = null;
        }
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Somente leitura");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
