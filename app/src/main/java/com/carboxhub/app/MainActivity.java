package com.carboxhub.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private TextView address;
    private ImageView qr;
    private final TextView[] digits = new TextView[6];
    private String lastUrl = "";
    private boolean renderedWebEnabled;
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
        int hp = clampPx(w / 30, dp(18), dp(38));
        int vp = clampPx(h / 45, dp(8), dp(16));
        int titleSp = spPx(clampPx((int) (h * 0.060f), dp(20), dp(30)));
        int labelSp = spPx(clampPx((int) (h * 0.032f), dp(11), dp(15)));
        int addressSp = spPx(clampPx((int) (h * 0.040f), dp(14), dp(20)));
        int tokenSp = spPx(clampPx((int) (h * 0.060f), dp(20), dp(30)));
        int hintSp = spPx(clampPx((int) (h * 0.026f), dp(9), dp(12)));
        int qrSide = clampPx((int) (h * 0.37f), dp(126), dp(198));

        renderedWebEnabled = AppConfig.webEnabled(this);
        address = null;
        qr = null;
        lastUrl = "";

        FrameLayout shell = new FrameLayout(this);
        shell.setBackgroundColor(Color.rgb(64, 66, 70));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(hp, vp, hp, vp);
        shell.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = tv("CarBoxHub 车机互联助手", titleSp, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addSettingsButton(shell);

        Space gap = new Space(this);
        root.addView(gap, new LinearLayout.LayoutParams(1, dp(6)));

        if (!renderedWebEnabled) {
            buildWebDisabledState(root, titleSp, labelSp, hintSp);
            return shell;
        }

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        body.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.20f));

        TextView addressLabel = tv("访问地址", labelSp, Color.rgb(230, 230, 230));
        addressLabel.setGravity(Gravity.CENTER);
        left.addView(addressLabel);

        address = tv("等待连接局域网", addressSp, Color.WHITE);
        address.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        address.setGravity(Gravity.CENTER);
        address.setSingleLine(true);
        address.setEllipsize(TextUtils.TruncateAt.END);
        address.setPadding(0, dp(5), 0, 0);
        address.setOnClickListener(v -> copyBaseUrl());
        left.addView(address, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView tokenLabel = tv("访问令牌", labelSp, Color.rgb(230, 230, 230));
        tokenLabel.setGravity(Gravity.CENTER);
        tokenLabel.setPadding(0, dp(12), 0, 0);
        left.addView(tokenLabel);

        LinearLayout tokenRow = new LinearLayout(this);
        tokenRow.setOrientation(LinearLayout.HORIZONTAL);
        tokenRow.setGravity(Gravity.CENTER);
        tokenRow.setPadding(0, dp(6), 0, 0);
        left.addView(tokenRow);

        int digitW = clampPx(w / 18, dp(34), dp(48));
        int digitH = clampPx(h / 8, dp(40), dp(56));
        int digitGap = clampPx(w / 120, dp(4), dp(9));
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

        TextView leftHint = tv("点击地址复制 · 点击令牌复制验证码", hintSp, Color.rgb(210, 210, 210));
        leftHint.setGravity(Gravity.CENTER);
        leftHint.setPadding(0, dp(8), 0, 0);
        left.addView(leftHint);

        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(105, 107, 111));
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(dp(1), clampPx((int) (h * 0.46f), dp(150), dp(230)));
        dividerLp.leftMargin = dp(16);
        dividerLp.rightMargin = dp(16);
        body.addView(divider, dividerLp);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        body.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.80f));

        qr = new ImageView(this);
        qr.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        qr.setPadding(dp(8), dp(8), dp(8), dp(8));
        GradientDrawable white = new GradientDrawable();
        white.setColor(Color.WHITE);
        white.setCornerRadius(dp(6));
        qr.setBackground(white);
        qr.setOnClickListener(v -> showQr());
        right.addView(qr, new LinearLayout.LayoutParams(qrSide, qrSide));

        TextView qrHint = tv("扫码进入管理面板", labelSp, Color.WHITE);
        qrHint.setGravity(Gravity.CENTER);
        qrHint.setPadding(0, dp(8), 0, 0);
        right.addView(qrHint);

        return shell;
    }

    private void buildWebDisabledState(LinearLayout root, int titleSp, int labelSp, int hintSp) {
        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        root.addView(center, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView state = tv("Web 管理服务已关闭", Math.max(labelSp + 4, titleSp - 4), Color.WHITE);
        state.setTypeface(Typeface.DEFAULT_BOLD);
        state.setGravity(Gravity.CENTER);
        center.addView(state);

        TextView hint = tv("局域网管理页面当前不可访问", hintSp + 1, Color.rgb(205, 207, 212));
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(7), 0, dp(18));
        center.addView(hint);

        TextView enable = tv("开启 Web 服务", labelSp + 2, Color.WHITE);
        enable.setTypeface(Typeface.DEFAULT_BOLD);
        enable.setGravity(Gravity.CENTER);
        enable.setPadding(dp(28), dp(11), dp(28), dp(11));
        enable.setBackground(primaryButtonBg());
        enable.setOnClickListener(v -> enableWebService());
        center.addView(enable, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addSettingsButton(FrameLayout shell) {
        ImageButton settings = new ImageButton(this);
        settings.setImageResource(R.drawable.ic_settings_gear);
        settings.setBackgroundColor(Color.TRANSPARENT);
        settings.setPadding(dp(10), dp(10), dp(10), dp(10));
        settings.setContentDescription("配置");
        settings.setOnClickListener(v -> startActivity(new Intent(this, ConfigActivity.class)));
        FrameLayout.LayoutParams settingsLp = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.START);
        settingsLp.leftMargin = dp(8);
        settingsLp.topMargin = dp(4);
        shell.addView(settings, settingsLp);
    }

    private void enableWebService() {
        AppConfig.setWebEnabled(this, true);
        LanServerService.requestApplyWebState(this);
        setContentView(buildUi());
        refresh();
        toast("Web 服务已开启");
    }

    private void refresh() {
        boolean enabled = AppConfig.webEnabled(this);
        if (enabled != renderedWebEnabled) {
            setContentView(buildUi());
            if (!enabled) return;
        }
        if (!enabled || address == null || qr == null) return;

        String ip = NetUtil.localIpv4();
        String token = AppConfig.token(this);
        String baseUrl = "http://" + ip + ":" + AppConfig.port(this);
        String fullUrl = baseUrl + "/?token=" + token;
        address.setText("0.0.0.0".equals(ip) ? "等待连接局域网" : baseUrl);
        for (int i = 0; i < 6; i++) digits[i].setText(String.valueOf(token.charAt(i)));
        if ("0.0.0.0".equals(ip)) {
            qr.setImageDrawable(null);
            lastUrl = "";
            return;
        }
        if (!fullUrl.equals(lastUrl)) {
            int side = clampPx((int) (getResources().getDisplayMetrics().heightPixels * 0.34f), dp(118), dp(184));
            qr.setImageBitmap(QrBitmap.create(fullUrl, side));
            lastUrl = fullUrl;
        }
    }

    private void showQr() {
        if (!AppConfig.webEnabled(this)) {
            toast("请先开启 Web 服务");
            return;
        }
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
        box.setPadding(dp(16), dp(16), dp(16), dp(16));
        box.setBackgroundColor(Color.WHITE);
        int size = Math.max(dp(240), (int) (Math.min(getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels) * 0.66f));
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

    private void copyBaseUrl() {
        if (!AppConfig.webEnabled(this)) {
            toast("请先开启 Web 服务");
            return;
        }
        String ip = NetUtil.localIpv4();
        if ("0.0.0.0".equals(ip)) {
            toast("当前没有可用地址");
            return;
        }
        copy("CarBoxHub URL", "http://" + ip + ":" + AppConfig.port(this));
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

    private GradientDrawable codeBg() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.rgb(146, 146, 146));
        g.setCornerRadius(dp(4));
        g.setStroke(dp(1), Color.rgb(196, 196, 196));
        return g;
    }

    private GradientDrawable primaryButtonBg() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.rgb(53, 112, 230));
        g.setCornerRadius(dp(22));
        return g;
    }

    private TextView tv(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private int clampPx(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private int spPx(int px) {
        return Math.max(1, Math.round(px / getResources().getDisplayMetrics().scaledDensity));
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
