package com.fongmi.android.tv;

import android.app.Activity;
import android.util.Log;  // 添加日志导入
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
import com.fongmi.android.tv.BuildConfig;
import org.json.JSONObject;

import java.io.File;
import java.util.Locale;

public class Updater implements Download.Callback {
    private static final String TAG = "Updater"; // 日志标签

    private DialogUpdateBinding binding;
    private final Download download;
    private AlertDialog dialog;

    private File getFile() {
        return Path.cache("update.apk");
    }

    private String getJson() {
        String url = Github.getJson("lkys");  // 示例: https://xhys.lcjly.cn/update/lkys.json
        Log.d(TAG, "JSON请求地址: " + url);  // 打印JSON地址
        return url;
    }

    private String getApk() {
        String url = Github.getApk(BuildConfig.FLAVOR_mode + "-" + BuildConfig.FLAVOR_abi);
        Log.d(TAG, "APK下载地址: " + url);  // 打印APK地址
        return url;
    }

    public static Updater create() {
        return new Updater();
    }

    public Updater() {
        String apkUrl = getApk();  // 获取APK地址
        Log.i(TAG, "初始化下载器，APK地址: " + apkUrl);  // 打印APK地址
        this.download = Download.create(apkUrl, getFile());
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
        Log.i(TAG, "开始检查更新...");  // 添加日志
        App.execute(() -> doInBackground(activity));
    }

    private void doInBackground(Activity activity) {
        try {
            String jsonUrl = getJson();  // 获取JSON地址
            Log.i(TAG, "正在请求JSON: " + jsonUrl);

            String jsonContent = OkHttp.string(jsonUrl);
            Log.d(TAG, "JSON返回内容:\n" + jsonContent);  // 打印JSON内容

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
            Log.e(TAG, "更新检查失败: " + e.getMessage(), e);  // 错误日志
            e.printStackTrace();
        }
    }

    private void show(Activity activity, String version, String desc) {
        binding = DialogUpdateBinding.inflate(LayoutInflater.from(activity));
        binding.version.setText(ResUtil.getString(R.string.update_version, version));
        binding.confirm.setOnClickListener(this::confirm);
        binding.cancel.setOnClickListener(this::cancel);
        check().create(activity).show();
        binding.desc.setText(desc);
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
        Log.i(TAG, "用户确认更新，开始下载...");  // 添加日志
        download.start(this);
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
        if (progress % 10 == 0) {  // 每10%打印一次，避免日志过多
            Log.d(TAG, "下载进度: " + progress + "%");
        }
    }

    @Override
    public void error(String msg) {
        Log.e(TAG, "下载失败: " + msg);  // 错误日志
        Notify.show(msg);
        dismiss();
    }

    @Override
    public void success(File file) {
        Log.i(TAG, "下载成功，文件路径: " + file.getAbsolutePath());  // 成功日志
        FileUtil.openFile(file);
        dismiss();
    }
}