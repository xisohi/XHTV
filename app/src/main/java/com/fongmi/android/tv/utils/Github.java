package com.fongmi.android.tv.utils;

public class Github {

    public static final String URL = "https://xhys.lcjly.cn";

    private static String getUrl(String name) {
        return URL + "/update/" + name;
    }

    public static String getJson(String name) {
        return getUrl(name + ".json");
    }

    public static String getApk(String name) {
        return getUrl(name + ".apk");
    }
}
