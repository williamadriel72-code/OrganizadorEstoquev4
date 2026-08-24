package com.multifigurinhas.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_IMAGES = 1001;
    private static final int MIN_STICKERS = 3;
    private static final int MAX_STICKERS = 30;
    private static final long MAX_STICKER_BYTES = 100L * 1024L;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ArrayList<Uri> selectedUris = new ArrayList<>();

    private TextView status;
    private TextView selectionCount;
    private LinearLayout previewRow;
    private Button selectButton;
    private Button clearButton;
    private Button prepareButton;
    private Button addButton;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        refreshState();
        updateSelectionControls();
    }

    private void buildUi() {
        int pad = dp(20);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Multi Figurinhas");
        title.setTextSize(28);
        title.setTextColor(Color.rgb(7, 94, 84));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(12), 0, dp(8));
        root.addView(title, fullWidth());

        TextView description = new TextView(this);
        description.setText("Adicione de 3 a 30 imagens. Se o seletor do seu celular permitir apenas uma por vez, volte e toque em Adicionar imagens novamente. As fotos escolhidas ficam visíveis abaixo.");
        description.setTextSize(16);
        description.setTextColor(Color.DKGRAY);
        description.setGravity(Gravity.CENTER);
        description.setPadding(0, 0, 0, dp(18));
        root.addView(description, fullWidth());

        selectButton = new Button(this);
        selectButton.setText("1. ADICIONAR IMAGENS");
        selectButton.setOnClickListener(v -> openPicker());
        root.addView(selectButton, buttonParams());

        selectionCount = new TextView(this);
        selectionCount.setTextSize(15);
        selectionCount.setTextColor(Color.DKGRAY);
        selectionCount.setGravity(Gravity.CENTER);
        selectionCount.setPadding(0, dp(10), 0, dp(8));
        root.addView(selectionCount, fullWidth());

        HorizontalScrollView previewScroll = new HorizontalScrollView(this);
        previewScroll.setHorizontalScrollBarEnabled(true);
        previewRow = new LinearLayout(this);
        previewRow.setOrientation(LinearLayout.HORIZONTAL);
        previewRow.setGravity(Gravity.CENTER_VERTICAL);
        previewRow.setPadding(0, dp(4), 0, dp(4));
        previewScroll.addView(previewRow);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(104));
        previewParams.setMargins(0, 0, 0, dp(6));
        root.addView(previewScroll, previewParams);

        clearButton = new Button(this);
        clearButton.setText("LIMPAR SELEÇÃO");
        clearButton.setOnClickListener(v -> clearSelection());
        root.addView(clearButton, buttonParams());

        prepareButton = new Button(this);
        prepareButton.setText("2. PREPARAR PACOTE");
        prepareButton.setOnClickListener(v -> {
            if (selectedUris.size() < MIN_STICKERS) {
                Toast.makeText(this, "Adicione pelo menos 3 imagens.", Toast.LENGTH_SHORT).show();
                return;
            }
            preparePack(new ArrayList<>(selectedUris));
        });
        root.addView(prepareButton, buttonParams());

        progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(48), dp(48));
        pp.setMargins(0, dp(16), 0, dp(8));
        root.addView(progress, pp);

        status = new TextView(this);
        status.setTextSize(15);
        status.setTextColor(Color.DKGRAY);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(14), 0, dp(20));
        root.addView(status, fullWidth());

        addButton = new Button(this);
        addButton.setText("3. ADICIONAR AO WHATSAPP");
        addButton.setOnClickListener(v -> addPackToWhatsApp());
        root.addView(addButton, buttonParams());

        TextView note = new TextView(this);
        note.setText("Depois de adicionar o pacote, abra uma conversa no WhatsApp e use as figurinhas normalmente pelo seletor de stickers.");
        note.setTextSize(14);
        note.setTextColor(Color.GRAY);
        note.setPadding(0, dp(24), 0, dp(10));
        root.addView(note, fullWidth());

        setContentView(scroll);
    }

    private void openPicker() {
        if (selectedUris.size() >= MAX_STICKERS) {
            Toast.makeText(this, "Você já selecionou 30 imagens.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_IMAGES);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_IMAGES || resultCode != RESULT_OK || data == null) return;

        ArrayList<Uri> returned = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                returned.add(clip.getItemAt(i).getUri());
            }
        } else if (data.getData() != null) {
            returned.add(data.getData());
        }

        int added = 0;
        for (Uri uri : returned) {
            if (selectedUris.size() >= MAX_STICKERS) break;
            if (uri == null || selectedUris.contains(uri)) continue;
            persistReadPermission(uri, data);
            selectedUris.add(uri);
            added++;
        }

        renderPreviews();
        updateSelectionControls();

        if (added == 0) {
            Toast.makeText(this, "Nenhuma imagem nova foi adicionada.", Toast.LENGTH_SHORT).show();
        } else if (selectedUris.size() < MIN_STICKERS) {
            Toast.makeText(this,
                    added + " imagem(ns) adicionada(s). Adicione mais " + (MIN_STICKERS - selectedUris.size()) + ".",
                    Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this,
                    selectedUris.size() + " imagens selecionadas. Você já pode preparar o pacote.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void persistReadPermission(Uri uri, Intent data) {
        try {
            int takeFlags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            if (takeFlags != 0) {
                getContentResolver().takePersistableUriPermission(uri, takeFlags);
            }
        } catch (Exception ignored) {
        }
    }

    private void clearSelection() {
        selectedUris.clear();
        previewRow.removeAllViews();
        updateSelectionControls();
        setStatus("Seleção limpa. Adicione de 3 a 30 imagens.");
    }

    private void updateSelectionControls() {
        int count = selectedUris.size();
        selectButton.setEnabled(count < MAX_STICKERS && progress.getVisibility() != View.VISIBLE);
        clearButton.setEnabled(count > 0 && progress.getVisibility() != View.VISIBLE);
        prepareButton.setEnabled(count >= MIN_STICKERS && progress.getVisibility() != View.VISIBLE);
        selectionCount.setText("Selecionadas: " + count + " / " + MAX_STICKERS
                + (count < MIN_STICKERS ? "  •  faltam " + (MIN_STICKERS - count) : "  •  prontas para preparar"));
    }

    private void renderPreviews() {
        previewRow.removeAllViews();
        for (Uri uri : selectedUris) {
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(Color.rgb(230, 230, 230));
            image.setTag(uri.toString());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(88), dp(88));
            params.setMargins(0, 0, dp(8), 0);
            previewRow.addView(image, params);

            executor.execute(() -> {
                Bitmap thumb = loadPreviewBitmap(uri);
                if (thumb == null) return;
                runOnUiThread(() -> {
                    if (uri.toString().equals(image.getTag())) {
                        image.setImageBitmap(thumb);
                    } else {
                        thumb.recycle();
                    }
                });
            });
        }
    }

    private Bitmap loadPreviewBitmap(Uri uri) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return getContentResolver().loadThumbnail(uri, new Size(240, 240), null);
            }

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }

            int sample = 1;
            while (bounds.outWidth / sample > 320 || bounds.outHeight / sample > 320) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = Math.max(1, sample);
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(in, null, options);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void preparePack(List<Uri> uris) {
        selectButton.setEnabled(false);
        clearButton.setEnabled(false);
        prepareButton.setEnabled(false);
        addButton.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        setStatus("Preparando " + uris.size() + " figurinhas...");

        executor.execute(() -> {
            try {
                File packDir = PackStorage.getPackDirectory(this);
                PackStorage.clearDirectory(packDir);
                int success = 0;
                Bitmap firstSticker = null;

                for (Uri uri : uris) {
                    Bitmap source = decodeBitmap(uri);
                    if (source == null) continue;
                    Bitmap sticker = fitIntoStickerCanvas(source);
                    source.recycle();

                    String name = String.format(Locale.US, "sticker_%02d.webp", success + 1);
                    File out = new File(packDir, name);
                    if (writeWebPWithinLimit(sticker, out)) {
                        if (firstSticker == null) firstSticker = sticker.copy(Bitmap.Config.ARGB_8888, false);
                        success++;
                    }
                    sticker.recycle();
                }

                if (success < MIN_STICKERS) {
                    PackStorage.clearDirectory(packDir);
                    throw new IllegalStateException("Não foi possível criar pelo menos 3 figurinhas válidas.");
                }

                if (firstSticker != null) {
                    writeTrayIcon(firstSticker, new File(packDir, PackStorage.TRAY_FILE));
                    firstSticker.recycle();
                }

                long version = System.currentTimeMillis();
                getSharedPreferences(PackStorage.PREFS, MODE_PRIVATE).edit()
                        .putLong(PackStorage.KEY_VERSION, version).apply();
                getContentResolver().notifyChange(Uri.parse("content://" + StickerContentProvider.AUTHORITY + "/metadata"), null);

                int finalSuccess = success;
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    updateSelectionControls();
                    addButton.setEnabled(true);
                    setStatus("Pacote pronto com " + finalSuccess + " figurinhas. Toque em Adicionar ao WhatsApp.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    updateSelectionControls();
                    addButton.setEnabled(false);
                    setStatus("Erro: " + e.getMessage());
                    Toast.makeText(this, "Falha ao preparar as figurinhas.", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private Bitmap decodeBitmap(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            return null;
        }
    }

    private Bitmap fitIntoStickerCanvas(Bitmap source) {
        int size = 512;
        Bitmap result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        canvas.drawColor(Color.TRANSPARENT);
        float scale = Math.min((float) size / source.getWidth(), (float) size / source.getHeight());
        float w = source.getWidth() * scale;
        float h = source.getHeight() * scale;
        float left = (size - w) / 2f;
        float top = (size - h) / 2f;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(source, null, new RectF(left, top, left + w, top + h), paint);
        return result;
    }

    private boolean writeWebPWithinLimit(Bitmap bitmap, File out) throws Exception {
        for (int quality = 88; quality >= 18; quality -= 7) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            Bitmap.CompressFormat format = Build.VERSION.SDK_INT >= 30
                    ? Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP;
            bitmap.compress(format, quality, buffer);
            byte[] data = buffer.toByteArray();
            if (data.length <= MAX_STICKER_BYTES) {
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    fos.write(data);
                }
                return true;
            }
        }
        return false;
    }

    private void writeTrayIcon(Bitmap source, File out) throws Exception {
        Bitmap tray = Bitmap.createScaledBitmap(source, 96, 96, true);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            tray.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        tray.recycle();
    }

    private void addPackToWhatsApp() {
        if (PackStorage.getStickerFiles(this).size() < MIN_STICKERS) {
            Toast.makeText(this, "Prepare um pacote primeiro.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK");
        intent.putExtra("sticker_pack_id", PackStorage.PACK_ID);
        intent.putExtra("sticker_pack_authority", StickerContentProvider.AUTHORITY);
        intent.putExtra("sticker_pack_name", "Minhas Figurinhas");

        try {
            intent.setPackage("com.whatsapp");
            startActivity(intent);
        } catch (ActivityNotFoundException first) {
            try {
                intent.setPackage("com.whatsapp.w4b");
                startActivity(intent);
            } catch (ActivityNotFoundException second) {
                Toast.makeText(this, "WhatsApp não encontrado ou não aceitou a integração.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void refreshState() {
        int count = PackStorage.getStickerFiles(this).size();
        boolean ready = count >= MIN_STICKERS && new File(PackStorage.getPackDirectory(this), PackStorage.TRAY_FILE).exists();
        addButton.setEnabled(ready);
        setStatus(ready ? "Pacote salvo com " + count + " figurinhas." : "Adicione de 3 a 30 imagens para começar.");
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams p = fullWidth();
        p.setMargins(0, dp(6), 0, dp(6));
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void setStatus(String text) {
        if (status != null) status.setText(text);
    }
}
