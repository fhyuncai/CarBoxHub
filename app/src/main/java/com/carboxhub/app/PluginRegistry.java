package com.carboxhub.app;

import android.content.Context;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PluginRegistry {
    private static final List<Plugin> PLUGINS;
    static {
        ArrayList<Plugin> p = new ArrayList<>();
        p.add(new NeteaseMediaPlugin());
        PLUGINS = Collections.unmodifiableList(p);
    }

    private PluginRegistry() {}

    public static List<Plugin> all() { return PLUGINS; }

    public static Plugin find(String id) {
        for (Plugin p : PLUGINS) if (p.id().equals(id)) return p;
        return null;
    }

    public static void startEnabled(Context c) {
        for (Plugin p : PLUGINS) if (p.isEnabled(c)) p.start(c);
    }

    public static void stopAll(Context c) {
        for (Plugin p : PLUGINS) p.stop(c);
    }

    public static String json(Context c) {
        StringBuilder b = new StringBuilder("[");
        boolean first = true;
        for (Plugin p : PLUGINS) {
            if (!first) b.append(',');
            first = false;
            b.append('{')
             .append("\"id\":").append(JsonUtil.q(p.id())).append(',')
             .append("\"name\":").append(JsonUtil.q(p.name())).append(',')
             .append("\"enabled\":").append(p.isEnabled(c)).append(',')
             .append("\"state\":").append(p.statusJson(c))
             .append('}');
        }
        return b.append(']').toString();
    }
}
