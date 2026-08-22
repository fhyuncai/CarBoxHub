package com.carboxhub.app;

import android.graphics.Bitmap;
import android.graphics.Color;

public final class QrBitmap {
    private QrBitmap() {}

    public static Bitmap create(String text, int requestedPx) {
        boolean[][] qr = QrCodeEncoder.encode(text);
        int border = 4;
        int modules = qr.length + border * 2;
        int scale = Math.max(1, requestedPx / modules);
        int px = modules * scale;
        Bitmap bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.WHITE);

        for (int r = 0; r < qr.length; r++) {
            for (int c = 0; c < qr.length; c++) {
                if (!qr[r][c]) continue;
                int left = (c + border) * scale;
                int top = (r + border) * scale;
                for (int y = top; y < top + scale; y++) {
                    for (int x = left; x < left + scale; x++) bitmap.setPixel(x, y, Color.BLACK);
                }
            }
        }
        return bitmap;
    }
}
