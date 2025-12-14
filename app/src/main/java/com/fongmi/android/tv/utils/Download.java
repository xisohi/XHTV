package com.fongmi.android.tv.utils;

import android.util.Log;

import com.fongmi.android.tv.App;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.google.common.net.HttpHeaders;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.concurrent.Future;

import okhttp3.Response;

/**
 * 文件下载工具类
 * 支持动态URL切换和弱引用防内存泄漏
 */
public class Download {

    private final File file;
    private String url;
    private String tag;
    private WeakReference<Callback> callbackRef;
    private Future<?> future;

    public static Download create(String url, File file) {
        return new Download(url, file);
    }

    public Download(String url, File file) {
        this.tag = url;
        this.url = url;
        this.file = file;
    }

    /**
     * 动态设置下载URL（用于切换代理）
     * @param url 新的下载地址
     * @return this
     */
    public synchronized Download setUrl(String url) {
        this.url = url;
        this.tag = url; // tag同步更新，确保取消时能正确匹配
        return this;
    }

    public Download tag(String tag) {
        this.tag = tag;
        return this;
    }

    public File get() {
        doInBackground();
        return file;
    }

    public void start(Callback callback) {
        this.callbackRef = new WeakReference<>(callback);
        future = App.submit(this::doInBackground);
    }

    public Download cancel() {
        if (future != null) future.cancel(true);
        OkHttp.cancel(tag);
        future = null;
        callbackRef = null;
        return this;
    }

    private void doInBackground() {
        // ✅ 修复：使用临时文件确保原子性
        File tempFile = new File(file.getAbsolutePath() + ".tmp");

        try (Response res = OkHttp.newCall(url, tag).execute()) {
            if (res.isSuccessful() && res.body() != null) {
                // ✅ 修复：下载到临时文件
                download(res.body().byteStream(), getLength(res), tempFile);

                // ✅ 修复：下载完成后重命名
                if (!tempFile.renameTo(file)) {
                    throw new IOException("无法重命名临时文件: " + tempFile);
                }

                Callback cb = callbackRef != null ? callbackRef.get() : null;
                if (cb != null) App.post(() -> cb.success(file));
            } else {
                throw new IOException("请求失败: HTTP " + res.code() + " " + res.message());
            }
        } catch (Exception e) {
            // ✅ 修复：清理两个文件
            Path.clear(tempFile);
            Path.clear(file);

            Callback cb = callbackRef != null ? callbackRef.get() : null;
            if (cb != null) App.post(() -> cb.error(e.getMessage()));
            else throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void download(InputStream is, double length, File temp) throws IOException {
        try (BufferedInputStream input = new BufferedInputStream(is);
             FileOutputStream os = new FileOutputStream(Path.create(temp))) {

            byte[] buffer = new byte[16384];
            int readBytes;
            long totalBytes = 0;

            while ((readBytes = input.read(buffer)) != -1) {
                // ✅ 修复：检查中断并清理
                if (Thread.interrupted()) {
                    throw new InterruptedException("下载被取消");
                }

                totalBytes += readBytes;
                os.write(buffer, 0, readBytes);

                if (length > 0) {
                    int progress = (int) (totalBytes / length * 100.0);
                    Callback cb = callbackRef != null ? callbackRef.get() : null;
                    if (cb != null) App.post(() -> cb.progress(progress));
                }
            }

            os.flush(); // 确保数据完全写入磁盘
        } catch (InterruptedException e) {
            // ✅ 修复：清理并重新抛出
            Path.clear(temp);
            Thread.currentThread().interrupt(); // 恢复中断状态
            throw new IOException("下载被中断", e);
        }
    }

    private double getLength(Response res) {
        try {
            String header = res.header(HttpHeaders.CONTENT_LENGTH);
            return header != null ? Double.parseDouble(header) : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    public interface Callback {
        void progress(int progress);
        void error(String msg);
        void success(File file);
    }
}