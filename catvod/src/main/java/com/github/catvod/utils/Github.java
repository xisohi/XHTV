package com.github.catvod.utils;

import android.net.Uri;

import com.github.catvod.net.OkHttp;

import java.io.File;

public class Github {

    // public static final String URL = "https://xhys.lcjly.cn";

    private static String getUrl(String path, String name) {
        return path + "/" + name;
    }

    public static String getJson(boolean dev, String name) {
        return getUrl("https://xhys.lcjly.cn/update" , "release.json");
    }

    public static String getApk(boolean dev, String name) {
        return getUrl("https://mirror.ghproxy.com/https://github.com/xisohi/TVBoxOSC/releases/download/release", name + ".apk");
    }

    public static String getSo(String url) {
        try {
            File file = new File(Path.so(), Uri.parse(url).getLastPathSegment());
            if (file.length() < 300) Path.write(file, OkHttp.newCall(url).execute().body().bytes());
            return file.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
}
