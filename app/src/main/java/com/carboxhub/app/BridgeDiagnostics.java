package com.carboxhub.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class BridgeDiagnostics {
    private static final String[] EXACT_PACKAGES = new String[] {
            "com.syu.carlink",
            "com.zjinnova.zlink",
            "com.zjinnova.zlink5",
            "com.suding.speedplay",
            "com.autokit.carplay",
            "com.carlinkit.autokit"
    };

    private static final String[] NATIVE_PATHS = new String[] {
            "/system/bin/sd_carplay",
            "/system/bin/sd_mdnsd",
            "/system/bin/z-link",
            "/system/bin/z-link.sh",
            "/system/bin/z-mdnsd",
            "/vendor/bin/sd_carplay",
            "/vendor/bin/sd_mdnsd",
            "/vendor/bin/z-link",
            "/product/bin/sd_carplay"
    };

    private BridgeDiagnostics() {}

    public static String summary(Context context) {
        List<Candidate> candidates = findCandidates(context);
        if (candidates.isEmpty()) return "未检测到常见 CarPlay 桥接包";
        Candidate c = candidates.get(0);
        return c.label + " · " + c.packageName + " · v" + c.versionName;
    }

    public static Result capture(Context context) {
        Context app = context.getApplicationContext();
        File dir = StorageUtil.uploadDir(app);
        if (!dir.exists() && !dir.mkdirs()) return new Result(false, null, "无法创建文件目录");

        File out = new File(dir, "CarBoxHub-CarPlay-diagnostic.txt");
        try {
            String report = buildReport(app);
            OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(out, false), StandardCharsets.UTF_8);
            writer.write(report);
            writer.flush();
            writer.close();
            return new Result(true, out, "诊断报告已生成");
        } catch (Throwable t) {
            return new Result(false, null, t.toString());
        }
    }

    public static Result exportPrimaryApk(Context context) {
        Context app = context.getApplicationContext();
        List<Candidate> candidates = findCandidates(app);
        if (candidates.isEmpty()) return new Result(false, null, "未检测到可导出的 CarPlay 桥接包");

        Candidate candidate = candidates.get(0);
        if (TextUtils.isEmpty(candidate.sourceDir)) return new Result(false, null, "桥接包没有可读取的 APK 路径");

        File src = new File(candidate.sourceDir);
        File dir = StorageUtil.uploadDir(app);
        if (!dir.exists() && !dir.mkdirs()) return new Result(false, null, "无法创建文件目录");

        String safeVersion = candidate.versionName.replaceAll("[^0-9A-Za-z._-]", "_");
        File dst = new File(dir, "bridge-" + candidate.packageName + "-v" + safeVersion + ".apk");
        try {
            copy(src, dst);
            return new Result(true, dst, "桥接 APK 已导出");
        } catch (Throwable directError) {
            if (RootShell.isAvailable()) {
                RootShell.Result root = RootShell.exec(
                        "cp " + RootShell.shQuote(candidate.sourceDir) + " " + RootShell.shQuote(dst.getAbsolutePath()) +
                                " && chmod 644 " + RootShell.shQuote(dst.getAbsolutePath())
                );
                if (root.ok && dst.isFile() && dst.length() > 0) return new Result(true, dst, "桥接 APK 已通过 root 导出");
                return new Result(false, null, "导出失败：" + root.output);
            }
            return new Result(false, null, "导出失败：" + directError);
        }
    }

    private static String buildReport(Context context) {
        StringBuilder b = new StringBuilder(96 * 1024);
        b.append("CarBoxHub CarPlay Bridge Diagnostic\n");
        b.append("Generated: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date())).append("\n");
        b.append("CarBoxHub: v").append(BuildConfig.VERSION_NAME).append("\n\n");

        section(b, "DEVICE");
        line(b, "manufacturer", Build.MANUFACTURER);
        line(b, "brand", Build.BRAND);
        line(b, "model", Build.MODEL);
        line(b, "device", Build.DEVICE);
        line(b, "product", Build.PRODUCT);
        line(b, "display", Build.DISPLAY);
        line(b, "fingerprint", Build.FINGERPRINT);
        line(b, "sdk", String.valueOf(Build.VERSION.SDK_INT));
        line(b, "release", Build.VERSION.RELEASE);
        line(b, "abis", Arrays.toString(Build.SUPPORTED_ABIS));
        line(b, "root", String.valueOf(RootShell.isAvailable()));
        line(b, "notificationAccess", String.valueOf(NeteaseMediaPlugin.hasNotificationAccess(context)));
        NowPlaying now = MediaCaptureEngine.get().current();
        line(b, "neteaseNowPlaying", now.title + " | " + now.artist + " | " + now.album + " | playing=" + now.playing);

        List<Candidate> candidates = findCandidates(context);
        section(b, "CARPLAY BRIDGE CANDIDATES");
        if (candidates.isEmpty()) {
            b.append("(none detected)\n");
        } else {
            for (int i = 0; i < candidates.size(); i++) appendCandidate(b, i + 1, candidates.get(i));
        }

        section(b, "KNOWN NATIVE CARPLAY FILES");
        for (String path : NATIVE_PATHS) {
            File f = new File(path);
            b.append(path).append(" exists=").append(f.exists()).append(" readable=").append(f.canRead());
            if (f.exists()) b.append(" size=").append(f.length());
            b.append('\n');
        }

        section(b, "ACTIVE MEDIA SESSIONS");
        appendMediaSessions(context, b);

        if (RootShell.isAvailable()) {
            section(b, "ROOT: PROCESSES");
            appendRoot(b, "ps -A | grep -Ei 'carlink|carplay|zlink|tlink|autokit|speedplay|syu' | head -n 250", 30000);

            section(b, "ROOT: SYSTEM PROPERTIES");
            appendRoot(b, "getprop | grep -Ei 'fyt|syu|dudu|carplay|carlink|ro.build|ro.product' | head -n 300", 40000);

            section(b, "ROOT: MEDIA_SESSION");
            appendRoot(b, "dumpsys media_session", 90000);

            for (Candidate c : candidates) {
                section(b, "ROOT: PACKAGE " + c.packageName);
                appendRoot(b, "dumpsys package " + RootShell.shQuote(c.packageName), 90000);
            }

            section(b, "ROOT: RECENT CARPLAY/MEDIA LOGCAT");
            appendRoot(b, "logcat -d -t 1200 | grep -Ei 'carlink|carplay|zlink|tlink|autokit|speedplay|MediaSession|NowPlaying|metadata|com.syu' | tail -n 500", 120000);
        } else {
            section(b, "ROOT DIAGNOSTICS");
            b.append("Root unavailable; dumpsys package/media_session and filtered logcat were skipped.\n");
        }

        return b.toString();
    }

    private static void appendMediaSessions(Context context, StringBuilder b) {
        try {
            MediaSessionManager manager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            ComponentName listener = new ComponentName(context, MediaNotificationListener.class);
            List<MediaController> sessions = manager == null ? null : manager.getActiveSessions(listener);
            if (sessions == null || sessions.isEmpty()) {
                b.append("(no active sessions)\n");
                return;
            }
            int index = 0;
            for (MediaController c : sessions) {
                if (c == null) continue;
                b.append("#").append(++index).append(" package=").append(c.getPackageName()).append('\n');
                MediaMetadata m = c.getMetadata();
                if (m != null) {
                    line(b, "  title", value(m, MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE));
                    line(b, "  artist", value(m, MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE));
                    line(b, "  album", safe(m.getString(MediaMetadata.METADATA_KEY_ALBUM)));
                    line(b, "  duration", String.valueOf(m.getLong(MediaMetadata.METADATA_KEY_DURATION)));
                    line(b, "  hasAlbumArt", String.valueOf(m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) != null));
                    line(b, "  hasArt", String.valueOf(m.getBitmap(MediaMetadata.METADATA_KEY_ART) != null));
                    line(b, "  hasDisplayIcon", String.valueOf(m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON) != null));
                }
                PlaybackState s = c.getPlaybackState();
                if (s != null) {
                    line(b, "  playbackState", String.valueOf(s.getState()));
                    line(b, "  position", String.valueOf(s.getPosition()));
                    line(b, "  actions", "0x" + Long.toHexString(s.getActions()));
                }
                Bundle extras = c.getExtras();
                if (extras != null && !extras.isEmpty()) line(b, "  extrasKeys", extras.keySet().toString());
            }
        } catch (SecurityException e) {
            b.append("SecurityException: notification access is required: ").append(e).append('\n');
        } catch (Throwable t) {
            b.append("Error: ").append(t).append('\n');
        }
    }

    private static String value(MediaMetadata metadata, String first, String second) {
        String v = metadata.getString(first);
        if (TextUtils.isEmpty(v)) v = metadata.getString(second);
        return safe(v);
    }

    private static void appendCandidate(StringBuilder b, int index, Candidate c) {
        b.append("#").append(index).append(' ').append(c.label).append('\n');
        line(b, "package", c.packageName);
        line(b, "version", c.versionName + " (" + c.versionCode + ")");
        line(b, "sourceDir", c.sourceDir);
        line(b, "processName", c.processName);
        line(b, "uid", String.valueOf(c.uid));
        line(b, "enabled", String.valueOf(c.enabled));
        line(b, "systemApp", String.valueOf(c.systemApp));
        if (c.requestedPermissions != null && c.requestedPermissions.length > 0) {
            line(b, "requestedPermissions", Arrays.toString(c.requestedPermissions));
        }
        appendComponents(b, "activities", c.activities);
        appendServices(b, "services", c.services);
        appendComponents(b, "receivers", c.receivers);
        b.append('\n');
    }

    private static void appendComponents(StringBuilder b, String title, ActivityInfo[] infos) {
        b.append(title).append(':').append('\n');
        if (infos == null || infos.length == 0) {
            b.append("  (none)\n");
            return;
        }
        int max = Math.min(120, infos.length);
        for (int i = 0; i < max; i++) {
            ActivityInfo x = infos[i];
            b.append("  ").append(x.name)
                    .append(" exported=").append(x.exported)
                    .append(" enabled=").append(x.enabled)
                    .append(" permission=").append(safe(x.permission))
                    .append(" process=").append(safe(x.processName))
                    .append('\n');
        }
        if (infos.length > max) b.append("  ... ").append(infos.length - max).append(" more\n");
    }

    private static void appendServices(StringBuilder b, String title, ServiceInfo[] infos) {
        b.append(title).append(':').append('\n');
        if (infos == null || infos.length == 0) {
            b.append("  (none)\n");
            return;
        }
        int max = Math.min(120, infos.length);
        for (int i = 0; i < max; i++) {
            ServiceInfo x = infos[i];
            b.append("  ").append(x.name)
                    .append(" exported=").append(x.exported)
                    .append(" enabled=").append(x.enabled)
                    .append(" permission=").append(safe(x.permission))
                    .append(" process=").append(safe(x.processName))
                    .append('\n');
        }
        if (infos.length > max) b.append("  ... ").append(infos.length - max).append(" more\n");
    }

    private static List<Candidate> findCandidates(Context context) {
        PackageManager pm = context.getPackageManager();
        ArrayList<Candidate> out = new ArrayList<>();
        int flags = PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES | PackageManager.GET_RECEIVERS |
                PackageManager.GET_PERMISSIONS | PackageManager.GET_META_DATA;
        try {
            List<PackageInfo> all = pm.getInstalledPackages(flags);
            if (all != null) {
                for (PackageInfo p : all) {
                    if (p == null || p.applicationInfo == null) continue;
                    String label;
                    try { label = String.valueOf(pm.getApplicationLabel(p.applicationInfo)); }
                    catch (Throwable ignored) { label = ""; }
                    if (!looksLikeBridge(p.packageName, label)) continue;
                    out.add(new Candidate(pm, p, label));
                }
            }
        } catch (Throwable ignored) {}

        for (String exact : EXACT_PACKAGES) {
            boolean exists = false;
            for (Candidate c : out) if (exact.equals(c.packageName)) { exists = true; break; }
            if (exists) continue;
            try {
                PackageInfo p = pm.getPackageInfo(exact, flags);
                String label = String.valueOf(pm.getApplicationLabel(p.applicationInfo));
                out.add(new Candidate(pm, p, label));
            } catch (Throwable ignored) {}
        }

        Collections.sort(out, new Comparator<Candidate>() {
            @Override public int compare(Candidate a, Candidate b) {
                int pa = priority(a.packageName);
                int pb = priority(b.packageName);
                if (pa != pb) return Integer.compare(pa, pb);
                return a.packageName.compareTo(b.packageName);
            }
        });
        return out;
    }

    private static boolean looksLikeBridge(String pkg, String label) {
        String p = safe(pkg).toLowerCase(Locale.US);
        String l = safe(label).toLowerCase(Locale.US);
        for (String exact : EXACT_PACKAGES) if (exact.equals(p)) return true;
        return containsAny(p, "carlink", "carplay", "zlink", "tlink", "autokit", "speedplay", "carbit") ||
                containsAny(l, "car link", "carlink", "carplay", "zlink", "tlink", "autokit", "speedplay");
    }

    private static boolean containsAny(String value, String... words) {
        for (String word : words) if (value.contains(word)) return true;
        return false;
    }

    private static int priority(String pkg) {
        if ("com.syu.carlink".equals(pkg)) return 0;
        if (pkg.contains("carlink")) return 1;
        if (pkg.contains("zlink")) return 2;
        if (pkg.contains("tlink")) return 3;
        return 10;
    }

    private static void appendRoot(StringBuilder b, String command, int maxChars) {
        RootShell.Result r = RootShell.exec(command);
        b.append("$ ").append(command).append('\n');
        b.append("ok=").append(r.ok).append('\n');
        String text = safe(r.output);
        if (text.length() > maxChars) text = text.substring(0, maxChars) + "\n... [truncated]";
        b.append(text).append('\n');
    }

    private static void copy(File src, File dst) throws Exception {
        FileInputStream in = new FileInputStream(src);
        FileOutputStream out = new FileOutputStream(dst, false);
        try {
            byte[] buffer = new byte[128 * 1024];
            int n;
            while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
            out.flush();
        } finally {
            try { in.close(); } catch (Throwable ignored) {}
            try { out.close(); } catch (Throwable ignored) {}
        }
    }

    private static void section(StringBuilder b, String title) {
        b.append("\n========== ").append(title).append(" ==========\n");
    }

    private static void line(StringBuilder b, String key, String value) {
        b.append(key).append(": ").append(safe(value)).append('\n');
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class Candidate {
        final String packageName;
        final String label;
        final String versionName;
        final long versionCode;
        final String sourceDir;
        final String processName;
        final int uid;
        final boolean enabled;
        final boolean systemApp;
        final String[] requestedPermissions;
        final ActivityInfo[] activities;
        final ServiceInfo[] services;
        final ActivityInfo[] receivers;

        Candidate(PackageManager pm, PackageInfo p, String label) {
            this.packageName = safe(p.packageName);
            this.label = TextUtils.isEmpty(label) ? this.packageName : label;
            this.versionName = TextUtils.isEmpty(p.versionName) ? "unknown" : p.versionName;
            this.versionCode = Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode;
            ApplicationInfo a = p.applicationInfo;
            this.sourceDir = a == null ? "" : safe(a.publicSourceDir);
            this.processName = a == null ? "" : safe(a.processName);
            this.uid = a == null ? -1 : a.uid;
            this.enabled = a != null && a.enabled;
            this.systemApp = a != null && (a.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            this.requestedPermissions = p.requestedPermissions;
            this.activities = p.activities;
            this.services = p.services;
            this.receivers = p.receivers;
        }
    }

    public static final class Result {
        public final boolean ok;
        public final File file;
        public final String message;

        Result(boolean ok, File file, String message) {
            this.ok = ok;
            this.file = file;
            this.message = message;
        }
    }
}
