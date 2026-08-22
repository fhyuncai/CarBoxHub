package com.carboxhub.app;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public final class RootShell {
    private static volatile long lastCheckAt = 0;
    private static volatile boolean lastAvailable = false;
    private RootShell() {}

    public static synchronized boolean isAvailable() {
        long now = System.currentTimeMillis();
        if (now - lastCheckAt < 30000) return lastAvailable;
        try {
            Process p = new ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start();
            boolean done = p.waitFor(3, TimeUnit.SECONDS);
            if (!done) { p.destroy(); lastAvailable = false; lastCheckAt = now; return false; }
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = r.readLine();
            lastAvailable = p.exitValue() == 0 && line != null && line.contains("uid=0");
            lastCheckAt = now;
            return lastAvailable;
        } catch (Throwable t) {
            lastAvailable = false;
            lastCheckAt = now;
            return false;
        }
    }

    public static Result exec(String command) {
        try {
            Process p = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) out.append(line).append('\n');
            boolean done = p.waitFor(120, TimeUnit.SECONDS);
            if (!done) { p.destroy(); return new Result(false, "root command timeout"); }
            return new Result(p.exitValue() == 0, out.toString().trim());
        } catch (Throwable t) {
            return new Result(false, t.toString());
        }
    }

    public static String shQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    public static final class Result {
        public final boolean ok;
        public final String output;
        public Result(boolean ok, String output) { this.ok = ok; this.output = output; }
    }
}
