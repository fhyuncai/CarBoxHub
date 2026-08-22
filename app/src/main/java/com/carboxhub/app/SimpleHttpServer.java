package com.carboxhub.app;

import android.content.Context;
import android.content.pm.PackageManager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SimpleHttpServer {
    private static final long MAX_UPLOAD = 2L * 1024 * 1024 * 1024;
    private final Context context;
    private final int port;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private volatile boolean alive;
    private ServerSocket ss;
    private Thread acceptThread;

    public SimpleHttpServer(Context context, int port) {
        this.context = context.getApplicationContext();
        this.port = port;
    }

    public void start() throws IOException {
        if (alive) return;
        ss = new ServerSocket(port);
        ss.setReuseAddress(true);
        alive = true;
        acceptThread = new Thread(new Runnable() {
            @Override public void run() {
                while (alive) {
                    try {
                        final Socket s = ss.accept();
                        s.setSoTimeout(120000);
                        pool.execute(new Runnable() {
                            @Override public void run() { handle(s); }
                        });
                    } catch (Throwable t) {
                        if (!alive) break;
                    }
                }
            }
        }, "CarBoxHub-http");
        acceptThread.start();
    }

    public void stop() {
        alive = false;
        try { if (ss != null) ss.close(); } catch (Throwable ignored) {}
        pool.shutdownNow();
    }

    private File uploadDir() { return StorageUtil.uploadDir(context); }

    private void handle(Socket socket) {
        try (Socket s = socket;
             BufferedInputStream in = new BufferedInputStream(s.getInputStream(), 64 * 1024);
             BufferedOutputStream out = new BufferedOutputStream(s.getOutputStream(), 64 * 1024)) {
            String request = readLine(in);
            if (request == null || request.length() > 8192) return;
            String[] rp = request.split(" ");
            if (rp.length < 2) {
                sendJson(out, 400, "{\"ok\":false,\"message\":\"bad request\"}");
                return;
            }

            String method = rp[0].toUpperCase(Locale.ROOT);
            String rawTarget = rp[1];
            String path = rawTarget;
            String query = "";
            int qi = rawTarget.indexOf('?');
            if (qi >= 0) {
                path = rawTarget.substring(0, qi);
                query = rawTarget.substring(qi + 1);
            }

            Map<String,String> q = parseQuery(query);
            Map<String,String> h = new HashMap<>();
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                int c = line.indexOf(':');
                if (c > 0) h.put(line.substring(0, c).trim().toLowerCase(Locale.ROOT), line.substring(c + 1).trim());
            }

            if ("GET".equals(method) && "/".equals(path)) {
                if (!authorized(q, h)) sendHtml(out, 401, loginPage());
                else sendHtml(out, 200, WebUi.page(AppConfig.token(context)));
                return;
            }
            if (!authorized(q, h)) {
                sendJson(out, 401, "{\"ok\":false,\"message\":\"unauthorized\"}");
                return;
            }

            if ("GET".equals(method) && "/api/status".equals(path)) { sendJson(out, 200, statusJson()); return; }
            if ("GET".equals(method) && "/api/config-state".equals(path)) { sendJson(out, 200, configStateJson()); return; }
            if ("GET".equals(method) && "/api/files".equals(path)) { sendJson(out, 200, filesJson()); return; }
            if ("GET".equals(method) && "/api/download".equals(path)) { download(out, q.get("name")); return; }
            if ("POST".equals(method) && "/api/upload".equals(path)) { upload(in, out, h, q); return; }
            if ("POST".equals(method) && "/api/install".equals(path)) { install(out, q.get("name")); return; }
            if ("POST".equals(method) && "/api/delete".equals(path)) { delete(out, q.get("name")); return; }
            if ("POST".equals(method) && "/api/plugin".equals(path)) { plugin(out, q); return; }
            if ("POST".equals(method) && "/api/config".equals(path)) { config(out, q); return; }
            sendJson(out, 404, "{\"ok\":false,\"message\":\"not found\"}");
        } catch (Throwable ignored) {}
    }

    private boolean authorized(Map<String,String> q, Map<String,String> h) {
        String expected = AppConfig.token(context);
        String got = h.get("x-carbox-token");
        if (got == null) got = q.get("token");
        return expected.equals(got);
    }

    private void upload(InputStream in, OutputStream out, Map<String,String> h, Map<String,String> q) throws IOException {
        String name = safeName(q.get("filename"));
        if (name.isEmpty()) {
            sendJson(out, 400, "{\"ok\":false,\"message\":\"缺少文件名\"}");
            return;
        }
        File dst = uniqueFile(uploadDir(), name);
        long size;
        try (FileOutputStream f = new FileOutputStream(dst)) {
            String te = h.get("transfer-encoding");
            if (te != null && te.toLowerCase(Locale.ROOT).contains("chunked")) {
                size = copyChunked(in, f, MAX_UPLOAD);
            } else {
                String cl = h.get("content-length");
                if (cl == null) {
                    dst.delete();
                    sendJson(out, 411, "{\"ok\":false,\"message\":\"Length Required\"}");
                    return;
                }
                long len;
                try { len = Long.parseLong(cl); } catch (Exception e) { len = -1; }
                if (len < 0 || len > MAX_UPLOAD) {
                    dst.delete();
                    sendJson(out, 413, "{\"ok\":false,\"message\":\"文件过大\"}");
                    return;
                }
                size = copyFixed(in, f, len);
            }
        } catch (Throwable t) {
            dst.delete();
            sendJson(out, 500, "{\"ok\":false,\"message\":" + JsonUtil.q(t.toString()) + "}");
            return;
        }

        boolean apk = dst.getName().toLowerCase(Locale.ROOT).endsWith(".apk");
        InstallManager.ApkInfo info = apk ? InstallManager.inspect(context, dst) : null;
        boolean doInstall = "1".equals(q.get("install")) || "true".equalsIgnoreCase(q.get("install"));
        InstallManager.InstallResult ir = (apk && doInstall) ? InstallManager.install(context, dst) : null;
        sendJson(out, 200,
                "{\"ok\":true,\"name\":" + JsonUtil.q(dst.getName()) +
                        ",\"size\":" + size +
                        ",\"apk\":" + (info == null ? "null" : info.json()) +
                        ",\"install\":" + (ir == null ? "null" : ir.json()) + "}");
    }

    private void install(OutputStream out, String raw) throws IOException {
        File f = resolve(raw);
        if (f == null || !f.isFile()) {
            sendJson(out, 404, "{\"ok\":false,\"message\":\"文件不存在\"}");
            return;
        }
        InstallManager.InstallResult r = InstallManager.install(context, f);
        sendJson(out, r.accepted ? 200 : 400, r.json());
    }

    private void delete(OutputStream out, String raw) throws IOException {
        File f = resolve(raw);
        boolean ok = f != null && f.isFile() && f.delete();
        sendJson(out, ok ? 200 : 404, "{\"ok\":" + ok + "}");
    }

    private void plugin(OutputStream out, Map<String,String> q) throws IOException {
        Plugin p = PluginRegistry.find(q.get("id"));
        if (p == null) {
            sendJson(out, 404, "{\"ok\":false,\"message\":\"插件不存在\"}");
            return;
        }
        boolean en = "1".equals(q.get("enabled")) || "true".equalsIgnoreCase(q.get("enabled"));
        p.setEnabled(context, en);
        if (en) p.start(context); else p.stop(context);
        sendJson(out, 200, configStateJson());
    }

    private void config(OutputStream out, Map<String,String> q) throws IOException {
        if (q.containsKey("rootInstall")) {
            AppConfig.setRootInstall(context, "1".equals(q.get("rootInstall")) || "true".equalsIgnoreCase(q.get("rootInstall")));
        }
        if (q.containsKey("autoStart")) {
            AppConfig.setAutoStart(context, "1".equals(q.get("autoStart")) || "true".equalsIgnoreCase(q.get("autoStart")));
        }
        sendJson(out, 200, configStateJson());
    }

    private void download(OutputStream out, String raw) throws IOException {
        File f = resolve(raw);
        if (f == null || !f.isFile()) {
            sendJson(out, 404, "{\"ok\":false,\"message\":\"文件不存在\"}");
            return;
        }
        byte[] head = ("HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/octet-stream\r\n" +
                "Content-Disposition: attachment; filename=\"" + f.getName().replace("\"", "") + "\"\r\n" +
                "Content-Length: " + f.length() + "\r\n" +
                "Connection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        out.write(head);
        try (FileInputStream fi = new FileInputStream(f)) {
            byte[] buf = new byte[128 * 1024];
            int n;
            while ((n = fi.read(buf)) >= 0) out.write(buf, 0, n);
        }
        out.flush();
    }

    private String configStateJson() {
        return "{" +
                "\"ok\":true," +
                "\"webEnabled\":" + AppConfig.webEnabled(context) + "," +
                "\"rootInstall\":" + AppConfig.rootInstall(context) + "," +
                "\"autoStart\":" + AppConfig.autoStart(context) + "," +
                "\"neteaseEnabled\":" + AppConfig.neteaseEnabled(context) +
                "}";
    }

    private String statusJson() {
        PackageManager pm = context.getPackageManager();
        boolean canInstall = android.os.Build.VERSION.SDK_INT < 26 || pm.canRequestPackageInstalls();
        return "{" +
                "\"ok\":true," +
                "\"version\":" + JsonUtil.q(BuildConfig.VERSION_NAME) + "," +
                "\"ip\":" + JsonUtil.q(NetUtil.localIpv4()) + "," +
                "\"port\":" + port + "," +
                "\"webEnabled\":" + AppConfig.webEnabled(context) + "," +
                "\"rootAvailable\":" + (AppConfig.rootInstall(context) && RootShell.isAvailable()) + "," +
                "\"rootInstall\":" + AppConfig.rootInstall(context) + "," +
                "\"autoStart\":" + AppConfig.autoStart(context) + "," +
                "\"storagePath\":" + JsonUtil.q(uploadDir().getAbsolutePath()) + "," +
                "\"canRequestPackageInstalls\":" + canInstall + "," +
                "\"plugins\":" + PluginRegistry.json(context) +
                "}";
    }

    private String filesJson() {
        File[] a = uploadDir().listFiles();
        ArrayList<File> files = new ArrayList<>();
        if (a != null) Collections.addAll(files, a);
        Collections.sort(files, new Comparator<File>() {
            @Override public int compare(File x, File y) { return Long.compare(y.lastModified(), x.lastModified()); }
        });
        StringBuilder b = new StringBuilder("{\"ok\":true,\"files\":[");
        boolean first = true;
        for (File f : files) {
            if (!f.isFile()) continue;
            if (!first) b.append(',');
            first = false;
            InstallManager.ApkInfo ai = f.getName().toLowerCase(Locale.ROOT).endsWith(".apk") ? InstallManager.inspect(context, f) : null;
            b.append("{\"name\":").append(JsonUtil.q(f.getName()))
                    .append(",\"size\":").append(f.length())
                    .append(",\"modified\":").append(f.lastModified())
                    .append(",\"apk\":").append(ai == null ? "null" : ai.json()).append('}');
        }
        return b.append("]}").toString();
    }

    private File resolve(String raw) {
        String n = safeName(raw);
        if (n.isEmpty()) return null;
        try {
            File dir = uploadDir().getCanonicalFile();
            File f = new File(dir, n).getCanonicalFile();
            if (!f.getParentFile().equals(dir)) return null;
            return f;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String safeName(String raw) {
        if (raw == null) return "";
        try { raw = URLDecoder.decode(raw, "UTF-8"); } catch (Throwable ignored) {}
        raw = raw.replace('\\', '/');
        int i = raw.lastIndexOf('/');
        if (i >= 0) raw = raw.substring(i + 1);
        raw = raw.replaceAll("[\\r\\n\\t]", "_").trim();
        if (raw.equals(".") || raw.equals("..")) return "";
        return raw;
    }

    private static File uniqueFile(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; i < 10000; i++) {
            f = new File(dir, base + " (" + i + ")" + ext);
            if (!f.exists()) return f;
        }
        return new File(dir, System.currentTimeMillis() + "_" + name);
    }

    private static long copyFixed(InputStream in, OutputStream out, long len) throws IOException {
        byte[] b = new byte[128 * 1024];
        long left = len;
        long total = 0;
        while (left > 0) {
            int n = in.read(b, 0, (int) Math.min(b.length, left));
            if (n < 0) throw new IOException("unexpected EOF");
            out.write(b, 0, n);
            left -= n;
            total += n;
        }
        return total;
    }

    private static long copyChunked(InputStream in, OutputStream out, long max) throws IOException {
        long total = 0;
        while (true) {
            String line = readLine(in);
            if (line == null) throw new IOException("bad chunk");
            int semi = line.indexOf(';');
            if (semi >= 0) line = line.substring(0, semi);
            int size = Integer.parseInt(line.trim(), 16);
            if (size == 0) {
                while ((line = readLine(in)) != null && !line.isEmpty()) {}
                break;
            }
            total += size;
            if (total > max) throw new IOException("upload too large");
            copyFixed(in, out, size);
            String crlf = readLine(in);
            if (crlf == null || !crlf.isEmpty()) throw new IOException("bad chunk terminator");
        }
        return total;
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int prev = -1;
        int c;
        while ((c = in.read()) != -1) {
            if (prev == '\r' && c == '\n') {
                byte[] a = b.toByteArray();
                int len = Math.max(0, a.length - 1);
                return new String(a, 0, len, StandardCharsets.UTF_8);
            }
            b.write(c);
            prev = c;
            if (b.size() > 65536) throw new IOException("header line too long");
        }
        if (b.size() == 0) return null;
        return new String(b.toByteArray(), StandardCharsets.UTF_8);
    }

    private static Map<String,String> parseQuery(String q) {
        HashMap<String,String> m = new HashMap<>();
        if (q == null || q.isEmpty()) return m;
        for (String p : q.split("&")) {
            int i = p.indexOf('=');
            String k = i < 0 ? p : p.substring(0, i);
            String v = i < 0 ? "" : p.substring(i + 1);
            try {
                k = URLDecoder.decode(k, "UTF-8");
                v = URLDecoder.decode(v, "UTF-8");
            } catch (Throwable ignored) {}
            m.put(k, v);
        }
        return m;
    }

    private static void sendJson(OutputStream out, int code, String json) throws IOException {
        send(out, code, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
    }

    private static void sendHtml(OutputStream out, int code, String html) throws IOException {
        send(out, code, "text/html; charset=utf-8", html.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(OutputStream out, int code, String type, byte[] body) throws IOException {
        String status = code == 200 ? "OK" :
                code == 400 ? "Bad Request" :
                code == 401 ? "Unauthorized" :
                code == 404 ? "Not Found" :
                code == 411 ? "Length Required" :
                code == 413 ? "Payload Too Large" : "Error";
        String h = "HTTP/1.1 " + code + " " + status + "\r\n" +
                "Content-Type: " + type + "\r\n" +
                "Content-Length: " + body.length + "\r\n" +
                "Cache-Control: no-store\r\n" +
                "Connection: close\r\n\r\n";
        out.write(h.getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }

    private String loginPage() {
        return "<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'><title>CarBoxHub</title>" +
                "<style>body{font-family:sans-serif;background:#0f172a;color:#e5e7eb;display:grid;place-items:center;height:100vh;margin:0}" +
                "form{background:#111827;padding:26px;border-radius:16px;width:min(420px,80vw)}" +
                "input,button{width:100%;box-sizing:border-box;padding:12px;margin-top:10px;border-radius:10px;border:1px solid #334155}" +
                "button{background:#2563eb;color:white}</style>" +
                "<form><h2>CarBoxHub</h2><p>请输入车盒屏幕上显示的访问令牌。</p><input name=token autofocus><button>进入</button></form>";
    }
}
