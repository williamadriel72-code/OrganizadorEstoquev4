package com.multifigurinhas.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_IMAGES = 1001;
    private static final int MAX_SOURCES = 30;
    private static final int MAX_QUEUE = 100;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ArrayList<Uri> sources = new ArrayList<>();
    private final ArrayList<Uri> queue = new ArrayList<>();

    private int activeIndex = -1;
    private int sentCount = 0;

    private TextView sourceCount;
    private TextView activeLabel;
    private TextView queueCount;
    private TextView progressLabel;
    private LinearLayout sourcesRow;
    private LinearLayout queueRow;
    private ImageView nextImage;
    private Button undoButton;
    private Button clearQueueButton;
    private Button nextButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        updateAll();
    }

    private void buildUi() {
        int pad = dp(18);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(root);

        TextView title = text("Multi Figurinhas — Fila 100", 26, Color.rgb(7, 94, 84));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(8), 0, dp(8));
        root.addView(title, fullWidth());

        TextView description = text(
                "Monte uma sequência de até 100 posições sem tocar figurinha por figurinha. " +
                "Importe algumas imagens-base, escolha uma como ATIVA e use os multiplicadores. " +
                "O app não dispara mensagens sozinho: ele prepara e acompanha a sequência enquanto você usa o WhatsApp.",
                15, Color.DKGRAY);
        description.setGravity(Gravity.CENTER);
        description.setPadding(0, 0, 0, dp(14));
        root.addView(description, fullWidth());

        Button addImages = new Button(this);
        addImages.setText("1. ADICIONAR FIGURINHAS-BASE");
        addImages.setOnClickListener(v -> openPicker());
        root.addView(addImages, buttonParams());

        sourceCount = text("", 15, Color.DKGRAY);
        sourceCount.setGravity(Gravity.CENTER);
        sourceCount.setPadding(0, dp(8), 0, dp(6));
        root.addView(sourceCount, fullWidth());

        HorizontalScrollView sourceScroll = new HorizontalScrollView(this);
        sourcesRow = new LinearLayout(this);
        sourcesRow.setOrientation(LinearLayout.HORIZONTAL);
        sourcesRow.setGravity(Gravity.CENTER_VERTICAL);
        sourceScroll.addView(sourcesRow);
        LinearLayout.LayoutParams sourceScrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(126));
        root.addView(sourceScroll, sourceScrollParams);

        activeLabel = text("Nenhuma figurinha ativa", 16, Color.rgb(7, 94, 84));
        activeLabel.setGravity(Gravity.CENTER);
        activeLabel.setPadding(0, dp(8), 0, dp(6));
        root.addView(activeLabel, fullWidth());

        TextView repeatTitle = text("Adicionar a figurinha ATIVA à fila:", 15, Color.DKGRAY);
        repeatTitle.setGravity(Gravity.CENTER);
        root.addView(repeatTitle, fullWidth());

        LinearLayout repeat1 = horizontalRow();
        repeat1.addView(multiplierButton("×1", 1), smallButtonParams());
        repeat1.addView(multiplierButton("×2", 2), smallButtonParams());
        repeat1.addView(multiplierButton("×5", 5), smallButtonParams());
        repeat1.addView(multiplierButton("×10", 10), smallButtonParams());
        root.addView(repeat1, fullWidth());

        LinearLayout repeat2 = horizontalRow();
        repeat2.addView(multiplierButton("×25", 25), smallButtonParams());
        repeat2.addView(multiplierButton("×50", 50), smallButtonParams());
        repeat2.addView(multiplierButton("×100", 100), smallButtonParams());
        root.addView(repeat2, fullWidth());

        TextView autoTitle = text("Ou preencha a fila usando todas as figurinhas-base em sequência:", 15, Color.DKGRAY);
        autoTitle.setGravity(Gravity.CENTER);
        autoTitle.setPadding(0, dp(12), 0, dp(4));
        root.addView(autoTitle, fullWidth());

        LinearLayout fill1 = horizontalRow();
        fill1.addView(fillButton("10", 10), smallButtonParams());
        fill1.addView(fillButton("25", 25), smallButtonParams());
        fill1.addView(fillButton("50", 50), smallButtonParams());
        fill1.addView(fillButton("100", 100), smallButtonParams());
        root.addView(fill1, fullWidth());

        queueCount = text("", 18, Color.rgb(7, 94, 84));
        queueCount.setGravity(Gravity.CENTER);
        queueCount.setPadding(0, dp(14), 0, dp(6));
        root.addView(queueCount, fullWidth());

        HorizontalScrollView queueScroll = new HorizontalScrollView(this);
        queueRow = new LinearLayout(this);
        queueRow.setOrientation(LinearLayout.HORIZONTAL);
        queueRow.setGravity(Gravity.CENTER_VERTICAL);
        queueScroll.addView(queueRow);
        LinearLayout.LayoutParams queueParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(88));
        root.addView(queueScroll, queueParams);

        LinearLayout manageRow = horizontalRow();
        undoButton = new Button(this);
        undoButton.setText("DESFAZER");
        undoButton.setOnClickListener(v -> undoLast());
        manageRow.addView(undoButton, smallButtonParams());
        clearQueueButton = new Button(this);
        clearQueueButton.setText("LIMPAR FILA");
        clearQueueButton.setOnClickListener(v -> clearQueue());
        manageRow.addView(clearQueueButton, smallButtonParams());
        root.addView(manageRow, fullWidth());

        TextView nextTitle = text("Próxima da sequência", 16, Color.DKGRAY);
        nextTitle.setGravity(Gravity.CENTER);
        nextTitle.setPadding(0, dp(16), 0, dp(6));
        root.addView(nextTitle, fullWidth());

        nextImage = new ImageView(this);
        nextImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        nextImage.setBackgroundColor(Color.rgb(235, 235, 235));
        root.addView(nextImage, new LinearLayout.LayoutParams(dp(180), dp(180)));

        progressLabel = text("", 16, Color.DKGRAY);
        progressLabel.setGravity(Gravity.CENTER);
        progressLabel.setPadding(0, dp(8), 0, dp(8));
        root.addView(progressLabel, fullWidth());

        nextButton = new Button(this);
        nextButton.setText("MARCAR COMO ENVIADA / PRÓXIMA");
        nextButton.setOnClickListener(v -> markNext());
        root.addView(nextButton, buttonParams());

        Button openWhatsApp = new Button(this);
        openWhatsApp.setText("2. ABRIR WHATSAPP");
        openWhatsApp.setOnClickListener(v -> openWhatsApp());
        root.addView(openWhatsApp, buttonParams());

        TextView note = text(
                "Exemplo: importe 1 figurinha, toque nela para ficar ATIVA e depois toque ×100. " +
                "A fila ficará 100/100 com a mesma figurinha repetida. Se importar várias, use 10/25/50/100 para preencher alternando entre elas.",
                14, Color.GRAY);
        note.setPadding(0, dp(16), 0, dp(16));
        root.addView(note, fullWidth());

        setContentView(scroll);
    }

    private Button multiplierButton(String label, int amount) {
        Button b = new Button(this);
        b.setText(label);
        b.setOnClickListener(v -> appendActive(amount));
        return b;
    }

    private Button fillButton(String label, int target) {
        Button b = new Button(this);
        b.setText(label);
        b.setOnClickListener(v -> fillTo(target));
        return b;
    }

    private void openPicker() {
        if (sources.size() >= MAX_SOURCES) {
            Toast.makeText(this, "Máximo de 30 figurinhas-base.", Toast.LENGTH_SHORT).show();
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
            for (int i = 0; i < clip.getItemCount(); i++) returned.add(clip.getItemAt(i).getUri());
        } else if (data.getData() != null) {
            returned.add(data.getData());
        }

        int added = 0;
        for (Uri uri : returned) {
            if (sources.size() >= MAX_SOURCES) break;
            if (uri == null || sources.contains(uri)) continue;
            persistReadPermission(uri, data);
            sources.add(uri);
            added++;
        }
        if (activeIndex < 0 && !sources.isEmpty()) activeIndex = 0;
        updateAll();
        Toast.makeText(this, added + " figurinha(s)-base adicionada(s).", Toast.LENGTH_SHORT).show();
    }

    private void persistReadPermission(Uri uri, Intent data) {
        try {
            int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            if (flags != 0) getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (Exception ignored) {
        }
    }

    private void appendActive(int amount) {
        if (activeIndex < 0 || activeIndex >= sources.size()) {
            Toast.makeText(this, "Escolha uma figurinha-base primeiro.", Toast.LENGTH_SHORT).show();
            return;
        }
        int capacity = MAX_QUEUE - queue.size();
        if (capacity <= 0) {
            Toast.makeText(this, "A fila já está em 100/100.", Toast.LENGTH_SHORT).show();
            return;
        }
        int actual = Math.min(amount, capacity);
        Uri active = sources.get(activeIndex);
        for (int i = 0; i < actual; i++) queue.add(active);
        updateAll();
        if (actual < amount) Toast.makeText(this, "Fila completada em 100/100.", Toast.LENGTH_SHORT).show();
    }

    private void fillTo(int target) {
        if (sources.isEmpty()) {
            Toast.makeText(this, "Adicione pelo menos uma figurinha-base.", Toast.LENGTH_SHORT).show();
            return;
        }
        target = Math.min(target, MAX_QUEUE);
        while (queue.size() < target) {
            queue.add(sources.get(queue.size() % sources.size()));
        }
        updateAll();
    }

    private void undoLast() {
        if (queue.isEmpty()) return;
        queue.remove(queue.size() - 1);
        if (sentCount > queue.size()) sentCount = queue.size();
        updateAll();
    }

    private void clearQueue() {
        queue.clear();
        sentCount = 0;
        updateAll();
    }

    private void markNext() {
        if (queue.isEmpty()) {
            Toast.makeText(this, "A fila está vazia.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (sentCount >= queue.size()) {
            Toast.makeText(this, "Sequência concluída.", Toast.LENGTH_SHORT).show();
            return;
        }
        sentCount++;
        updateAll();
    }

    private void openWhatsApp() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("com.whatsapp");
        if (launch != null) {
            startActivity(launch);
            return;
        }
        launch = getPackageManager().getLaunchIntentForPackage("com.whatsapp.w4b");
        if (launch != null) {
            startActivity(launch);
            return;
        }
        Toast.makeText(this, "WhatsApp não encontrado.", Toast.LENGTH_LONG).show();
    }

    private void updateAll() {
        sourceCount.setText("Figurinhas-base: " + sources.size() + " / " + MAX_SOURCES);
        activeLabel.setText(activeIndex >= 0 && activeIndex < sources.size()
                ? "ATIVA: figurinha #" + (activeIndex + 1) + " — toque em outra miniatura para trocar"
                : "Nenhuma figurinha ativa");
        queueCount.setText("FILA: " + queue.size() + " / " + MAX_QUEUE);
        progressLabel.setText(queue.isEmpty()
                ? "Monte a fila para começar."
                : "Progresso manual: " + sentCount + " / " + queue.size());
        undoButton.setEnabled(!queue.isEmpty());
        clearQueueButton.setEnabled(!queue.isEmpty());
        nextButton.setEnabled(!queue.isEmpty() && sentCount < queue.size());
        renderSources();
        renderQueue();
        renderNext();
    }

    private void renderSources() {
        sourcesRow.removeAllViews();
        for (int i = 0; i < sources.size(); i++) {
            final int index = i;
            Uri uri = sources.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setPadding(dp(3), dp(3), dp(3), dp(3));

            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(index == activeIndex ? Color.rgb(180, 230, 220) : Color.rgb(230, 230, 230));
            image.setTag(uri.toString());
            image.setOnClickListener(v -> {
                activeIndex = index;
                updateAll();
            });
            card.addView(image, new LinearLayout.LayoutParams(dp(84), dp(84)));

            TextView label = text(index == activeIndex ? "ATIVA #" + (index + 1) : "#" + (index + 1), 12,
                    index == activeIndex ? Color.rgb(7, 94, 84) : Color.DKGRAY);
            label.setGravity(Gravity.CENTER);
            card.addView(label, new LinearLayout.LayoutParams(dp(92), dp(28)));

            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(96), dp(118));
            cp.setMargins(0, 0, dp(6), 0);
            sourcesRow.addView(card, cp);
            loadInto(image, uri, 220);
        }
    }

    private void renderQueue() {
        queueRow.removeAllViews();
        int visible = Math.min(queue.size(), 30);
        for (int i = 0; i < visible; i++) {
            Uri uri = queue.get(i);
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(i < sentCount ? Color.LTGRAY : Color.rgb(235, 235, 235));
            image.setTag(uri.toString() + ":" + i);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(70), dp(70));
            p.setMargins(0, 0, dp(5), 0);
            queueRow.addView(image, p);
            loadInto(image, uri, 160);
        }
        if (queue.size() > visible) {
            TextView more = text("+" + (queue.size() - visible) + "\nmais", 14, Color.DKGRAY);
            more.setGravity(Gravity.CENTER);
            queueRow.addView(more, new LinearLayout.LayoutParams(dp(72), dp(70)));
        }
    }

    private void renderNext() {
        nextImage.setImageDrawable(null);
        if (queue.isEmpty() || sentCount >= queue.size()) {
            nextImage.setBackgroundColor(Color.rgb(235, 235, 235));
            return;
        }
        nextImage.setBackgroundColor(Color.rgb(235, 235, 235));
        loadInto(nextImage, queue.get(sentCount), 420);
    }

    private void loadInto(ImageView view, Uri uri, int size) {
        final String tag = uri.toString() + ":" + System.identityHashCode(view);
        view.setTag(tag);
        executor.execute(() -> {
            Bitmap bmp = loadPreviewBitmap(uri, size);
            if (bmp == null) return;
            runOnUiThread(() -> {
                if (tag.equals(view.getTag())) view.setImageBitmap(bmp);
                else bmp.recycle();
            });
        });
    }

    private Bitmap loadPreviewBitmap(Uri uri, int size) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return getContentResolver().loadThumbnail(uri, new Size(size, size), null);
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            int sample = 1;
            while (bounds.outWidth / sample > size || bounds.outHeight / sample > size) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = Math.max(1, sample);
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(in, null, options);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private TextView text(String value, int sizeSp, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        return t;
    }

    private LinearLayout horizontalRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams p = fullWidth();
        p.setMargins(0, dp(5), 0, dp(5));
        return p;
    }

    private LinearLayout.LayoutParams smallButtonParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(dp(2), dp(2), dp(2), dp(2));
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
