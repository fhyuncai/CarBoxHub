package com.carboxhub.app;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MediaCaptureEngine {
    private static final MediaCaptureEngine INSTANCE = new MediaCaptureEngine();
    private static final Set<String> TARGETS = new HashSet<>(Arrays.asList("com.netease.cloudmusic.iot", "com.netease.cloudmusic"));
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayList<MediaController> controllers = new ArrayList<>();
    private Context app; private MediaSessionManager manager; private MediaSessionCarSink sink;
    private volatile NowPlaying now = new NowPlaying(); private volatile String lastError = ""; private boolean started;
    private final MediaSessionManager.OnActiveSessionsChangedListener activeListener = new MediaSessionManager.OnActiveSessionsChangedListener() { @Override public void onActiveSessionsChanged(List<MediaController> list) { bindControllers(list); } };
    private final MediaController.Callback controllerCallback = new MediaController.Callback() { @Override public void onMetadataChanged(MediaMetadata metadata) { refreshFromBound(); } @Override public void onPlaybackStateChanged(PlaybackState state) { refreshFromBound(); } };
    private MediaCaptureEngine() {}
    public static MediaCaptureEngine get() { return INSTANCE; }
    public synchronized void start(Context c) {
        if (started) return;
        app = c.getApplicationContext(); manager = (MediaSessionManager) app.getSystemService(Context.MEDIA_SESSION_SERVICE); sink = new MediaSessionCarSink(app);
        ComponentName listener = new ComponentName(app, MediaNotificationListener.class);
        try { manager.addOnActiveSessionsChangedListener(activeListener, listener, main); bindControllers(manager.getActiveSessions(listener)); lastError = ""; }
        catch (SecurityException se) { lastError = "请授予通知使用权后重试"; }
        catch (Throwable t) { lastError = t.toString(); }
        started = true;
    }
    public synchronized void stop() { if (!started) return; try { manager.removeOnActiveSessionsChangedListener(activeListener); } catch (Throwable ignored) {} clearControllers(); if (sink != null) sink.stop(); started = false; }
    public synchronized void notificationListenerConnected(Context c) { if (!started) start(c); refreshSessions(); }
    public synchronized void refreshSessions() { if (app == null) return; try { ComponentName listener = new ComponentName(app, MediaNotificationListener.class); bindControllers(manager.getActiveSessions(listener)); lastError = ""; } catch (Throwable t) { lastError = t.toString(); } }
    public void onNotification(StatusBarNotification sbn) {
        if (sbn == null || !TARGETS.contains(sbn.getPackageName())) return; Notification n = sbn.getNotification(); if (n == null) return; Bundle e = n.extras; if (e == null) return;
        CharSequence title = e.getCharSequence(Notification.EXTRA_TITLE); CharSequence text = e.getCharSequence(Notification.EXTRA_TEXT);
        if (!TextUtils.isEmpty(title) || !TextUtils.isEmpty(text)) { NowPlaying x = cloneNow(); x.sourcePackage = sbn.getPackageName(); if (!TextUtils.isEmpty(title)) x.title = title.toString(); if (!TextUtils.isEmpty(text)) x.artist = text.toString(); x.updatedAt = System.currentTimeMillis(); publish(x, null); }
        refreshSessions();
    }
    private synchronized void bindControllers(List<MediaController> list) { clearControllers(); if (list != null) for (MediaController c : list) if (c != null && TARGETS.contains(c.getPackageName())) { controllers.add(c); try { c.registerCallback(controllerCallback, main); } catch (Throwable ignored) {} } refreshFromBound(); }
    private synchronized void clearControllers() { for (MediaController c : controllers) try { c.unregisterCallback(controllerCallback); } catch (Throwable ignored) {} controllers.clear(); }
    private synchronized MediaController bestController() { MediaController fallback = null; for (MediaController c : controllers) { if (fallback == null) fallback = c; PlaybackState s = c.getPlaybackState(); if (s != null && s.getState() == PlaybackState.STATE_PLAYING) return c; } return fallback; }
    private void refreshFromBound() {
        MediaController c; synchronized (this) { c = bestController(); } if (c == null) return;
        NowPlaying x = new NowPlaying(); x.sourcePackage = c.getPackageName(); MediaMetadata m = c.getMetadata();
        if (m != null) { x.title = str(m, MediaMetadata.METADATA_KEY_TITLE); if (TextUtils.isEmpty(x.title)) x.title = str(m, MediaMetadata.METADATA_KEY_DISPLAY_TITLE); x.artist = str(m, MediaMetadata.METADATA_KEY_ARTIST); if (TextUtils.isEmpty(x.artist)) x.artist = str(m, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE); x.album = str(m, MediaMetadata.METADATA_KEY_ALBUM); x.durationMs = m.getLong(MediaMetadata.METADATA_KEY_DURATION); }
        PlaybackState s = c.getPlaybackState(); if (s != null) { x.positionMs = s.getPosition(); x.playing = s.getState() == PlaybackState.STATE_PLAYING || s.getState() == PlaybackState.STATE_BUFFERING; }
        x.updatedAt = System.currentTimeMillis(); publish(x, c);
    }
    private static String str(MediaMetadata m, String key) { String s = m.getString(key); return s == null ? "" : s; }
    private synchronized NowPlaying cloneNow() { NowPlaying x = new NowPlaying(); x.sourcePackage = now.sourcePackage; x.title = now.title; x.artist = now.artist; x.album = now.album; x.durationMs = now.durationMs; x.positionMs = now.positionMs; x.playing = now.playing; x.updatedAt = now.updatedAt; return x; }
    private void publish(NowPlaying x, MediaController source) { now = x; MediaSessionCarSink s = sink; if (s != null) s.publish(x, source); }
    public NowPlaying current() { return cloneNow(); }
    public String lastError() { return lastError; }
    public boolean isStarted() { return started; }
}
