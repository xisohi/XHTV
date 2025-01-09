package com.github.catvod.utils;

public class Github {

//    public static final String URL = "https://raw.githubusercontent.com/FongMi/Release/fongmi";

    private static String getUrl(String path, String name) {
        return path + "/" + name;
    }

    public static String getJson(boolean dev, String name) {
        return getUrl("https://xhys.lcjly.cn/update" , "lkys.json");
    }

    public static String getApk(boolean dev, String name) {
        return getUrl("https://mirror.ghproxy.com/https://github.com/xisohi/TVBoxOSC/releases/download/lkys", name + ".apk");
    }
}
