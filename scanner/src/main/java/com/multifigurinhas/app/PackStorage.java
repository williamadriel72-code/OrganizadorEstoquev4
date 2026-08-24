package com.multifigurinhas.app;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class PackStorage {
    public static final String PREFS = "pack_prefs";
    public static final String KEY_VERSION = "image_data_version";
    public static final String PACK_ID = "user_pack_1";
    public static final String TRAY_FILE = "tray.png";

    private PackStorage() {}

    public static File getPackDirectory(Context context) {
        File dir = new File(context.getFilesDir(), "sticker_pack");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static List<File> getStickerFiles(Context context) {
        File[] files = getPackDirectory(context).listFiles((dir, name) ->
                name.startsWith("sticker_") && name.endsWith(".webp"));
        List<File> result = new ArrayList<>();
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName));
            result.addAll(Arrays.asList(files));
        }
        return result;
    }

    public static void clearDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isFile()) file.delete();
        }
    }
}
