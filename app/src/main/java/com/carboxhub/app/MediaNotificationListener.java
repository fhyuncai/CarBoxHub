package com.carboxhub.app;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public final class MediaNotificationListener extends NotificationListenerService {
    @Override public void onListenerConnected() {
        super.onListenerConnected();
        if (AppConfig.neteaseEnabled(this)) MediaCaptureEngine.get().notificationListenerConnected(this);
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (AppConfig.neteaseEnabled(this)) MediaCaptureEngine.get().onNotification(sbn);
    }
}
