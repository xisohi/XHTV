package com.fongmi.android.tv.utils;

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
    private WeakReference<Callback> callbackRef; // 弱引用防止内存泄漏
    private Future<?> future;

    public static Download create(String url, File file) {
        return new Download(url, file);
    }

    public Download(String url, File file) {
        this.url = url;
        this.tag = url;
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
        if (Thread.interrupted()) return;

        Callback callback = callbackRef != null ? callbackRef.get() : null;
        if (callback == null) return; // Activity已销毁，直接返回

        try (Response res = OkHttp.newCall(url, tag).execute()) {
            if (res.isSuccessful() && res.body() != null) {
                download(res.body().byteStream(), getLength(res));
                Callback cb = callbackRef.get();
                if (cb != null) App.post(() -> cb.success(file));
            } else {
                throw new IOException("请求失败: HTTP " + res.code() + " " + res.message());
            }
        } catch (Exception e) {
            Path.clear(file);
            Callback cb = callbackRef.get();
            if (cb != null) App.post(() -> cb.error(e.getMessage()));
            else throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void download(InputStream is, double length) throws IOException {
        try (BufferedInputStream input = new BufferedInputStream(is);
             FileOutputStream os = new FileOutputStream(Path.create(file))) {

            byte[] buffer = new byte[16384];
            int readBytes;
            long totalBytes = 0;

            while ((readBytes = input.read(buffer)) != -1) {
                // 检查是否被取消
                if (Thread.interrupted()) {
                    Log.w("Download", "下载任务被取消: " + url);
                    return;
                }

                totalBytes += readBytes;
                os.write(buffer, 0, readBytes);

                if (length > 0) {
                    int progress = (int) (totalBytes / length * 100.0);
                    Callback cb = callbackRef.get();
                    if (cb != null) App.post(() -> cb.progress(progress));
                }
            }

            os.flush(); // 确保数据完全写入磁盘
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