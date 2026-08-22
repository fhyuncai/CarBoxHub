package com.carboxhub.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;

public final class MediaSessionCarSink {
    private final Context context;
    private MediaSession session;
    private volatile MediaController source;
    public MediaSessionCarSink(Context context) { this.context = context.getApplicationContext(); }
    public void start() {
        if (session != null) return;
        session = new MediaSession(context, "CarBoxHub-NetEase-Proxy");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { transport(0); }
            @Override public void onPause() { transport(1); }
            @Override public void onSkipToNext() { transport(2); }
            @Override public void onSkipToPrevious() { transport(3); }
            @Override public void onSeekTo(long pos) { MediaController c = source; if (c != null) try { c.getTransportControls().seekTo(pos); } catch (Throwable ignored) {} }
        });
        session.setActive(true);
    }
    public void stop() { if (session != null) { try { session.setActive(false); session.release(); } catch (Throwable ignored) {} session = null; } source = null; }
    public void publish(NowPlaying x, MediaController sourceController) {
        if (session == null) start();
        if (sourceController != null) source = sourceController;
        if (session == null) return;
        MediaMetadata.Builder mb = new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, x.title).putString(MediaMetadata.METADATA_KEY_ARTIST, x.artist).putString(MediaMetadata.METADATA_KEY_ALBUM, x.album).putLong(MediaMetadata.METADATA_KEY_DURATION, Math.max(0, x.durationMs));
        if (sourceController != null && sourceController.getMetadata() != null) {
            try { Bitmap art = sourceController.getMetadata().getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART); if (art == null) art = sourceController.getMetadata().getBitmap(MediaMetadata.METADATA_KEY_ART); if (art != null) mb.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art); } catch (Throwable ignored) {}
        }
        session.setMetadata(mb.build());
        int state = x.playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO;
        PlaybackState ps = new PlaybackState.Builder().setActions(actions).setState(state, Math.max(0, x.positionMs), x.playing ? 1f : 0f).build();
        session.setPlaybackState(ps);
        Bundle extras = new Bundle(); extras.putString("carboxhub.source_package", x.sourcePackage); session.setExtras(extras); session.setActive(true);
    }
    private void transport(int what) {
        MediaController c = source; if (c == null) return;
        try { switch (what) { case 0: c.getTransportControls().play(); break; case 1: c.getTransportControls().pause(); break; case 2: c.getTransportControls().skipToNext(); break; case 3: c.getTransportControls().skipToPrevious(); break; } } catch (Throwable ignored) {}
    }
}
