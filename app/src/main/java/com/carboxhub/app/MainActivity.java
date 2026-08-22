package com.carboxhub.app;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private TextView address;
    private ImageView qr;
    private final TextView[] digits = new TextView[6];
    private String lastUrl = "";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            refresh();
            handler.postDelayed(this, 2500);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(buildUi());
        Intent i = new Intent(this, LanServerService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(ticker);
        refresh();
        handler.postDelayed(ticker, 2500);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    private View buildUi() {
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;
        int hp = clampPx(w / 24, dp(18), dp(44));
        int vp = clampPx(h / 30, dp(10), dp(24));
        int titleSp = spPx(clampPx((int) (h * 0.075f), dp(22), dp(34)));
        int labelSp = spPx(clampPx((int) (h * 0.037f), dp(12), dp(17)));
        int addressSp = spPx(clampPx((int) (h * 0.050f), dp(15), dp(23)));
        int tokenSp = spPx(clampPx((int) (h * 0.074f), dp(22), dp(34)));
        int hintSp = spPx(clampPx((int) (h * 0.032f), dp(10), dp(14)));
        int qrSide = clampPx((int) (h * 0.46f), dp(128), dp(220));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(hp, vp, hp, vp);
        root.setBackgroundColor(Color.rgb(64, 66, 70));

        TextView title = tv("CarBoxHub 车机互联助手", titleSp, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Space titleGap = new Space(this);
        root.addView(titleGap, new LinearLayout.LayoutParams(1, 0, 0.10f));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setGravity(Gravity.CENTER);
        root.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER_HORIZONTAL);
        body.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.15f));

        TextView addressLabel = tv("访问地址", labelSp, Color.rgb(230, 230, 230));
        addressLabel.setGravity(Gravity.CENTER);
        left.addView(addressLabel);

        address = tv("等待连接局域网", addressSp, Color.WHITE);
        address.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        address.setGravity(Gravity.CENTER);
        address.setMaxLines(2);
        address.setEllipsize(TextUtils.TruncateAt.END);
        address.setPadding(0, dp(6), 0, 0);
        address.setOnClickListener(v -> copyUrl());
        left.addView(address, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView tokenLabel = tv("访问令牌", labelSp, Color.rgb(230, 230, 230));
        tokenLabel.setGravity(Gravity.CENTER);
        tokenLabel.setPadding(0, dp(15), 0, 0);
        left.addView(tokenLabel);

        LinearLayout tokenRow = new LinearLayout(this);
        tokenRow.setOrientation(LinearLayout.HORIZONTAL);
        tokenRow.setGravity(Gravity.CENTER);
        tokenRow.setPadding(0, dp(8), 0, 0);
        left.addView(tokenRow);

        int digitW = clampPx(w / 15, dp(36), dp(54));
        int digitH = clampPx(h / 6, dp(42), dp(64));
        int digitGap = clampPx(w / 100, dp(5), dp(12));
        for (int n = 0; n < 6; n++) {
            TextView d = tv("0", tokenSp, Color.WHITE);
            d.setGravity(Gravity.CENTER);
            d.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            d.setBackground(codeBg());
            d.setOnClickListener(v -> copyToken());
            digits[n] = d;
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(digitW, digitH);
            if (n > 0) p.leftMargin = digitGap;
            tokenRow.addView(d, p);
        }

        TextView leftHint = tv("点击地址复制链接 · 点击令牌复制验证码", hintSp, Color.rgb(215, 215, 215));
        leftHint.setGravity(Gravity.CENTER);
        leftHint.setPadding(0, dp(10), 0, 0);
        left.addView(leftHint);

        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(110, 112, 116));
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(dp(1), Math.max(qrSide, dp(150)));
        dividerLp.leftMargin = dp(20);
        dividerLp.rightMargin = dp(20);
        body.addView(divider, dividerLp);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.CENTER_HORIZONTAL);
        body.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.82f));

        qr = new ImageView(this);
        qr.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        qr.setPadding(dp(10), dp(10), dp(10), dp(10));
        GradientDrawable white = new GradientDrawable();
        white.setColor(Color.WHITE);
        white.setCornerRadius(dp(6));
        qr.setBackground(white);
        qr.setOnClickListener(v -> showQr());
        right.addView(qr, new LinearLayout.LayoutParams(qrSide, qrSide));

        TextView qrHint = tv("扫码进入管理面板\n点击二维码可放大", labelSp, Color.WHITE);
        qrHint.setGravity(Gravity.CENTER);
        qrHint.setPadding(0, dp(10), 0, 0);
        right.addView(qrHint);

        Space bottomGap = new Space(this);
        root.addView(bottomGap, new LinearLayout.LayoutParams(1, 0, 0.06f));

        Button config = button("配置");
        config.setTextSize(Math.max(14, labelSp));
        config.setTextColor(Color.WHITE);
        config.setBackground(buttonBg());
        config.setPadding(dp(30), dp(10), dp(30), dp(10));
        config.setOnClickListener(v -> showSettings());
        LinearLayout.LayoutParams configLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        configLp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(config, configLp);

        return root;
    }

    private void refresh() {
        String ip = NetUtil.localIpv4();
        String token = AppConfig.token(this);
        String url = "http://" + ip + ":" + AppConfig.port(this) + "/?token=" + token;
        address.setText("0.0.0.0".equals(ip) ? "等待连接局域网" : url);
        for (int i = 0; i < 6; i++) digits[i].setText(String.valueOf(token.charAt(i)));
        if ("0.0.0.0".equals(ip)) {
            qr.setImageDrawable(null);
            lastUrl = "";
            return;
        }
        if (!url.equals(lastUrl)) {
            int side = clampPx((int) (getResources().getDisplayMetrics().heightPixels * 0.43f), dp(120), dp(210));
            qr.setImageBitmap(QrBitmap.create(url, side));
            lastUrl = url;
        }
    }

    private void showQr() {
        String ip = NetUtil.localIpv4();
        if ("0.0.0.0".equals(ip)) {
            toast("当前没有可用的局域网地址");
            return;
        }
        String url = "http://" + ip + ":" + AppConfig.port(this) + "/?token=" + AppConfig.token(this);
        Dialog d = new Dialog(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(18), dp(18), dp(18), dp(18));
        box.setBackgroundColor(Color.WHITE);
        int size = Math.max(dp(260), (int) (Math.min(getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels) * 0.70f));
        ImageView image = new ImageView(this);
        image.setImageBitmap(QrBitmap.create(url, size));
        box.addView(image, new LinearLayout.LayoutParams(size, size));
        TextView u = tv(url, 12, Color.rgb(51, 65, 85));
        u.setGravity(Gravity.CENTER);
        box.addView(u);
        box.setOnClickListener(v -> d.dismiss());
        d.setContentView(box);
        d.show();
    }

    private void showSettings() {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(18), dp(14), dp(18), dp(14));
        shell.setBackgroundColor(Color.rgb(37, 39, 43));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = tv("CarBoxHub 配置", 22, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button close = button("返回");
        close.setOnClickListener(v -> d.dismiss());
        header.addView(close);
        shell.addView(header);

        TextView hint = tv("权限、安装方式、插件与启动设置", 13, Color.rgb(180, 184, 190));
        hint.setPadding(0, dp(4), 0, dp(8));
        shell.addView(hint);

        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(4), 0, dp(8));
        scroll.addView(box);
        shell.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button reset = button("重置 6 位访问令牌");
        reset.setOnClickListener(v -> {
            AppConfig.regenerateToken(this);
            refresh();
            toast("令牌已更新");
        });
        box.addView(reset);

        Button notif = button("通知使用权");
        notif.setOnClickListener(v -> startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));
        box.addView(notif);

        Button unknown = button("安装未知应用权限");
        unknown.setOnClickListener(v -> openUnknown());
        box.addView(unknown);

        Button storage = button("公共下载目录权限");
        storage.setOnClickListener(v -> requestStorage());
        box.addView(storage);

        CheckBox media = check("启用网易云媒体投送插件", AppConfig.neteaseEnabled(this));
        media.setOnCheckedChangeListener((v, c) -> {
            AppConfig.setNeteaseEnabled(this, c);
            if (c) MediaCaptureEngine.get().start(this); else MediaCaptureEngine.get().stop();
        });
        box.addView(media);

        CheckBox root = check("有 root 时优先静默安装 APK", AppConfig.rootInstall(this));
        root.setOnCheckedChangeListener((v, c) -> AppConfig.setRootInstall(this, c));
        box.addView(root);

        CheckBox boot = check("开机自动启动 CarBoxHub", AppConfig.autoStart(this));
        boot.setOnCheckedChangeListener((v, c) -> AppConfig.setAutoStart(this, c));
        box.addView(boot);

        boolean access = NeteaseMediaPlugin.hasNotificationAccess(this);
        boolean install = Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls();
        String rootState = AppConfig.rootInstall(this) ? (RootShell.isAvailable() ? "可用" : "无 / 未授权") : "未启用";
        TextView state = tv(
                "通知使用权：" + (access ? "已授权" : "未授权") +
                        "\nAPK 安装权限：" + (install ? "可用" : "未允许") +
                        "\nRoot：" + rootState +
                        "\n文件目录：" + StorageUtil.uploadDir(this).getAbsolutePath(),
                14,
                Color.rgb(220, 223, 228));
        state.setPadding(0, dp(12), 0, 0);
        box.addView(state);

        NowPlaying n = MediaCaptureEngine.get().current();
        TextView mediaState = tv(
                "\n当前媒体：" + (n.title.isEmpty() ? "暂无" : n.title) +
                        (n.artist.isEmpty() ? "" : " / " + n.artist) +
                        "\n播放状态：" + (n.playing ? "播放中" : "暂停 / 空闲"),
                14,
                Color.rgb(180, 184, 190));
        box.addView(mediaState);

        d.setContentView(shell);
        d.setOnDismissListener(x -> refresh());
        d.show();
        Window window = d.getWindow();
        if (window != null) window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private void copyUrl() {
        String ip = NetUtil.localIpv4();
        if ("0.0.0.0".equals(ip)) {
            toast("当前没有可用地址");
            return;
        }
        copy("CarBoxHub URL", "http://" + ip + ":" + AppConfig.port(this) + "/?token=" + AppConfig.token(this));
        toast("地址已复制");
    }

    private void copyToken() {
        copy("CarBoxHub Token", AppConfig.token(this));
        toast("令牌已复制");
    }

    private void copy(String label, String value) {
        ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(label, value));
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private void requestStorage() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 200);
        } else {
            toast("公共存储权限已可用");
        }
    }

    private void openUnknown() {
        if (Build.VERSION.SDK_INT >= 26) {
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
        } else {
            toast("Android 8 以下一般无需单独开启");
        }
    }

    private GradientDrawable codeBg() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.rgb(140, 140, 140));
        g.setCornerRadius(dp(4));
        g.setStroke(dp(1), Color.rgb(190, 190, 190));
        return g;
    }

    private GradientDrawable buttonBg() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.rgb(107, 109, 113));
        g.setCornerRadius(dp(28));
        return g;
    }

    private TextView tv(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        return b;
    }

    private CheckBox check(String s, boolean value) {
        CheckBox c = new CheckBox(this);
        c.setText(s);
        c.setTextColor(Color.WHITE);
        c.setChecked(value);
        c.setPadding(0, dp(5), 0, dp(5));
        return c;
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    private int spPx(int px) {
        return Math.max(10, Math.round(px / getResources().getDisplayMetrics().scaledDensity));
    }

    private int clampPx(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
