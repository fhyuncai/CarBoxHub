package com.carboxhub.app;

public final class NowPlaying {
    public String sourcePackage = "";
    public String title = "";
    public String artist = "";
    public String album = "";
    public long durationMs = 0;
    public long positionMs = 0;
    public boolean playing = false;
    public long updatedAt = 0;

    public String toJson() {
        return "{" +
                "\"sourcePackage\":" + JsonUtil.q(sourcePackage) + "," +
                "\"title\":" + JsonUtil.q(title) + "," +
                "\"artist\":" + JsonUtil.q(artist) + "," +
                "\"album\":" + JsonUtil.q(album) + "," +
                "\"durationMs\":" + durationMs + "," +
                "\"positionMs\":" + positionMs + "," +
                "\"playing\":" + playing + "," +
                "\"updatedAt\":" + updatedAt +
                "}";
    }
}
