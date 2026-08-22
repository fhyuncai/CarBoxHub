package com.carboxhub.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;

public final class InstallResultReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        String pkg = intent.getStringExtra("package");

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { context.startActivity(confirm); }
                catch (Throwable t) { showInstallNotification(context, confirm, "需要在车盒屏幕确认安装"); }
            }
            return;
        }

        String text = status == PackageInstaller.STATUS_SUCCESS ? "安装成功: " + pkg : "安装失败: " + (msg == null ? status : msg);
        notifyText(context, text);
    }

    private static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel("install", "CarBoxHub 安装", NotificationManager.IMPORTANCE_HIGH));
        }
    }

    private static void showInstallNotification(Context c, Intent confirm, String text) {
        ensureChannel(c);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(c, 101, confirm, flags);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, "install") : new Notification.Builder(c);
        Notification n = b.setSmallIcon(R.drawable.ic_stat_carbox).setContentTitle("CarBoxHub 安装确认").setContentText(text).setAutoCancel(true).setContentIntent(pi).build();
        ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(101, n);
    }

    private static void notifyText(Context c, String text) {
        ensureChannel(c);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, "install") : new Notification.Builder(c);
        Notification n = b.setSmallIcon(R.drawable.ic_stat_carbox).setContentTitle("CarBoxHub").setContentText(text).setAutoCancel(true).build();
        ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(102, n);
    }
}
