package com.carboxhub.app;

import android.content.Context;

public interface Plugin {
    String id();
    String name();
    boolean isEnabled(Context context);
    void setEnabled(Context context, boolean enabled);
    void start(Context context);
    void stop(Context context);
    String statusJson(Context context);
}
