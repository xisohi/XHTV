package com.fongmi.android.tv.utils;

import android.util.Log;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Github {

    private static final String TAG = "Github";
    private static final long SPEED_TEST_INTERVAL = 24 * 60 * 60 * 1000L;

    public static final String[] PROXY_HOSTS = {
            "github.catvod.com",
            "ghproxy.net",
            "gh-proxy.org",
            "ghfast.top",
            "gh.acmsz.top",
            "gh.xisohi.dpdns.org"
    };

    private static List<Integer> speedRanking = new ArrayList<>();
    private static volatile long lastSpeedTestTime = 0;

    private static OkHttpClient httpClient;

    private static synchronized OkHttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(8, TimeUnit.SECONDS)
                    .build();
        }
        return httpClient;
    }

    public static String getJson(String name) {
        return "https://xhys.xisohi.dpdns.org/update/" + name + ".json";
    }

    public static String getApk(String name) {
        String githubUrl = "https://github.com/xisohi/XHYSosc/releases/download/fongmi/" + name + ".apk";
        return getAcceleratedUrl(githubUrl);
    }

    /**
     * 获取加速后的 URL（同步等待测速完成）
     */
    private static String getAcceleratedUrl(String githubUrl) {
        // 如果测速结果为空，先同步测速
        if (speedRanking.isEmpty()) {
            Log.d(TAG, "等待测速完成...");
            speedTestProxiesSync();
        }

        if (!speedRanking.isEmpty()) {
            String fastestProxy = PROXY_HOSTS[speedRanking.get(0)];
            Log.d(TAG, "使用最快代理: " + fastestProxy);
            return "https://" + fastestProxy + "/" + githubUrl;
        }

        Log.w(TAG, "测速失败，使用直连");
        return githubUrl;
    }

    /**
     * 按指定索引获取代理 URL（用于下载失败轮询）
     */
    public static synchronized String getProxyUrlByIndex(String githubUrl, int index) {
        if (speedRanking.isEmpty()) {
            speedTestProxiesSync();
        }
        if (index < 0 || index >= speedRanking.size()) {
            return null;
        }
        int proxyIndex = speedRanking.get(index);
        Log.i(TAG, "切换到代理 " + (index + 1) + "/" + speedRanking.size() + ": " + PROXY_HOSTS[proxyIndex]);
        return "https://" + PROXY_HOSTS[proxyIndex] + "/" + githubUrl;
    }

    /**
     * 获取可用代理数量
     */
    public static synchronized int getProxyCount() {
        return speedRanking.isEmpty() ? PROXY_HOSTS.length : speedRanking.size();
    }

    /**
     * 同步测速（公开方法，供 Updater 调用）
     */
    public static synchronized void speedTestProxiesSync() {
        // 24小时内测速过且结果不为空，跳过
        if (System.currentTimeMillis() - lastSpeedTestTime < SPEED_TEST_INTERVAL && !speedRanking.isEmpty()) {
            Log.d(TAG, "测速缓存有效，跳过测速");
            return;
        }

        Log.d(TAG, "========== 开始测速 ==========");
        List<ProxySpeed> speeds = new ArrayList<>();
        String testUrl = "https://github.com/robots.txt";

        for (int i = 0; i < PROXY_HOSTS.length; i++) {
            String proxyUrl = "https://" + PROXY_HOSTS[i] + "/" + testUrl;
            long start = System.currentTimeMillis();
            boolean reachable = pingProxy(proxyUrl);
            if (reachable) {
                long elapsed = System.currentTimeMillis() - start;
                speeds.add(new ProxySpeed(i, elapsed));
                Log.d(TAG, "代理 " + i + " (" + PROXY_HOSTS[i] + ") 响应: " + elapsed + "ms ✅");
            } else {
                Log.w(TAG, "代理 " + i + " (" + PROXY_HOSTS[i] + ") 不可达 ❌");
            }
        }

        java.util.Collections.sort(speeds, new Comparator<ProxySpeed>() {
            @Override
            public int compare(ProxySpeed o1, ProxySpeed o2) {
                return Long.compare(o1.time, o2.time);
            }
        });

        speedRanking.clear();
        for (ProxySpeed ps : speeds) {
            speedRanking.add(ps.index);
        }

        lastSpeedTestTime = System.currentTimeMillis();

        if (!speedRanking.isEmpty()) {
            Log.i(TAG, "测速完成，最快代理: " + PROXY_HOSTS[speedRanking.get(0)]);
            Log.i(TAG, "代理优先级: " + speedRanking.toString());
        } else {
            Log.w(TAG, "测速完成，无可用代理");
        }
        Log.d(TAG, "========== 测速结束 ==========");
    }

    private static boolean pingProxy(String testUrl) {
        try {
            Request request = new Request.Builder()
                    .url(testUrl)
                    .head()
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build();

            try (Response response = getHttpClient().newCall(request).execute()) {
                int code = response.code();
                if (code > 0) {
                    Log.d(TAG, "ping 响应码: " + code + " (代理可达)");
                    return true;
                }
                return false;
            }
        } catch (Exception e) {
            Log.d(TAG, "OkHttp ping 失败，降级到 Legacy: " + e.getMessage());
            return pingProxyLegacy(testUrl);
        }
    }

    private static boolean pingProxyLegacy(String testUrl) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(testUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            int code = conn.getResponseCode();
            Log.d(TAG, "Legacy ping 响应码: " + code);
            return code >= 200 && code < 400;
        } catch (Exception e) {
            Log.d(TAG, "Legacy 测速失败: " + e.getMessage());
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public static synchronized void forceSpeedTest() {
        lastSpeedTestTime = 0;
        speedRanking.clear();
        speedTestProxiesSync();
    }

    public static String getProxyStatus() {
        StringBuilder status = new StringBuilder("\n========== GitHub 代理状态 ==========\n");
        if (!speedRanking.isEmpty()) {
            status.append("最快代理: ").append(PROXY_HOSTS[speedRanking.get(0)]).append("\n");
            status.append("代理优先级:\n");
            for (int i = 0; i < speedRanking.size(); i++) {
                status.append("  ").append(i + 1).append(". ")
                        .append(PROXY_HOSTS[speedRanking.get(i)]).append("\n");
            }
        } else {
            status.append("未测速或测速失败\n");
        }
        status.append("=====================================");
        return status.toString();
    }

    private static class ProxySpeed {
        int index;
        long time;
        ProxySpeed(int index, long time) {
            this.index = index;
            this.time = time;
        }
    }
}