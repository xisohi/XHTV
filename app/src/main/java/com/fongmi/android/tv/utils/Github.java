package com.fongmi.android.tv.utils;

public class Github {

    public static final String URL = "https://raw.githubusercontent.com/FongMi/Release/fongmi";

    private static String getUrl(String name) {
        return URL + "/apk/" + name;
    }

    /**
     * JSON配置文件地址（你的自定义服务器）
     * 示例: https://xhys.lcjly.cn/update/lkys.json
     */
    public static String getJson(String name) {
        return "https://xhys.lcjly.cn/update/" + name + ".json";
    }

    /**
     * APK下载地址（GitHub Releases）
     * 示例: https://github.com/xisohi/XHYSosc/releases/download/lkys/lkys-armeabi_v7a.apk
     */
    public static String getApk(String name) {
        return "https://ghfast.top/https://github.com/xisohi/XHYSosc/releases/download/lkys/" + name + ".apk";
    }
}
