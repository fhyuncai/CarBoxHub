package com.carboxhub.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!AppConfig.autoStart(context)) return;
        Intent s = new Intent(context, LanServerService.class);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(s); else context.startService(s);
    }
}
