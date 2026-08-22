package com.carboxhub.app;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

public final class InstallManager {
    private InstallManager() {}

    public static ApkInfo inspect(Context c, File apk) {
        try {
            PackageInfo info = c.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
            if (info == null) return new ApkInfo(false, "", "", 0, false, "无法解析 APK");
            long vc = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            return new ApkInfo(true, info.packageName, info.versionName == null ? "" : info.versionName,
                    vc, c.getPackageName().equals(info.packageName), "");
        } catch (Throwable t) {
            return new ApkInfo(false, "", "", 0, false, t.toString());
        }
    }

    public static InstallResult install(Context c, File apk) {
        ApkInfo info = inspect(c, apk);
        if (!info.valid) return new InstallResult(false, false, info.error);

        if (AppConfig.rootInstall(c) && RootShell.isAvailable()) {
            RootShell.Result r = RootShell.exec("pm install -r " + RootShell.shQuote(apk.getAbsolutePath()));
            if (r.ok) return new InstallResult(true, true, "root 静默安装成功: " + info.packageName);
        }

        try {
            PackageInstaller installer = c.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(info.packageName);
            int sessionId = installer.createSession(params);
            PackageInstaller.Session session = installer.openSession(sessionId);
            try (FileInputStream in = new FileInputStream(apk);
                 OutputStream out = session.openWrite("base.apk", 0, apk.length())) {
                byte[] buf = new byte[128 * 1024];
                int n;
                while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
                session.fsync(out);
            }

            Intent callback = new Intent(c, InstallResultReceiver.class);
            callback.setAction("com.carboxhub.app.INSTALL_RESULT");
            callback.putExtra("package", info.packageName);
            callback.putExtra("selfUpdate", info.selfUpdate);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
            PendingIntent pi = PendingIntent.getBroadcast(c, sessionId, callback, flags);
            session.commit(pi.getIntentSender());
            session.close();
            return new InstallResult(true, false, info.selfUpdate ? "自更新已提交，等待系统确认" : "安装已提交，等待系统确认");
        } catch (Throwable t) {
            return new InstallResult(false, false, t.toString());
        }
    }

    public static final class ApkInfo {
        public final boolean valid; public final String packageName; public final String versionName; public final long versionCode; public final boolean selfUpdate; public final String error;
        ApkInfo(boolean valid, String packageName, String versionName, long versionCode, boolean selfUpdate, String error) {
            this.valid = valid; this.packageName = packageName; this.versionName = versionName; this.versionCode = versionCode; this.selfUpdate = selfUpdate; this.error = error;
        }
        public String json() {
            return "{" + "\"valid\":" + valid + "," + "\"packageName\":" + JsonUtil.q(packageName) + "," + "\"versionName\":" + JsonUtil.q(versionName) + "," + "\"versionCode\":" + versionCode + "," + "\"selfUpdate\":" + selfUpdate + "," + "\"error\":" + JsonUtil.q(error) + "}";
        }
    }

    public static final class InstallResult {
        public final boolean accepted; public final boolean silent; public final String message;
        InstallResult(boolean accepted, boolean silent, String message) { this.accepted = accepted; this.silent = silent; this.message = message; }
        public String json() { return "{\"ok\":" + accepted + ",\"silent\":" + silent + ",\"message\":" + JsonUtil.q(message) + "}"; }
    }
}
