package com.multifigurinhas.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Objects;

public class StickerContentProvider extends ContentProvider {
    public static final String AUTHORITY = "com.multifigurinhas.app.stickercontentprovider";

    private static final String METADATA = "metadata";
    private static final String STICKERS = "stickers";
    private static final String STICKERS_ASSET = "stickers_asset";

    private static final int METADATA_ALL = 1;
    private static final int METADATA_ONE = 2;
    private static final int STICKERS_LIST = 3;
    private static final int ASSET = 4;

    private final UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);

    @Override
    public boolean onCreate() {
        matcher.addURI(AUTHORITY, METADATA, METADATA_ALL);
        matcher.addURI(AUTHORITY, METADATA + "/*", METADATA_ONE);
        matcher.addURI(AUTHORITY, STICKERS + "/*", STICKERS_LIST);
        matcher.addURI(AUTHORITY, STICKERS_ASSET + "/*/*", ASSET);
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        int code = matcher.match(uri);
        if (code == METADATA_ALL) return metadataCursor(uri, true);
        if (code == METADATA_ONE) {
            String id = uri.getLastPathSegment();
            return metadataCursor(uri, PackStorage.PACK_ID.equals(id));
        }
        if (code == STICKERS_LIST) return stickerCursor(uri);
        throw new IllegalArgumentException("URI desconhecida: " + uri);
    }

    private Cursor metadataCursor(Uri uri, boolean includePack) {
        MatrixCursor cursor = new MatrixCursor(new String[]{
                "sticker_pack_identifier",
                "sticker_pack_name",
                "sticker_pack_publisher",
                "sticker_pack_icon",
                "android_play_store_link",
                "ios_app_download_link",
                "sticker_pack_publisher_email",
                "sticker_pack_publisher_website",
                "sticker_pack_privacy_policy_website",
                "sticker_pack_license_agreement_website",
                "image_data_version",
                "whatsapp_will_not_cache_stickers",
                "animated_sticker_pack"
        });

        List<File> stickers = PackStorage.getStickerFiles(Objects.requireNonNull(getContext()));
        File tray = new File(PackStorage.getPackDirectory(getContext()), PackStorage.TRAY_FILE);
        if (includePack && stickers.size() >= 3 && tray.exists()) {
            long version = getContext().getSharedPreferences(PackStorage.PREFS, 0)
                    .getLong(PackStorage.KEY_VERSION, 1L);
            cursor.addRow(new Object[]{
                    PackStorage.PACK_ID,
                    "Minhas Figurinhas",
                    "Multi Figurinhas",
                    PackStorage.TRAY_FILE,
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    String.valueOf(version),
                    1,
                    0
            });
        }
        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }

    private Cursor stickerCursor(Uri uri) {
        MatrixCursor cursor = new MatrixCursor(new String[]{
                "sticker_file_name",
                "sticker_emoji",
                "sticker_accessibility_text"
        });
        String id = uri.getLastPathSegment();
        if (PackStorage.PACK_ID.equals(id)) {
            List<File> files = PackStorage.getStickerFiles(Objects.requireNonNull(getContext()));
            int index = 1;
            for (File file : files) {
                cursor.addRow(new Object[]{file.getName(), "🙂", "Figurinha " + index});
                index++;
            }
        }
        cursor.setNotificationUri(Objects.requireNonNull(getContext()).getContentResolver(), uri);
        return cursor;
    }

    @Override
    public AssetFileDescriptor openAssetFile(Uri uri, String mode) throws FileNotFoundException {
        if (matcher.match(uri) != ASSET) return null;
        List<String> segments = uri.getPathSegments();
        if (segments.size() != 3) throw new FileNotFoundException("URI inválida");

        String packId = segments.get(1);
        String fileName = segments.get(2);
        if (!PackStorage.PACK_ID.equals(packId) || TextUtils.isEmpty(fileName)) {
            throw new FileNotFoundException("Pacote ou arquivo inválido");
        }

        File packDir = PackStorage.getPackDirectory(Objects.requireNonNull(getContext()));
        File requested = new File(packDir, fileName);
        try {
            if (!Objects.equals(requested.getCanonicalFile().getParentFile(), packDir.getCanonicalFile())) {
                throw new FileNotFoundException("Caminho inválido");
            }
        } catch (Exception e) {
            throw new FileNotFoundException("Caminho inválido");
        }

        boolean allowed = PackStorage.TRAY_FILE.equals(fileName);
        if (!allowed) {
            for (File sticker : PackStorage.getStickerFiles(getContext())) {
                if (sticker.getName().equals(fileName)) {
                    allowed = true;
                    break;
                }
            }
        }
        if (!allowed || !requested.exists()) throw new FileNotFoundException("Arquivo inválido");

        ParcelFileDescriptor pfd = ParcelFileDescriptor.open(requested, ParcelFileDescriptor.MODE_READ_ONLY);
        return new AssetFileDescriptor(pfd, 0, requested.length());
    }

    @Override
    public String getType(Uri uri) {
        int code = matcher.match(uri);
        if (code == METADATA_ALL) return "vnd.android.cursor.dir/vnd." + AUTHORITY + ".metadata";
        if (code == METADATA_ONE) return "vnd.android.cursor.item/vnd." + AUTHORITY + ".metadata";
        if (code == STICKERS_LIST) return "vnd.android.cursor.dir/vnd." + AUTHORITY + ".stickers";
        if (code == ASSET) return PackStorage.TRAY_FILE.equals(uri.getLastPathSegment()) ? "image/png" : "image/webp";
        throw new IllegalArgumentException("URI desconhecida: " + uri);
    }

    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
}
