package com.fongmi.android.tv;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.view.View;

import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.impl.UpdateListener;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.dialog.UpdateDialog;
import com.fongmi.android.tv.utils.Download;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Github;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;

import org.json.JSONObject;

import java.io.File;

public class Updater implements Download.Callback, UpdateListener {

    private static final String TAG = "Updater";
    private static final int MAX_RETRY_COUNT = 4;

    private Download download;
    private UpdateDialog dialog;
    private String apkName;
    private int retryCount;
    private boolean isDownloading = false;

    private Updater() {
    }

    public static Updater create() {
        return new Updater();
    }

    private File getFile() {
        return Path.cache("update.apk");
    }

    private String getJson() {
        String url = Github.getJson("fongmi");
        Log.d(TAG, "JSON请求地址: " + url);
        return url;
    }

    private String getApk() {
        apkName = BuildConfig.FLAVOR_mode + "-" + BuildConfig.FLAVOR_abi;
        String url = Github.getApk(apkName);
        Log.d(TAG, "APK下载地址: " + url);
        return url;
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
        Log.i(TAG, "开始检查更新...");
        App.execute(() -> doInBackground(activity));  // ✅ 使用 App.execute
    }

    private void doInBackground(FragmentActivity activity) {
        try {
            String jsonUrl = getJson();
            Log.i(TAG, "正在请求JSON: " + jsonUrl);

            String jsonContent = OkHttp.string(jsonUrl);
            Log.d(TAG, "JSON返回内容:\n" + jsonContent);

            JSONObject object = new JSONObject(jsonContent);
            String name = object.optString("name");
            String desc = object.optString("desc");
            int code = object.optInt("code");

            Log.d(TAG, "解析JSON - name: " + name + ", versionCode: " + code);

            if (code > BuildConfig.VERSION_CODE) {
                Log.i(TAG, "发现新版本，当前: " + BuildConfig.VERSION_CODE + ", 最新: " + code);
                App.post(() -> show(activity, name, desc));  // ✅ 使用 App.post
            } else {
                Log.i(TAG, "当前已是最新版本: " + BuildConfig.VERSION_CODE);
            }

        } catch (Exception e) {
            Log.e(TAG, "更新检查失败: " + e.getMessage(), e);
        }
    }

    private void show(FragmentActivity activity, String version, String desc) {
        dismiss();
        retryCount = 0;
        isDownloading = false;
        dialog = UpdateDialog.create()
                .title(ResUtil.getString(R.string.update_version, version))
                .desc(desc)
                .listener(this)
                .show(activity);
    }

    @Override
    public void onConfirm(View view) {
        if (isDownloading) return;

        view.setEnabled(false);
        isDownloading = true;
        retryCount = 0;
        Github.resetProxy();
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
        if (dialog != null && progress >= 0 && progress <= 100) {
            dialog.setProgress(progress);
        }
    }

    @Override
    public void error(String msg) {
        Log.e(TAG, "下载失败: " + msg);

        if (retryCount < MAX_RETRY_COUNT) {
            retryCount++;
            Log.w(TAG, String.format("第%d/%d次失败，切换代理重试...", retryCount, MAX_RETRY_COUNT));

            // ✅ 使用 Notify 提示，不直接操作 dialog 内部控件
            Notify.show("下载失败，切换服务器重试 (" + retryCount + "/" + MAX_RETRY_COUNT + ")");

            Github.switchToNextProxy();

            // ✅ 使用 App.post 延迟重试
            App.post(() -> {
                if (download != null) {
                    String newUrl = Github.getApk(apkName);
                    Log.i(TAG, "切换后新地址: " + newUrl);
                    download.setUrl(newUrl).start(this);
                }
            }, 1500);

        } else {
            Log.e(TAG, "所有代理均失败，终止下载");
            Notify.show("下载失败，请检查网络后重试");
            isDownloading = false;
            dismiss();
            Setting.putUpdate(true);
        }
    }

    @Override
    public void success(File file) {
        // 1. 基础信息日志
        Log.i(TAG, "========== 下载成功 ==========");
        Log.i(TAG, "文件路径: " + file.getAbsolutePath());
        Log.i(TAG, "文件大小: " + file.length() + " bytes (" + (file.length() / 1024) + " KB)");
        Log.i(TAG, "文件是否存在: " + file.exists());
        Log.i(TAG, "文件可读: " + file.canRead());

        // 2. Android 版本和权限状态
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            boolean canInstall = App.get().getPackageManager().canRequestPackageInstalls();
            Log.i(TAG, "Android版本: " + Build.VERSION.SDK_INT);
            Log.i(TAG, "是否拥有安装未知应用权限: " + canInstall);
            if (!canInstall) {
                Log.w(TAG, "未授权安装未知应用，将跳转设置页面");
                Notify.show("需要授权安装未知应用才能自动更新");
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                intent.setData(Uri.parse("package:" + App.get().getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                App.get().startActivity(intent);
                isDownloading = false;
                return;
            }
        }

        // 3. 调用安装前的日志
        Log.i(TAG, "权限检查通过，开始调用 FileUtil.openFile()");
        isDownloading = false;
        try {
            FileUtil.openFile(file);
            Log.i(TAG, "FileUtil.openFile() 调用完成");
        } catch (Exception e) {
            Log.e(TAG, "FileUtil.openFile() 异常: " + Log.getStackTraceString(e));
        }
        dismiss();
    }
}