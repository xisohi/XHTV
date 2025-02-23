package com.github.catvod.utils;

public class Github {

    private static String getUrl(String path, String name) {
        return path + "/" + name;
    }

    public static String getJson(boolean dev, String name) {
        return getUrl("https://xhys.lcjly.cn/update", "lkys.json");
    }
}