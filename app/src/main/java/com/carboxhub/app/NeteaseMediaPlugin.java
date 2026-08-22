package com.carboxhub.app;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;

public final class NeteaseMediaPlugin implements Plugin {
    @Override public String id() { return "netease_media"; }
    @Override public String name() { return "网易云媒体投送"; }
    @Override public boolean isEnabled(Context c) { return AppConfig.neteaseEnabled(c); }
    @Override public void setEnabled(Context c, boolean enabled) { AppConfig.setNeteaseEnabled(c, enabled); }
    @Override public void start(Context c) { MediaCaptureEngine.get().start(c); }
    @Override public void stop(Context c) { MediaCaptureEngine.get().stop(); }

    @Override public String statusJson(Context c) {
        boolean access = hasNotificationAccess(c);
        NowPlaying n = MediaCaptureEngine.get().current();
        return "{" +
                "\"notificationAccess\":" + access + "," +
                "\"engineStarted\":" + MediaCaptureEngine.get().isStarted() + "," +
                "\"lastError\":" + JsonUtil.q(MediaCaptureEngine.get().lastError()) + "," +
                "\"artworkDataUrl\":" + JsonUtil.q(MediaCaptureEngine.get().artworkDataUrl()) + "," +
                "\"nowPlaying\":" + n.toJson() +
                "}";
    }

    public static boolean hasNotificationAccess(Context c) {
        String flat = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        if (TextUtils.isEmpty(flat)) return false;
        ComponentName me = new ComponentName(c, MediaNotificationListener.class);
        String[] parts = flat.split(":");
        for (String p : parts) {
            ComponentName n = ComponentName.unflattenFromString(p);
            if (me.equals(n)) return true;
        }
        return false;
    }
}
