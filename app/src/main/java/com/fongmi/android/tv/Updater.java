package com.fongmi.android.tv;

import android.view.View;
import android.util.Log;

import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.impl.UpdateListener;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.dialog.UpdateDialog;
import com.fongmi.android.tv.utils.Download;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Github;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;

import org.json.JSONObject;

import java.io.File;

public class Updater implements Download.Callback, UpdateListener {

    private static final String TAG = "Updater";

    private Download download;
    private UpdateDialog dialog;

    private String originalApkUrl;      // 原始 GitHub URL（不含代理）
    private int proxyRetryIndex = 0;    // 当前尝试的代理索引（0~n-1），-1 表示直连

    private Updater() {
    }

    public static Updater create() {
        return new Updater();
    }

    private File getFile() {
        return Path.cache("update.apk");
    }

    private String getJson() {
        return Github.getJson("fongmi");
    }

    // 不再使用 Github.getApk 的自动加速，自行控制
    private String getRawApkUrl() {
        return Github.getApk(BuildConfig.FLAVOR_mode + "-" + BuildConfig.FLAVOR_abi);
    }

    private void clearDownload() {
        if (download != null) {
            download.cancel();
            download = null;
        }
        // 删除可能残留的旧文件，避免断点续传导致数据混乱
        File file = getFile();
        if (file.exists()) {
            file.delete();
        }
    }

    private void startDownloadWithCurrentProxy() {
        clearDownload();

        String finalUrl;
        if (proxyRetryIndex >= 0) {
            String proxyUrl = Github.getProxyUrlByIndex(originalApkUrl, proxyRetryIndex);
            if (proxyUrl != null) {
                finalUrl = proxyUrl;
                Log.i(TAG, "使用代理 " + (proxyRetryIndex + 1) + "/" + Github.getProxyCount() + ": " + finalUrl);
            } else {
                // 代理索引无效，回退直连
                Log.w(TAG, "代理索引无效，回退直连");
                proxyRetryIndex = -1;
                finalUrl = originalApkUrl;
            }
        } else {
            finalUrl = originalApkUrl;
            Log.i(TAG, "使用直连: " + finalUrl);
        }

        download = Download.create(finalUrl, getFile());
        download.start(this);
    }

    public Updater force() {
        Notify.show(R.string.update_check);
        Setting.putUpdate(true);
        return this;
    }

    public void start(FragmentActivity activity) {
        if (!Setting.getUpdate()) return;
        Task.execute(() -> doInBackground(activity));
    }

    private void doInBackground(FragmentActivity activity) {
        try {
            String jsonUrl = getJson();
            Log.d(TAG, "请求 JSON URL: " + jsonUrl);

            String response = OkHttp.string(jsonUrl);
            Log.d(TAG, "返回内容: " + response.substring(0, Math.min(200, response.length())));

            JSONObject object = new JSONObject(response);
            String name = object.optString("name");
            String desc = object.optString("desc");
            int code = object.optInt("code");

            Log.d(TAG, "解析结果 - name: " + name + ", code: " + code + ", 当前版本: " + BuildConfig.VERSION_CODE);

            if (code <= BuildConfig.VERSION_CODE) {
                Log.i(TAG, "当前已是最新版本");
                return;
            }

            // 发现新版本，先同步测速（获取代理排序）
            Log.i(TAG, "发现新版本! " + BuildConfig.VERSION_CODE + " -> " + code);
            Log.i(TAG, "开始测速...");
            Github.speedTestProxiesSync();
            Log.i(TAG, "测速完成，代理状态: " + Github.getProxyStatus());

            // 保存原始 APK URL
            originalApkUrl = getRawApkUrl();
            // 重置重试索引
            proxyRetryIndex = 0;

            // 显示更新对话框
            App.post(() -> show(activity, name, desc));

        } catch (Exception e) {
            Log.e(TAG, "========== 更新检查失败 ==========");
            Log.e(TAG, "错误类型: " + e.getClass().getSimpleName());
            Log.e(TAG, "错误信息: " + e.getMessage());
            Log.e(TAG, "完整堆栈:", e);
        }
        Log.d(TAG, "========== 更新检查结束 ==========");
    }

    private void show(FragmentActivity activity, String version, String desc) {
        dismiss();
        dialog = UpdateDialog.create()
                .title(ResUtil.getString(R.string.update_version, version))
                .desc(desc)
                .listener(this)
                .show(activity);
    }

    @Override
    public void onConfirm(View view) {
        view.setEnabled(false);
        // 如果尚未获取原始 URL（极少数情况），再次获取
        if (originalApkUrl == null) {
            originalApkUrl = getRawApkUrl();
        }
        proxyRetryIndex = 0;  // 从最快代理开始
        startDownloadWithCurrentProxy();
    }

    @Override
    public void onCancel(View view) {
        Setting.putUpdate(false);
        clearDownload();
        dismiss();
    }

    private void dismiss() {
        try {
            if (dialog != null) {
                dialog.dismiss();
                dialog = null;
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void progress(int progress) {
        if (dialog != null) {
            dialog.setProgress(progress);
        }
    }

    @Override
    public void error(String msg) {
        Log.e(TAG, "下载失败: " + msg);

        // 如果当前是代理模式（proxyRetryIndex >= 0），尝试下一个代理
        if (proxyRetryIndex >= 0) {
            int nextIndex = proxyRetryIndex + 1;
            if (nextIndex < Github.getProxyCount()) {
                proxyRetryIndex = nextIndex;
                Log.i(TAG, "切换到下一个代理，索引 " + proxyRetryIndex);
                startDownloadWithCurrentProxy();
                return;
            } else {
                // 所有代理均失败，尝试直连
                Log.w(TAG, "所有代理均失败，尝试直连");
                proxyRetryIndex = -1;   // 标记为直连模式
                startDownloadWithCurrentProxy();
                return;
            }
        }

        // 直连也失败，彻底放弃
        Notify.show(msg);
        dismiss();
    }

    @Override
    public void success(File file) {
        FileUtil.openFile(file);
        dismiss();
    }
}