package com.carboxhub.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private TextView status;
    private TextView nowPlaying;
    private CheckBox plugin;
    private CheckBox rootInstall;
    private CheckBox autoStart;
    private ImageView qrView;
    private TextView qrHint;
    private String lastQrUrl = "";
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshTask = new Runnable() {
        @Override public void run() { refresh(); refreshHandler.postDelayed(this, 2500); }
    };

    @Override protected void onCreate(Bundle b) { super.onCreate(b); setContentView(buildUi()); startLanService(); }
    @Override protected void onResume() { super.onResume(); refreshHandler.removeCallbacks(refreshTask); refresh(); refreshHandler.postDelayed(refreshTask, 2500); }
    @Override protected void onPause() { refreshHandler.removeCallbacks(refreshTask); super.onPause(); }

    private View buildUi() {
        ScrollView sc = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(28), dp(22), dp(28), dp(22)); root.setBackgroundColor(Color.rgb(15,23,42)); sc.addView(root);
        TextView title = text("CarBoxHub", 30, Color.WHITE); root.addView(title);
        TextView sub = text("7862 车盒 · 局域网文件传送 / APK 安装 / 网易云媒体桥接", 15, Color.rgb(148,163,184)); root.addView(sub);
        status = text("", 18, Color.WHITE); status.setPadding(0,dp(18),0,dp(12)); root.addView(status);
        TextView qrTitle = text("手机扫码进入管理面板", 18, Color.WHITE); root.addView(qrTitle);
        qrHint = text("二维码包含当前局域网地址和访问 Token · 点击二维码可放大", 14, Color.rgb(148,163,184)); qrHint.setPadding(0, dp(4), 0, dp(8)); root.addView(qrHint);
        qrView = new ImageView(this); qrView.setBackgroundColor(Color.WHITE); qrView.setAdjustViewBounds(true); qrView.setScaleType(ImageView.ScaleType.CENTER);
        LinearLayout.LayoutParams qrLp = new LinearLayout.LayoutParams(dp(240), dp(240)); qrLp.gravity = Gravity.CENTER_HORIZONTAL; qrLp.setMargins(0, 0, 0, dp(14)); qrView.setLayoutParams(qrLp); qrView.setContentDescription("CarBoxHub Web 管理面板二维码"); qrView.setOnClickListener(v -> showLargeQr()); root.addView(qrView);
        LinearLayout buttons = new LinearLayout(this); buttons.setOrientation(LinearLayout.HORIZONTAL); root.addView(buttons);
        Button copy = button("复制 Web 地址"); buttons.addView(copy); copy.setOnClickListener(v -> copyUrl());
        Button notif = button("授予通知使用权"); buttons.addView(notif); notif.setOnClickListener(v -> startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));
        Button unknown = button("允许安装未知应用"); buttons.addView(unknown); unknown.setOnClickListener(v -> openUnknownSources());
        Button storage = button("允许公共下载目录"); buttons.addView(storage); storage.setOnClickListener(v -> requestStorage());
        plugin = check("启用：网易云媒体投送插件"); root.addView(plugin);
        plugin.setOnCheckedChangeListener((v, checked) -> { AppConfig.setNeteaseEnabled(this, checked); if (checked) MediaCaptureEngine.get().start(this); else MediaCaptureEngine.get().stop(); refresh(); });
        rootInstall = check("有 root 时优先静默安装 APK"); root.addView(rootInstall); rootInstall.setOnCheckedChangeListener((v, checked) -> AppConfig.setRootInstall(this, checked));
        autoStart = check("开机自动启动 CarBoxHub"); root.addView(autoStart); autoStart.setOnCheckedChangeListener((v, checked) -> AppConfig.setAutoStart(this, checked));
        TextView ptitle = text("当前网易云媒体", 18, Color.WHITE); ptitle.setPadding(0,dp(18),0,0); root.addView(ptitle);
        nowPlaying = text("暂无", 20, Color.rgb(226,232,240)); root.addView(nowPlaying);
        TextView note = text("说明：CarBoxHub 会把网易云车机版的 MediaSession/通知媒体信息复制到一个代理 MediaSession。若嘟嘟梁山车盒的 CarPlay 桥接层会读取 Android 当前媒体信息，原车机即可显示歌名/歌手/播放状态并回传上一曲、下一曲、播放/暂停。若厂商桥接层不读取标准 MediaSession，需要再针对其私有服务做适配。", 14, Color.rgb(148,163,184)); note.setPadding(0,dp(18),0,0); root.addView(note);
        return sc;
    }

    private void startLanService() { Intent i = new Intent(this, LanServerService.class); if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i); }
    private void refresh() {
        String ip = NetUtil.localIpv4(); String url = "http://" + ip + ":" + AppConfig.port(this) + "/?token=" + AppConfig.token(this); updateQr(url, ip);
        boolean access = NeteaseMediaPlugin.hasNotificationAccess(this); boolean install = Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls();
        status.setText("Web：" + url + "\n通知使用权：" + (access ? "已授权" : "未授权") + "    APK 安装权限：" + (install ? "可用" : "未允许") + "    Root：" + (AppConfig.rootInstall(this) ? (RootShell.isAvailable() ? "可用" : "无/未授权") : "未启用") + "\n文件目录：" + StorageUtil.uploadDir(this).getAbsolutePath());
        setCheck(plugin, AppConfig.neteaseEnabled(this)); setCheck(rootInstall, AppConfig.rootInstall(this)); setCheck(autoStart, AppConfig.autoStart(this));
        NowPlaying n = MediaCaptureEngine.get().current(); nowPlaying.setText((n.title.isEmpty() ? "暂无媒体" : n.title) + (n.artist.isEmpty() ? "" : "\n" + n.artist) + (n.album.isEmpty() ? "" : " · " + n.album) + "\n状态：" + (n.playing ? "播放中" : "暂停/空闲") + (MediaCaptureEngine.get().lastError().isEmpty() ? "" : "\n" + MediaCaptureEngine.get().lastError()));
    }
    private void setCheck(CheckBox b, boolean v) { if (b.isChecked() != v) b.setChecked(v); }
    private void copyUrl() { String url = "http://" + NetUtil.localIpv4() + ":" + AppConfig.port(this) + "/?token=" + AppConfig.token(this); ((ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("CarBoxHub", url)); Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show(); }
    private void updateQr(String url, String ip) {
        if (qrView == null) return;
        if ("0.0.0.0".equals(ip)) { qrView.setImageDrawable(null); qrHint.setText("等待车盒连接 Wi-Fi / 热点后生成二维码"); lastQrUrl = ""; return; }
        qrHint.setText("二维码包含当前局域网地址和访问 Token · 点击二维码可放大"); if (url.equals(lastQrUrl)) return;
        try { qrView.setImageBitmap(QrBitmap.create(url, dp(240))); lastQrUrl = url; } catch (Throwable t) { qrView.setImageDrawable(null); qrHint.setText("二维码生成失败：" + t.getMessage()); lastQrUrl = ""; }
    }
    private void showLargeQr() {
        String ip = NetUtil.localIpv4(); if ("0.0.0.0".equals(ip)) { Toast.makeText(this, "车盒还没有可用的局域网 IP", Toast.LENGTH_SHORT).show(); return; }
        String url = "http://" + ip + ":" + AppConfig.port(this) + "/?token=" + AppConfig.token(this);
        int target = Math.max(240, (int)(Math.min(getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels) * 0.68f));
        Dialog dialog = new Dialog(this); LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setGravity(Gravity.CENTER); panel.setPadding(dp(18), dp(18), dp(18), dp(18)); panel.setBackgroundColor(Color.WHITE);
        ImageView image = new ImageView(this); image.setImageBitmap(QrBitmap.create(url, target)); image.setAdjustViewBounds(true); image.setScaleType(ImageView.ScaleType.CENTER); panel.addView(image, new LinearLayout.LayoutParams(target, target));
        TextView tip = text("手机连接同一局域网后扫码打开 CarBoxHub", 16, Color.rgb(15,23,42)); tip.setGravity(Gravity.CENTER); tip.setPadding(0, dp(8), 0, dp(4)); panel.addView(tip);
        TextView address = text(url, 12, Color.rgb(71,85,105)); address.setGravity(Gravity.CENTER); panel.addView(address); panel.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(panel); dialog.setCanceledOnTouchOutside(true); dialog.show();
    }
    private void requestStorage() { if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 200); else { Toast.makeText(this, "公共存储权限已可用", Toast.LENGTH_SHORT).show(); refresh(); } }
    private void openUnknownSources() { if (Build.VERSION.SDK_INT >= 26) startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()))); else startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS)); }
    private TextView text(String s, int sp, int color) { TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(color); v.setGravity(Gravity.START); return v; }
    private Button button(String s) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(50), 1); lp.setMargins(0,0,dp(10),dp(12)); b.setLayoutParams(lp); return b; }
    private CheckBox check(String s) { CheckBox c = new CheckBox(this); c.setText(s); c.setTextColor(Color.WHITE); c.setTextSize(16); c.setPadding(0,dp(8),0,dp(8)); return c; }
    private int dp(int x) { return Math.round(x * getResources().getDisplayMetrics().density); }
}
