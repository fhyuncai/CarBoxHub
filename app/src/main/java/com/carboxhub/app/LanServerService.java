package com.carboxhub.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.IBinder;

public final class LanServerService extends Service {
    public static final String ACTION_APPLY_WEB_STATE = "com.carboxhub.app.action.APPLY_WEB_STATE";
    public static volatile boolean running = false;
    public static volatile boolean webRunning = false;
    private SimpleHttpServer server;
    private boolean configReceiverRegistered;

    private final BroadcastReceiver configReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null || !AppConfig.ACTION_CONFIG_CHANGED.equals(intent.getAction())) return;
            String key = intent.getStringExtra(AppConfig.EXTRA_KEY);
            if ("web_enabled".equals(key)) applyWebState();
            updateNotification();
        }
    };

    public static void requestApplyWebState(Context context) {
        Intent i = new Intent(context, LanServerService.class).setAction(ACTION_APPLY_WEB_STATE);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i); else context.startService(i);
    }

    @Override public void onCreate() {
        super.onCreate();
        ensureChannel();
        registerReceiver(configReceiver, new IntentFilter(AppConfig.ACTION_CONFIG_CHANGED));
        configReceiverRegistered = true;
        startForeground(7, notification());
        applyWebState();
        PluginRegistry.startEnabled(this);
        running = true;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        applyWebState();
        PluginRegistry.startEnabled(this);
        running = true;
        updateNotification();
        return START_STICKY;
    }

    @Override public void onDestroy() {
        running = false;
        PluginRegistry.stopAll(this);
        stopServer();
        if (configReceiverRegistered) {
            try { unregisterReceiver(configReceiver); } catch (Throwable ignored) {}
            configReceiverRegistered = false;
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private synchronized void applyWebState() {
        if (AppConfig.webEnabled(this)) startServer(); else stopServer();
    }

    private synchronized void startServer() {
        if (server != null) {
            webRunning = true;
            return;
        }
        try {
            server = new SimpleHttpServer(getApplicationContext(), AppConfig.port(this));
            server.start();
            webRunning = true;
        } catch (Throwable t) {
            server = null;
            webRunning = false;
        }
    }

    private synchronized void stopServer() {
        if (server != null) {
            try { server.stop(); } catch (Throwable ignored) {}
            server = null;
        }
        webRunning = false;
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("service", "CarBoxHub 服务", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("CarBoxHub 后台服务");
            ch.setLightColor(0xFF2563EB);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    private void updateNotification() {
        try {
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(7, notification());
        } catch (Throwable ignored) {}
    }

    private Notification notification() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

        int f = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) f |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, 7, i, f);

        String text = AppConfig.webEnabled(this)
                ? "Web：http://" + NetUtil.localIpv4() + ":" + AppConfig.port(this)
                : "Web 管理服务已关闭";
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, "service")
                : new Notification.Builder(this);

        return b
                .setSmallIcon(R.mipmap.ic_notification)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_notification))
                .setColor(0xFF2563EB)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setShowWhen(false)
                .setContentTitle("CarBoxHub 正在运行")
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }
}
