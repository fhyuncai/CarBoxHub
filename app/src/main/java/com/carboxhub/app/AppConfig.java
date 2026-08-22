package com.carboxhub.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.SecureRandom;

public final class AppConfig {
    private static final String PREF = "carboxhub";
    private static final String K_TOKEN = "token";
    private static final String K_PORT = "port";
    private static final String K_WEB_ENABLED = "web_enabled";
    private static final String K_ROOT_INSTALL = "root_install";
    private static final String K_AUTOSTART = "autostart";
    private static final String K_NETEASE = "plugin_netease";
    private static final SecureRandom RNG = new SecureRandom();

    private AppConfig() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static String token(Context c) {
        String token = p(c).getString(K_TOKEN, "");
        if (!isSixDigitToken(token)) {
            token = generateSixDigitToken();
            p(c).edit().putString(K_TOKEN, token).apply();
        }
        return token;
    }

    public static String regenerateToken(Context c) {
        String token = generateSixDigitToken();
        p(c).edit().putString(K_TOKEN, token).apply();
        return token;
    }

    public static int port(Context c) { return p(c).getInt(K_PORT, 8899); }
    public static boolean webEnabled(Context c) { return p(c).getBoolean(K_WEB_ENABLED, true); }
    public static void setWebEnabled(Context c, boolean v) { p(c).edit().putBoolean(K_WEB_ENABLED, v).apply(); }
    public static boolean rootInstall(Context c) { return p(c).getBoolean(K_ROOT_INSTALL, false); }
    public static void setRootInstall(Context c, boolean v) { p(c).edit().putBoolean(K_ROOT_INSTALL, v).apply(); }
    public static boolean autoStart(Context c) { return p(c).getBoolean(K_AUTOSTART, true); }
    public static void setAutoStart(Context c, boolean v) { p(c).edit().putBoolean(K_AUTOSTART, v).apply(); }
    public static boolean neteaseEnabled(Context c) { return p(c).getBoolean(K_NETEASE, true); }
    public static void setNeteaseEnabled(Context c, boolean v) { p(c).edit().putBoolean(K_NETEASE, v).apply(); }

    private static boolean isSixDigitToken(String token) {
        return token != null && token.matches("\\d{6}");
    }

    private static String generateSixDigitToken() {
        int value = RNG.nextInt(1_000_000);
        return String.format("%06d", value);
    }
}
