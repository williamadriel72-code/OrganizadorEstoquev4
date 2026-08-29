package com.autoclicker.android;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.nio.ByteBuffer;

public class FruitNinjaCaptureService extends Service {
    public static final String ACTION_STOP = "com.mastertools.fruitguard.STOP_CAPTURE";
    private static final String CHANNEL_ID = "fruit_guard_capture";
    private static final int NOTIFICATION_ID = 5102;

    private final FruitNinjaDetector detector = new FruitNinjaDetector();
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private HandlerThread captureThread;
    private Handler captureHandler;
    private long lastAnalysisAt;

    private int screenWidth;
    private int screenHeight;
    private int densityDpi;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        captureThread = new HandlerThread("FruitGuardCapture");
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            FruitNinjaBus.captureStatus = "Captura parada";
            stopSelf();
            return START_NOT_STICKY;
        }

        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        if (projection != null) {
            FruitNinjaBus.captureRunning = true;
            FruitNinjaBus.captureStatus = "Captura ativa";
            return START_STICKY;
        }

        if (intent == null) {
            FruitNinjaBus.captureStatus = "Permissão de captura ausente";
            stopSelf();
            return START_NOT_STICKY;
        }

        int resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED);
        Intent data = intent.getParcelableExtra("data");
        if (resultCode != Activity.RESULT_OK || data == null) {
            FruitNinjaBus.captureStatus = "Permissão de captura negada";
            stopSelf();
            return START_NOT_STICKY;
        }

        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (manager == null) {
            FruitNinjaBus.captureStatus = "MediaProjection indisponível";
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            projection = manager.getMediaProjection(resultCode, data);
            if (projection == null) {
                FruitNinjaBus.captureStatus = "Falha ao iniciar captura";
                stopSelf();
                return START_NOT_STICKY;
            }

            projection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    FruitNinjaBus.captureRunning = false;
                    FruitNinjaBus.captureStatus = "Captura encerrada pelo Android";
                    cleanupProjection(false);
                    stopSelf();
                }
            }, captureHandler);

            updateDisplayMetrics();
            createVirtualDisplay();
            FruitNinjaBus.captureRunning = true;
            FruitNinjaBus.captureStatus = "Captura ativa • detector em execução";
        } catch (Throwable t) {
            FruitNinjaBus.captureRunning = false;
            FruitNinjaBus.captureStatus = "Erro de captura: " + t.getClass().getSimpleName();
            stopSelf();
        }

        return START_STICKY;
    }

    private void updateDisplayMetrics() {
        DisplayMetrics dm = new DisplayMetrics();
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (wm != null) {
            try {
                wm.getDefaultDisplay().getRealMetrics(dm);
            } catch (Throwable ignored) {
                dm.setTo(getResources().getDisplayMetrics());
            }
        } else {
            dm.setTo(getResources().getDisplayMetrics());
        }
        screenWidth = Math.max(1, dm.widthPixels);
        screenHeight = Math.max(1, dm.heightPixels);
        densityDpi = Math.max(120, dm.densityDpi);
    }

    private void createVirtualDisplay() {
        if (projection == null) return;
        releaseDisplayOnly();

        imageReader = ImageReader.newInstance(
                screenWidth,
                screenHeight,
                PixelFormat.RGBA_8888,
                3);

        imageReader.setOnImageAvailableListener(reader -> {
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image == null) return;

                long now = SystemClock.uptimeMillis();
                if (now - lastAnalysisAt < 38) return;
                lastAnalysisAt = now;

                Image.Plane[] planes = image.getPlanes();
                if (planes == null || planes.length == 0) return;
                Image.Plane plane = planes[0];
                final ByteBuffer buffer = plane.getBuffer();
                final int rowStride = plane.getRowStride();
                final int pixelStride = plane.getPixelStride();
                final int width = image.getWidth();
                final int height = image.getHeight();

                if (buffer == null || pixelStride < 3 || rowStride <= 0) return;

                FruitNinjaDetector.Result result = detector.analyze(
                        new FruitNinjaDetector.PixelSource() {
                            @Override
                            public int getArgb(int x, int y) {
                                int pos = y * rowStride + x * pixelStride;
                                if (pos < 0 || pos + 2 >= buffer.limit()) return 0xff000000;
                                int r = buffer.get(pos) & 0xff;
                                int g = buffer.get(pos + 1) & 0xff;
                                int b = buffer.get(pos + 2) & 0xff;
                                return 0xff000000 | (r << 16) | (g << 8) | b;
                            }
                        },
                        width,
                        height,
                        now);

                FruitNinjaBus.latestResult = result;
                FruitNinjaBus.captureRunning = true;
                FruitNinjaBus.captureStatus =
                        "Captura ativa • " + result.bombs.size() + " bomba(s) • "
                                + result.fruits.size() + " fruta(s) • "
                                + result.analysisMs + " ms";
            } catch (Throwable t) {
                FruitNinjaBus.captureStatus = "Detector: " + t.getClass().getSimpleName();
            } finally {
                if (image != null) {
                    try { image.close(); } catch (Throwable ignored) {}
                }
            }
        }, captureHandler);

        virtualDisplay = projection.createVirtualDisplay(
                "FruitGuardScreen",
                screenWidth,
                screenHeight,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                captureHandler);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (projection == null || captureHandler == null) return;
        captureHandler.postDelayed(() -> {
            try {
                updateDisplayMetrics();
                createVirtualDisplay();
                FruitNinjaBus.captureStatus = "Captura reajustada à rotação";
            } catch (Throwable t) {
                FruitNinjaBus.captureStatus = "Falha ao reajustar captura";
            }
        }, 180);
    }

    private void releaseDisplayOnly() {
        if (virtualDisplay != null) {
            try { virtualDisplay.release(); } catch (Throwable ignored) {}
            virtualDisplay = null;
        }
        if (imageReader != null) {
            try { imageReader.close(); } catch (Throwable ignored) {}
            imageReader = null;
        }
    }

    private void cleanupProjection(boolean stopProjection) {
        FruitNinjaBus.captureRunning = false;
        releaseDisplayOnly();
        if (projection != null) {
            if (stopProjection) {
                try { projection.stop(); } catch (Throwable ignored) {}
            }
            projection = null;
        }
        FruitNinjaBus.latestResult = null;
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, FruitNinjaActivity.class);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, open, pendingFlags);

        Intent stop = new Intent(this, FruitNinjaCaptureService.class);
        stop.setAction(ACTION_STOP);
        PendingIntent stopIntent = PendingIntent.getService(this, 1, stop, pendingFlags);

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        b.setSmallIcon(R.drawable.ic_fruit_guard)
                .setContentTitle("Master Tools Fruit Guard")
                .setContentText("Analisando a tela para evitar bombas")
                .setOngoing(true)
                .setContentIntent(contentIntent)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_media_pause,
                        "Parar",
                        stopIntent).build());

        return b.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                "Captura Fruit Guard",
                NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Captura usada somente para detectar frutas e bombas na tela.");
        nm.createNotificationChannel(ch);
    }

    @Override
    public void onDestroy() {
        cleanupProjection(true);
        FruitNinjaBus.captureStatus = "Captura parada";
        if (captureThread != null) {
            try { captureThread.quitSafely(); } catch (Throwable ignored) {}
            captureThread = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
