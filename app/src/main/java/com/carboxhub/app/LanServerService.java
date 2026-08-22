package com.carboxhub.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.IBinder;

public final class LanServerService extends Service {
    public static volatile boolean running = false;
    private SimpleHttpServer server;
    @Override public void onCreate() { super.onCreate(); ensureChannel(); startForeground(7, notification()); startServer(); PluginRegistry.startEnabled(this); running = true; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) { if (server == null) startServer(); PluginRegistry.startEnabled(this); running = true; return START_STICKY; }
    @Override public void onDestroy() { running = false; PluginRegistry.stopAll(this); if (server != null) { server.stop(); server = null; } super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
    private void startServer() { try { server = new SimpleHttpServer(getApplicationContext(), AppConfig.port(this)); server.start(); } catch (Throwable t) { server = null; } }
    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("service", "CarBoxHub 服务", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("CarBoxHub 局域网管理服务");
            ch.setLightColor(0xFF2563EB);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }
    private Notification notification() {
        Intent i = new Intent(this, MainActivity.class);
        int f = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) f |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, 7, i, f);
        String text = "http://" + NetUtil.localIpv4() + ":" + AppConfig.port(this);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "service") : new Notification.Builder(this);
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
