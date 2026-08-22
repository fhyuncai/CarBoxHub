package com.carboxhub.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.security.SecureRandom;

public final class AppConfig {
    public static final String ACTION_CONFIG_CHANGED = "com.carboxhub.app.action.CONFIG_CHANGED";
    public static final String EXTRA_KEY = "key";

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

    private static void changed(Context c, String key) {
        Intent i = new Intent(ACTION_CONFIG_CHANGED);
        i.setPackage(c.getPackageName());
        i.putExtra(EXTRA_KEY, key);
        c.getApplicationContext().sendBroadcast(i);
    }

    public static String token(Context c) {
        String token = p(c).getString(K_TOKEN, "");
        if (!isSixDigitToken(token)) {
            token = generateSixDigitToken();
            p(c).edit().putString(K_TOKEN, token).apply();
            changed(c, K_TOKEN);
        }
        return token;
    }

    public static String regenerateToken(Context c) {
        String token = generateSixDigitToken();
        p(c).edit().putString(K_TOKEN, token).apply();
        changed(c, K_TOKEN);
        return token;
    }

    public static int port(Context c) { return p(c).getInt(K_PORT, 8899); }
    public static boolean webEnabled(Context c) { return p(c).getBoolean(K_WEB_ENABLED, true); }
    public static void setWebEnabled(Context c, boolean v) {
        if (webEnabled(c) == v) return;
        p(c).edit().putBoolean(K_WEB_ENABLED, v).apply();
        changed(c, K_WEB_ENABLED);
    }
    public static boolean rootInstall(Context c) { return p(c).getBoolean(K_ROOT_INSTALL, false); }
    public static void setRootInstall(Context c, boolean v) {
        if (rootInstall(c) == v) return;
        p(c).edit().putBoolean(K_ROOT_INSTALL, v).apply();
        changed(c, K_ROOT_INSTALL);
    }
    public static boolean autoStart(Context c) { return p(c).getBoolean(K_AUTOSTART, true); }
    public static void setAutoStart(Context c, boolean v) {
        if (autoStart(c) == v) return;
        p(c).edit().putBoolean(K_AUTOSTART, v).apply();
        changed(c, K_AUTOSTART);
    }
    public static boolean neteaseEnabled(Context c) { return p(c).getBoolean(K_NETEASE, true); }
    public static void setNeteaseEnabled(Context c, boolean v) {
        if (neteaseEnabled(c) == v) return;
        p(c).edit().putBoolean(K_NETEASE, v).apply();
        changed(c, K_NETEASE);
    }

    private static boolean isSixDigitToken(String token) {
        return token != null && token.matches("\\d{6}");
    }

    private static String generateSixDigitToken() {
        int value = RNG.nextInt(1_000_000);
        return String.format("%06d", value);
    }
}
