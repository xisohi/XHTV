package com.fongmi.android.tv.utils;

public class Github {

    private static String getUrl(String path, String name) {
        return path + "/" + name;
    }

    public static String getJson(String name) {
        return getUrl("https://xhys.lcjly.cn/update","lkys.json");
    }
}