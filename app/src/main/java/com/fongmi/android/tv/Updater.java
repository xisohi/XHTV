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

    private String getApk() {
        return Github.getApk(BuildConfig.FLAVOR_mode + "-" + BuildConfig.FLAVOR_abi);
    }

    private void createDownload() {
        if (download != null) {
            download.cancel();
        }
        download = Download.create(getApk(), getFile());
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
            // 1. 检查更新
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

            // ===== 2. 发现新版本，先同步测速 =====
            Log.i(TAG, "发现新版本! " + BuildConfig.VERSION_CODE + " -> " + code);
            Log.i(TAG, "开始测速...");
            Github.speedTestProxiesSync();
            Log.i(TAG, "测速完成，最快代理: " + (Github.getProxyStatus()));

            // 3. 显示更新对话框
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
        createDownload();
        download.start(this);
    }

    @Override
    public void onCancel(View view) {
        Setting.putUpdate(false);
        if (download != null) {
            download.cancel();
        }
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

        // ===== 尝试切换代理重试 =====
        if (download != null) {
            boolean switched = download.switchToNextProxy();
            if (switched) {
                Log.i(TAG, "切换代理重试...");
                Notify.show("切换代理重试...");
                download.retry();
                return;
            }
        }

        // 所有代理都失败，使用直连
        Log.w(TAG, "所有代理失败，使用直连");
        if (download != null) {
            download.retry();
            return;
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