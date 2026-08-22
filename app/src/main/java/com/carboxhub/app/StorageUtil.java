package com.carboxhub.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

import java.io.File;

public final class StorageUtil {
    private StorageUtil() {}

    public static boolean hasLegacyStoragePermission(Context c) {
        return Build.VERSION.SDK_INT < 23 ||
                c.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    public static File uploadDir(Context c) {
        try {
            if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) && hasLegacyStoragePermission(c)) {
                File d = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "CarBoxHub");
                if ((d.exists() || d.mkdirs()) && d.canWrite()) return d;
            }
        } catch (Throwable ignored) {}

        File base = c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (base == null) base = new File(c.getFilesDir(), "downloads");
        File d = new File(base, "uploads");
        if (!d.exists()) d.mkdirs();
        return d;
    }
}
