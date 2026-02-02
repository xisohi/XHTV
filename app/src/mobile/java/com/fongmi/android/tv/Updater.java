package com.fongmi.android.tv;

import android.app.Activity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

import com.fongmi.android.tv.databinding.DialogUpdateBinding;
import com.fongmi.android.tv.utils.Download;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Github;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.io.File;
import java.util.Locale;

/**
 * 应用更新管理器
 * 功能：检查更新、下载APK、自动切换代理重试
 */
public class Updater implements Download.Callback {
    private static final String TAG = "Updater";
    private static final int MAX_RETRY_COUNT = 3; // 最大代理切换次数

    private DialogUpdateBinding binding;
    private final Download download;
    private AlertDialog dialog;
    private String apkName;
    private int retryCount; // 重试计数器

    private File getFile() {
        return Path.cache("update.apk");
    }

    private String getJson() {
        String url = Github.getJson("lkys");
        Log.d(TAG, "JSON请求地址: " + url);
        return url;
    }

    private String getApk() {
        apkName = BuildConfig.FLAVOR_mode + "-" + BuildConfig.FLAVOR_abi;
        String url = Github.getApk(apkName);
        Log.d(TAG, "APK下载地址: " + url);
        return url;
    }

    public static Updater create() {
        return new Updater();
    }

    public Updater() {
        this.download = Download.create("", getFile());
        this.retryCount = 0; // 初始化计数器
    }

    public Updater force() {
        Notify.show(R.string.update_check);
        Setting.putUpdate(true);
        return this;
    }

    private Updater check() {
        dismiss();
        return this;
    }

    public void start(Activity activity) {
        if (!Setting.getUpdate()) return;
        Log.i(TAG, "开始检查更新...");
        App.execute(() -> doInBackground(activity));
    }

    private void doInBackground(Activity activity) {
        try {
            String jsonUrl = getJson();
            Log.i(TAG, "正在请求JSON: " + jsonUrl);

            String jsonContent = OkHttp.string(jsonUrl);
            Log.d(TAG, "JSON返回内容:\n" + jsonContent);

            JSONObject object = new JSONObject(jsonContent);
            String name = object.optString("name");
            String desc = object.optString("desc");
            int code = object.optInt("code");

            Log.d(TAG, "解析JSON - name: " + name + ", versionCode: " + code + ", desc: " + desc);

            if (code > BuildConfig.VERSION_CODE) {
                Log.i(TAG, "发现新版本，当前版本: " + BuildConfig.VERSION_CODE + ", 最新版本: " + code);
                App.post(() -> show(activity, name, desc));
            } else {
                Log.i(TAG, "当前已是最新版本: " + BuildConfig.VERSION_CODE);
            }
        } catch (Exception e) {
            Log.e(TAG, "更新检查失败: " + e.getMessage(), e);
        }
    }

    private void show(Activity activity, String version, String desc) {
        binding = DialogUpdateBinding.inflate(LayoutInflater.from(activity));
        binding.version.setText(ResUtil.getString(R.string.update_version, version));
        binding.confirm.setOnClickListener(this::confirm);
        binding.cancel.setOnClickListener(this::cancel);
        check().create(activity).show();
        binding.desc.setText(desc);

        retryCount = 0;       // 修复：重置重试计数器
        Github.resetProxy();  // 重置代理为首选
    }

    private AlertDialog create(Activity activity) {
        return dialog = new MaterialAlertDialogBuilder(activity).setView(binding.getRoot()).setCancelable(false).create();
    }

    private void cancel(View view) {
        Setting.putUpdate(false);
        download.cancel();
        dismiss();
    }

    private void confirm(View view) {
        view.setEnabled(false);
        Log.i(TAG, "用户确认更新，开始下载...");

        String apkUrl = getApk();
        Log.i(TAG, "代理状态:\n" + Github.getProxyStatus());

        download.setUrl(apkUrl).start(this);
    }

    private void dismiss() {
        try {
            if (dialog != null) dialog.dismiss();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void progress(int progress) {
        binding.confirm.setText(String.format(Locale.getDefault(), "%1$d%%", progress));
        if (progress % 10 == 0) {
            Log.d(TAG, "下载进度: " + progress + "%");
        }
    }

    @Override
    public void error(String msg) {
        Log.e(TAG, "下载失败: " + msg);

        // 修复：使用异步延迟避免UI线程阻塞
        if (retryCount < MAX_RETRY_COUNT) {
            retryCount++;
            Log.w(TAG, String.format("第%d/%d次失败，%d秒后切换代理重试...", retryCount, MAX_RETRY_COUNT, retryCount * 2));

            App.execute(() -> { // 在后台线程执行延迟
                try {
                    Thread.sleep(retryCount * 2000);
                } catch (InterruptedException ignored) {}

                Github.switchToNextProxy();
                String newUrl = getApk();
                Log.i(TAG, "切换后新地址: " + newUrl);

                // 更新UI（必须在主线程）
                App.post(() -> {
                    binding.confirm.setText(String.format("重试中(%d/%d)...", retryCount, MAX_RETRY_COUNT));
                });

                download.setUrl(newUrl).start(this);
            });

        } else {
            Log.e(TAG, "所有代理均失败，终止下载");
            Notify.show("下载失败，已尝试所有代理: " + msg);
            dismiss();
        }
    }

    @Override
    public void success(File file) {
        Log.i(TAG, "下载成功，文件路径: " + file.getAbsolutePath());
        FileUtil.openFile(file);
        dismiss();
    }
}