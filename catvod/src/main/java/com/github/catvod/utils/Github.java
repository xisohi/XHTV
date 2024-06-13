package com.github.catvod.utils;

import android.net.Uri;

import com.github.catvod.net.OkHttp;

import java.io.File;

public class Github {

    //public static final String URL = "http://666.ewwe.gq/fongmi/release/main";

    private static String getUrl(String path, String name) {
        return path + "/" + name;
    }

    public static String getJson(String name) {
        return getUrl("https://xhys.lcjly.cn", name + "leanback.json");
    }

    public static String getApk(String name) {
        return getUrl("https://mirror.ghproxy.com/https://github.com/xisohi/TVBoxOSC/releases/download/XHTV", name + ".apk");
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
