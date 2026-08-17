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

    private String originalApkUrl;
    private int proxyRetryIndex = 0; // -1 表示直连

    private Updater() {}

    public static Updater create() {
        return new Updater();
    }

    private File getFile() {
        return Path.cache("update.apk");
    }

    private String getJson() {
        return Github.getJson("fongmi");
    }

    private String getRawApkUrl() {
        return Github.getApk(BuildConfig.FLAVOR_mode + "-" + BuildConfig.FLAVOR_abi);
    }

    private void clearDownload() {
        if (download != null) {
            download.cancel();
            download = null;
        }
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

            // ---------- 发现新版本，立即弹窗 ----------
            Log.i(TAG, "发现新版本! " + BuildConfig.VERSION_CODE + " -> " + code);
            originalApkUrl = getRawApkUrl();
            proxyRetryIndex = 0;

            App.post(() -> show(activity, name, desc));

        } catch (Exception e) {
            Log.e(TAG, "更新检查失败", e);
        }
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

        if (dialog != null) {
            dialog.setStatus("正在选择最优下载线路...");
        }

        Task.execute(() -> {
            try {
                Log.i(TAG, "开始测速...");
                Github.speedTestProxiesSync();
                Log.i(TAG, "测速完成，代理状态: " + Github.getProxyStatus());
            } catch (Exception e) {
                Log.e(TAG, "测速异常", e);
            }
            App.post(() -> {
                if (dialog != null) {
                    // 测速完成，准备下载，先显示“准备下载”状态，稍后进度更新会覆盖
                    dialog.setStatus("准备下载…");
                }
                startDownloadWithCurrentProxy();
            });
        });
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
        } catch (Exception ignored) {}
    }

    // ---------- Download.Callback 实现 ----------
    @Override
    public void progress(int progress) {
        Log.d(TAG, "progress callback: " + progress + ", dialog=" + dialog);
        if (dialog != null) {
            // 只要进度 >= 0 就更新百分比，覆盖任何状态文字
            if (progress >= 0) {
                dialog.setProgress(progress);
            } else {
                // 如果传入负数（目前不会），可以显示其他状态
                dialog.setStatus("下载中…");
            }
        } else {
            Log.w(TAG, "dialog is null, cannot update progress");
        }
    }

    @Override
    public void error(String msg) {
        Log.e(TAG, "下载失败: " + msg);

        if (proxyRetryIndex >= 0) {
            int nextIndex = proxyRetryIndex + 1;
            if (nextIndex < Github.getProxyCount()) {
                proxyRetryIndex = nextIndex;
                Log.i(TAG, "切换到下一个代理，索引 " + proxyRetryIndex);
                if (dialog != null) {
                    dialog.setStatus("切换代理重试…");
                }
                startDownloadWithCurrentProxy();
                return;
            } else {
                Log.w(TAG, "所有代理均失败，尝试直连");
                proxyRetryIndex = -1;
                if (dialog != null) {
                    dialog.setStatus("使用直连…");
                }
                startDownloadWithCurrentProxy();
                return;
            }
        }

        Notify.show(msg);
        dismiss();
    }

    @Override
    public void success(File file) {
        FileUtil.openFile(file);
        dismiss();
    }
}