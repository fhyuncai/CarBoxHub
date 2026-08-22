package com.carboxhub.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.SecureRandom;

public final class AppConfig {
    private static final String PREF = "carboxhub";
    private static final String K_TOKEN = "token";
    private static final String K_PORT = "port";
    private static final String K_ROOT_INSTALL = "root_install";
    private static final String K_AUTOSTART = "autostart";
    private static final String K_NETEASE = "plugin_netease";

    private AppConfig() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static String token(Context c) {
        String token = p(c).getString(K_TOKEN, "");
        if (token == null || token.length() < 12) {
            byte[] b = new byte[12];
            new SecureRandom().nextBytes(b);
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x & 0xff));
            token = sb.toString();
            p(c).edit().putString(K_TOKEN, token).apply();
        }
        return token;
    }

    public static int port(Context c) { return p(c).getInt(K_PORT, 8899); }
    public static boolean rootInstall(Context c) { return p(c).getBoolean(K_ROOT_INSTALL, false); }
    public static void setRootInstall(Context c, boolean v) { p(c).edit().putBoolean(K_ROOT_INSTALL, v).apply(); }
    public static boolean autoStart(Context c) { return p(c).getBoolean(K_AUTOSTART, true); }
    public static void setAutoStart(Context c, boolean v) { p(c).edit().putBoolean(K_AUTOSTART, v).apply(); }
    public static boolean neteaseEnabled(Context c) { return p(c).getBoolean(K_NETEASE, true); }
    public static void setNeteaseEnabled(Context c, boolean v) { p(c).edit().putBoolean(K_NETEASE, v).apply(); }
}
