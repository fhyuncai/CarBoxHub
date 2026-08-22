package com.carboxhub.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public final class ConfigActivity extends Activity {
    private LinearLayout content;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(buildUi());
    }

    @Override protected void onResume() {
        super.onResume();
        if (content != null) rebuildRows();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(37, 39, 43));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(8), dp(14), dp(8));
        header.setBackgroundColor(Color.rgb(49, 51, 55));

        TextView back = new TextView(this);
        back.setText("←");
        back.setTextColor(Color.WHITE);
        back.setTextSize(28);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(8), 0, dp(14), 0);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(56), dp(48)));

        TextView title = text("配置", 22, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView version = text("v" + BuildConfig.VERSION_NAME, 13, Color.rgb(180, 184, 190));
        header.addView(version);
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(12), dp(18), dp(18));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        rebuildRows();
        return root;
    }

    private void rebuildRows() {
        content.removeAllViews();

        section("权限");
        boolean notificationAccess = NeteaseMediaPlugin.hasNotificationAccess(this);
        actionRow(
                "通知使用权",
                notificationAccess ? "已授权，可读取媒体会话" : "未授权，媒体插件无法读取网易云状态",
                notificationAccess ? "查看" : "授权",
                v -> startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        );

        boolean canInstall = Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls();
        actionRow(
                "安装未知应用",
                canInstall ? "已允许 CarBoxHub 请求安装 APK" : "需要允许才能从管理面板安装 APK",
                canInstall ? "查看" : "授权",
                v -> openUnknownSources()
        );

        boolean storageAllowed = Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        actionRow(
                "公共下载目录",
                storageAllowed ? "文件可保存到 Download/CarBoxHub" : "未授权时会回退到应用私有目录",
                storageAllowed ? "已授权" : "授权",
                v -> requestStorage()
        );

        section("Web 管理");
        toggleRow(
                "Web 管理服务",
                "控制局域网管理页面；关闭后首屏只显示开启 Web 服务按钮",
                AppConfig.webEnabled(this),
                checked -> {
                    AppConfig.setWebEnabled(this, checked);
                    LanServerService.requestApplyWebState(this);
                    toast(checked ? "Web 服务已开启" : "Web 服务已关闭");
                }
        );

        section("访问与安装");
        actionRow(
                "访问令牌",
                "当前令牌：" + AppConfig.token(this),
                "重新生成",
                v -> {
                    AppConfig.regenerateToken(this);
                    toast("已生成新的 6 位令牌");
                    rebuildRows();
                }
        );

        toggleRow(
                "Root 静默安装",
                "有 root 权限时优先使用 pm install -r",
                AppConfig.rootInstall(this),
                checked -> AppConfig.setRootInstall(this, checked)
        );

        section("插件与启动");
        toggleRow(
                "网易云媒体投送",
                "读取网易云车机版 MediaSession 并建立代理媒体会话",
                AppConfig.neteaseEnabled(this),
                checked -> {
                    AppConfig.setNeteaseEnabled(this, checked);
                    if (checked) MediaCaptureEngine.get().start(this); else MediaCaptureEngine.get().stop();
                }
        );

        toggleRow(
                "开机自动启动",
                "车盒启动后自动恢复 CarBoxHub 后台服务",
                AppConfig.autoStart(this),
                checked -> AppConfig.setAutoStart(this, checked)
        );

        section("CarPlay 桥接诊断");
        infoRow("桥接程序", BridgeDiagnostics.summary(this));
        actionRow(
                "生成诊断报告",
                "收集桥接包、当前 MediaSession；有 Root 时附加 dumpsys 与日志",
                "生成",
                v -> generateBridgeDiagnostic()
        );
        actionRow(
                "导出桥接 APK",
                "把检测到的 Car Link / ZLink / TLink 安装包导出到文件目录",
                "导出",
                v -> exportBridgeApk()
        );

        section("状态");
        String webState = !AppConfig.webEnabled(this) ? "已关闭" : (LanServerService.webRunning ? "运行中" : "正在启动");
        infoRow("Web 服务", webState);
        String rootState = AppConfig.rootInstall(this) ? (RootShell.isAvailable() ? "可用" : "无 / 未授权") : "未启用";
        infoRow("Root", rootState);
        infoRow("文件目录", StorageUtil.uploadDir(this).getAbsolutePath());

        NowPlaying now = MediaCaptureEngine.get().current();
        String media = now.title.isEmpty() ? "暂无媒体" : now.title + (now.artist.isEmpty() ? "" : " · " + now.artist);
        infoRow("当前媒体", media);
    }

    private void generateBridgeDiagnostic() {
        toast("正在收集 CarPlay 诊断信息...");
        new Thread(() -> {
            BridgeDiagnostics.Result result = BridgeDiagnostics.capture(getApplicationContext());
            runOnUiThread(() -> {
                if (result.ok && result.file != null) {
                    toast("已生成：" + result.file.getName() + "，可在 Web 服务开启后下载");
                } else {
                    toast("诊断失败：" + result.message);
                }
            });
        }, "CarBoxHub-bridge-diagnostic").start();
    }

    private void exportBridgeApk() {
        toast("正在导出 CarPlay 桥接 APK...");
        new Thread(() -> {
            BridgeDiagnostics.Result result = BridgeDiagnostics.exportPrimaryApk(getApplicationContext());
            runOnUiThread(() -> {
                if (result.ok && result.file != null) {
                    toast("已导出：" + result.file.getName() + "，可在 Web 服务开启后下载");
                } else {
                    toast(result.message);
                }
            });
        }, "CarBoxHub-bridge-export").start();
    }

    private void section(String title) {
        TextView tv = text(title, 13, Color.rgb(150, 154, 160));
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(dp(4), dp(12), dp(4), dp(6));
        content.addView(tv);
    }

    private void actionRow(String title, String subtitle, String actionText, View.OnClickListener listener) {
        LinearLayout row = baseRow();

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = text(title, 16, Color.WHITE);
        TextView s = text(subtitle, 12, Color.rgb(180, 184, 190));
        s.setPadding(0, dp(2), 0, 0);
        labels.addView(t);
        labels.addView(s);
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView action = text(actionText, 13, Color.WHITE);
        action.setGravity(Gravity.CENTER);
        action.setPadding(dp(14), dp(7), dp(14), dp(7));
        action.setBackground(pillBackground());
        action.setOnClickListener(listener);
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionLp.leftMargin = dp(14);
        row.addView(action, actionLp);

        content.addView(row);
    }

    private interface ToggleListener { void onChanged(boolean checked); }

    private void toggleRow(String title, String subtitle, boolean checked, ToggleListener listener) {
        LinearLayout row = baseRow();

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = text(title, 16, Color.WHITE);
        TextView s = text(subtitle, 12, Color.rgb(180, 184, 190));
        s.setPadding(0, dp(2), 0, 0);
        labels.addView(t);
        labels.addView(s);
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch sw = new Switch(this);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> listener.onChanged(isChecked));
        LinearLayout.LayoutParams switchLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        switchLp.leftMargin = dp(12);
        row.addView(sw, switchLp);
        content.addView(row);
    }

    private void infoRow(String title, String value) {
        LinearLayout row = baseRow();
        TextView t = text(title, 15, Color.WHITE);
        row.addView(t, new LinearLayout.LayoutParams(dp(120), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView v = text(value, 13, Color.rgb(190, 194, 200));
        v.setGravity(Gravity.END);
        row.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        content.addView(row);
    }

    private LinearLayout baseRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(48, 50, 54));
        bg.setCornerRadius(dp(10));
        row.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(7);
        row.setLayoutParams(lp);
        return row;
    }

    private GradientDrawable pillBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(83, 86, 92));
        bg.setCornerRadius(dp(18));
        return bg;
    }

    private void requestStorage() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 200);
        } else {
            toast("公共存储权限已可用");
        }
    }

    private void openUnknownSources() {
        if (Build.VERSION.SDK_INT >= 26) {
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
        } else {
            toast("Android 8 以下一般无需单独开启");
        }
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
