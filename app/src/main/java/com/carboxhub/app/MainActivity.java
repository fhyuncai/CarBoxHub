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
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private TextView address;
    private ImageView qr;
    private final TextView[] digits = new TextView[6];
    private String lastUrl = "";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() { refresh(); handler.postDelayed(this, 2500); }
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
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(7,13,25));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(42), dp(28), dp(42), dp(28));
        scroll.addView(root);

        TextView title = tv("CarBoxHub", 30, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setOnLongClickListener(v -> { showSettings(); return true; });
        root.addView(title);

        LinearLayout addrCard = card("访问地址");
        address = tv("等待连接局域网", 21, Color.WHITE);
        address.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        address.setGravity(Gravity.CENTER);
        address.setPadding(0, dp(10), 0, 0);
        addrCard.addView(address);
        addrCard.setOnClickListener(v -> copyUrl());
        root.addView(addrCard, lpTop(22));

        LinearLayout tokenCard = card("访问令牌");
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, dp(14), 0, 0);
        for (int n=0; n<6; n++) {
            TextView d = tv("0", 28, Color.rgb(15,23,42));
            d.setGravity(Gravity.CENTER);
            d.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            d.setBackground(codeBg());
            digits[n] = d;
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(54), dp(64));
            if (n > 0) p.leftMargin = dp(12);
            row.addView(d, p);
        }
        tokenCard.addView(row);
        tokenCard.setOnClickListener(v -> copyToken());
        root.addView(tokenCard, lpTop(16));

        LinearLayout qrCard = card("扫码进入管理面板");
        qrCard.setGravity(Gravity.CENTER_HORIZONTAL);
        qr = new ImageView(this);
        qr.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        qr.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable white = new GradientDrawable();
        white.setColor(Color.WHITE); white.setCornerRadius(dp(24));
        qr.setBackground(white);
        qr.setOnClickListener(v -> showQr());
        LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(dp(280), dp(280));
        qp.topMargin = dp(16);
        qrCard.addView(qr, qp);
        root.addView(qrCard, lpTop(16));
        return scroll;
    }

    private void refresh() {
        String ip = NetUtil.localIpv4();
        String token = AppConfig.token(this);
        String url = "http://" + ip + ":" + AppConfig.port(this) + "/?token=" + token;
        address.setText("0.0.0.0".equals(ip) ? "等待连接局域网" : url);
        for (int i=0; i<6; i++) digits[i].setText(String.valueOf(token.charAt(i)));
        if ("0.0.0.0".equals(ip)) { qr.setImageDrawable(null); lastUrl=""; return; }
        if (!url.equals(lastUrl)) {
            qr.setImageBitmap(QrBitmap.create(url, dp(248)));
            lastUrl = url;
        }
    }

    private void showQr() {
        String ip = NetUtil.localIpv4();
        if ("0.0.0.0".equals(ip)) { toast("当前没有可用的局域网地址"); return; }
        String url = "http://" + ip + ":" + AppConfig.port(this) + "/?token=" + AppConfig.token(this);
        Dialog d = new Dialog(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(18),dp(18),dp(18),dp(18));
        box.setBackgroundColor(Color.WHITE);
        ImageView image = new ImageView(this);
        int size = Math.max(dp(280), (int)(Math.min(getResources().getDisplayMetrics().widthPixels,getResources().getDisplayMetrics().heightPixels)*0.7f));
        image.setImageBitmap(QrBitmap.create(url,size));
        box.addView(image,new LinearLayout.LayoutParams(size,size));
        TextView u = tv(url,12,Color.rgb(51,65,85)); u.setGravity(Gravity.CENTER); box.addView(u);
        box.setOnClickListener(v -> d.dismiss());
        d.setContentView(box); d.show();
    }

    private void showSettings() {
        Dialog d = new Dialog(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20),dp(20),dp(20),dp(20));
        box.setBackgroundColor(Color.rgb(7,13,25));
        scroll.addView(box);
        box.addView(tv("CarBoxHub 控制面板",22,Color.WHITE));
        TextView hint = tv("长按首页标题可打开这里",13,Color.rgb(148,163,184)); hint.setPadding(0,dp(6),0,dp(12)); box.addView(hint);

        Button reset = button("重置 6 位令牌"); reset.setOnClickListener(v -> { AppConfig.regenerateToken(this); refresh(); toast("令牌已更新"); }); box.addView(reset);
        Button notif = button("通知使用权"); notif.setOnClickListener(v -> startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))); box.addView(notif);
        Button unknown = button("安装未知应用"); unknown.setOnClickListener(v -> openUnknown()); box.addView(unknown);
        Button storage = button("公共下载目录"); storage.setOnClickListener(v -> requestStorage()); box.addView(storage);

        CheckBox media = check("启用：网易云媒体投送插件", AppConfig.neteaseEnabled(this));
        media.setOnCheckedChangeListener((v,c) -> { AppConfig.setNeteaseEnabled(this,c); if(c) MediaCaptureEngine.get().start(this); else MediaCaptureEngine.get().stop(); }); box.addView(media);
        CheckBox root = check("有 root 时优先静默安装 APK", AppConfig.rootInstall(this));
        root.setOnCheckedChangeListener((v,c) -> AppConfig.setRootInstall(this,c)); box.addView(root);
        CheckBox boot = check("开机自动启动 CarBoxHub", AppConfig.autoStart(this));
        boot.setOnCheckedChangeListener((v,c) -> AppConfig.setAutoStart(this,c)); box.addView(boot);

        boolean access = NeteaseMediaPlugin.hasNotificationAccess(this);
        boolean install = Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls();
        TextView state = tv("通知使用权："+(access?"已授权":"未授权")+"\nAPK 安装权限："+(install?"可用":"未允许")+"\n文件目录："+StorageUtil.uploadDir(this).getAbsolutePath(),14,Color.rgb(203,213,225));
        state.setPadding(0,dp(12),0,0); box.addView(state);
        d.setContentView(scroll); d.setOnDismissListener(x -> refresh()); d.show();
    }

    private void copyUrl() {
        String ip=NetUtil.localIpv4(); if("0.0.0.0".equals(ip)){toast("当前没有可用地址");return;}
        copy("CarBoxHub URL","http://"+ip+":"+AppConfig.port(this)+"/?token="+AppConfig.token(this)); toast("地址已复制");
    }
    private void copyToken(){ copy("CarBoxHub Token",AppConfig.token(this)); toast("令牌已复制"); }
    private void copy(String label,String value){ ((ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(label,value)); }
    private void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_SHORT).show(); }

    private void requestStorage(){
        if(Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE},200);
        else toast("公共存储权限已可用");
    }
    private void openUnknown(){ if(Build.VERSION.SDK_INT>=26) startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:"+getPackageName()))); else toast("Android 8 以下一般无需单独开启"); }

    private LinearLayout card(String label){
        LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(20),dp(18),dp(20),dp(18)); c.setBackground(cardBg());
        TextView l=tv(label,14,Color.rgb(148,163,184)); l.setGravity(Gravity.CENTER); c.addView(l); return c;
    }
    private LinearLayout.LayoutParams lpTop(int top){ LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); p.topMargin=dp(top); return p; }
    private GradientDrawable cardBg(){ GradientDrawable g=new GradientDrawable(); g.setColor(Color.rgb(15,23,42)); g.setCornerRadius(dp(26)); g.setStroke(dp(1),Color.rgb(37,99,235)); return g; }
    private GradientDrawable codeBg(){ GradientDrawable g=new GradientDrawable(); g.setColor(Color.rgb(241,245,249)); g.setCornerRadius(dp(18)); g.setStroke(dp(2),Color.rgb(191,219,254)); return g; }
    private TextView tv(String s,int sp,int color){ TextView t=new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); return t; }
    private Button button(String s){ Button b=new Button(this); b.setText(s); b.setAllCaps(false); return b; }
    private CheckBox check(String s,boolean v){ CheckBox c=new CheckBox(this); c.setText(s); c.setTextColor(Color.WHITE); c.setChecked(v); return c; }
    private int dp(int v){ return Math.round(getResources().getDisplayMetrics().density*v); }
}
