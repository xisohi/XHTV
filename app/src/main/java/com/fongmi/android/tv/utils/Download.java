package com.fongmi.android.tv.utils;

import android.util.Log;

import com.fongmi.android.tv.App;
import com.github.catvod.utils.Path;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.concurrent.Future;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Download {

    private static final String TAG = "Download";

    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    private final File file;
    private String url;
    private Callback callback;
    private Future<?> future;
    private long maxBytes;
    private String tag;
    private long downloadedBytes = 0;

    public static Download create(String url, File file) {
        return new Download(url, file);
    }

    public Download(String url, File file) {
        this.url = url;
        this.file = file;
        this.maxBytes = Long.MAX_VALUE;
        this.tag = url;
        if (file.exists()) {
            this.downloadedBytes = file.length();
            Log.d(TAG, "发现已下载文件，大小: " + downloadedBytes + " bytes");
        } else {
            this.downloadedBytes = 0;
        }
        Log.d(TAG, "创建下载任务，URL: " + url);
        Log.d(TAG, "保存路径: " + file.getAbsolutePath());
        Log.d(TAG, "已下载: " + downloadedBytes + " bytes");
    }

    public Download tag(String tag) {
        this.tag = tag;
        return this;
    }

    public Download maxBytes(long maxBytes) {
        this.maxBytes = maxBytes > 0 ? maxBytes : Long.MAX_VALUE;
        return this;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String newUrl) {
        this.url = newUrl;
        Log.d(TAG, "更新下载 URL: " + newUrl);
    }

    public File get() {
        doInBackground();
        return file;
    }

    public void start(Callback callback) {
        this.callback = callback;
        future = Task.submit(this::doInBackground);
    }

    public void retry() {
        if (file.exists()) {
            this.downloadedBytes = file.length();
        } else {
            this.downloadedBytes = 0;
        }
        future = Task.submit(this::doInBackground);
    }

    public Download cancel() {
        if (future != null) {
            future.cancel(true);
        }
        future = null;
        callback = null;
        return this;
    }

    private void doInBackground() {
        Log.d(TAG, "========== 开始下载执行 ==========");
        Log.d(TAG, "下载 URL: " + url);
        Log.d(TAG, "已下载: " + downloadedBytes + " bytes");

        RandomAccessFile raf = null;
        try {
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            raf = new RandomAccessFile(file, "rw");
            raf.seek(downloadedBytes);

            Request.Builder requestBuilder = new Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

            if (downloadedBytes > 0) {
                requestBuilder.addHeader("Range", "bytes=" + downloadedBytes + "-");
                Log.d(TAG, "断点续传: bytes=" + downloadedBytes + "-");
            }

            Request request = requestBuilder.build();

            try (Response res = HTTP_CLIENT.newCall(request).execute()) {
                Log.d(TAG, "服务器响应状态码: " + res.code());

                if (res.code() == 206) {
                    Log.d(TAG, "✅ 断点续传成功");
                } else if (res.code() == 200) {
                    Log.d(TAG, "📥 全新下载");
                    if (downloadedBytes > 0) {
                        raf.close();
                        file.delete();
                        raf = new RandomAccessFile(file, "rw");
                        downloadedBytes = 0;
                        Log.w(TAG, "文件已存在但服务器不支持断点续传，重新下载");
                    }
                } else if (res.code() == 416) {
                    Log.d(TAG, "文件已下载完整 (416 Range Not Satisfiable)");
                    if (callback != null) {
                        App.post(() -> callback.success(file));
                    }
                    return;
                }

                if (res.isSuccessful() && res.body() != null) {
                    long totalSize = getTotalSize(res);
                    Log.d(TAG, "文件总大小: " + totalSize + " bytes");
                    download(res.body().byteStream(), totalSize, raf);
                    Log.d(TAG, "========== 下载完成 ==========");
                    if (callback != null) {
                        App.post(() -> callback.success(file));
                    }
                } else {
                    throw new IOException("请求失败: HTTP " + res.code() + " " + res.message());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "========== 下载异常 ==========");
            Log.e(TAG, "异常信息: " + e.getMessage());
            if (file.exists() && file.length() == 0) {
                Path.clear(file);
            }
            if (callback != null) {
                App.post(() -> callback.error(e.getMessage()));
            }
        } finally {
            if (raf != null) {
                try {
                    raf.close();
                } catch (IOException e) {
                    Log.w(TAG, "关闭文件异常: " + e.getMessage());
                }
            }
        }
        Log.d(TAG, "========== 下载线程结束 ==========");
    }

    private long getTotalSize(Response res) {
        String rangeHeader = res.header("Content-Range");
        if (rangeHeader != null && rangeHeader.contains("/")) {
            try {
                String total = rangeHeader.substring(rangeHeader.indexOf('/') + 1);
                return Long.parseLong(total);
            } catch (Exception e) {
                Log.w(TAG, "解析 Content-Range 失败: " + e.getMessage());
            }
        }

        try {
            String length = res.header("Content-Length");
            if (length != null && !length.isEmpty()) {
                long total = Long.parseLong(length);
                if (res.code() == 206) {
                    total += downloadedBytes;
                }
                return total;
            }
        } catch (Exception e) {
            Log.w(TAG, "解析 Content-Length 失败: " + e.getMessage());
        }
        return -1;
    }

    private void download(InputStream is, long totalSize, RandomAccessFile raf) throws IOException {
        Log.d(TAG, "开始写入文件...");

        try (BufferedInputStream input = new BufferedInputStream(is)) {
            byte[] buffer = new byte[32768];
            int readBytes;
            long totalBytes = downloadedBytes;
            int lastProgress = -1;

            while ((readBytes = input.read(buffer)) != -1) {
                if (Thread.interrupted()) {
                    Log.w(TAG, "下载被中断，已保存进度: " + totalBytes);
                    return;
                }

                raf.write(buffer, 0, readBytes);
                totalBytes += readBytes;
                downloadedBytes = totalBytes;

                if (totalSize <= 0 || callback == null) continue;

                int progress = (int) (totalBytes * 100 / totalSize);
                progress = Math.max(0, Math.min(100, progress));

                if (progress != lastProgress && progress % 2 == 0) {
                    Log.d(TAG, "下载进度: " + progress + "% (" + totalBytes + "/" + totalSize + " bytes)");
                    lastProgress = progress;
                }

                final int currentProgress = progress;
                App.post(() -> callback.progress(currentProgress));
            }
            Log.d(TAG, "文件写入完成，总大小: " + totalBytes + " bytes");
        } catch (Exception e) {
            Log.e(TAG, "写入文件异常: " + e.getMessage());
            throw e;
        }
    }

    public interface Callback {
        void progress(int progress);
        void error(String msg);
        void success(File file);
    }
}