package com.fongmi.android.tv.ui.base;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import android.app.Activity;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.ui.custom.CustomWallView;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Util;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.jessyan.autosize.AutoSizeCompat;

public abstract class BaseActivity extends AppCompatActivity {

    private static final String TAG = "WallpaperLogger";
    private static final String WALL_URL = "https://xhys.lcjly.cn/image/bg.jpg";

    private OnBackInvokedCallback callback;
    private CustomWallView wallView;

    protected abstract ViewBinding getBinding();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getBinding().getRoot());
        EventBus.getDefault().register(this);
        Util.hideSystemUI(this);
        setBackCallback();
        // 先添加默认背景视图
        addWallView(null);
        initView();
        initEvent();
        loadNetworkWallpaper();   // 唯一入口
    }

    /* ===== 网络壁纸逻辑（增强版） ===== */
    private void loadNetworkWallpaper() {
        Log.d(TAG, "=== 开始加载网络壁纸 ===");
        Log.d(TAG, "customWall() = " + customWall());

        if (!customWall()) {
            Log.w(TAG, "customWall() returned false → skip network wallpaper");
            return;
        }

        Log.d(TAG, "loadNetworkWallpaper() start, url=" + WALL_URL);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        pool.execute(() -> {
            boolean netOk = isNetOk();
            Log.d(TAG, "network available=" + netOk);

            if (!netOk) {
                Log.w(TAG, "无网络连接，使用默认壁纸");
                runOnUiThread(() -> applyWallBitmap(null));
                return;
            }

            Bitmap bmp = null;
            try {
                Log.d(TAG, "开始HTTP请求...");
                HttpURLConnection connection = (HttpURLConnection) new URL(WALL_URL).openConnection();
                connection.setConnectTimeout(10000); // 延长超时时间
                connection.setReadTimeout(10000);
                connection.setRequestMethod("GET");
                connection.setDoInput(true);

                int code = connection.getResponseCode();
                Log.d(TAG, "HTTP响应码: " + code);

                if (code == HttpURLConnection.HTTP_OK) {
                    Log.d(TAG, "开始解码图片流...");
                    bmp = BitmapFactory.decodeStream(connection.getInputStream());
                    if (bmp != null) {
                        Log.i(TAG, "网络图片解码成功, 尺寸: " + bmp.getWidth() + "x" + bmp.getHeight());
                        // 保存到缓存
                        saveBitmapToCache(bmp);
                    } else {
                        Log.e(TAG, "图片解码失败 - BitmapFactory.decodeStream返回null");
                    }
                } else {
                    Log.w(TAG, "HTTP错误: " + code + ", 使用默认壁纸");
                }
                connection.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "网络加载错误: " + e.getMessage(), e);
            }

            final Bitmap result = bmp;
            runOnUiThread(() -> {
                Log.d(TAG, "在UI线程应用壁纸, bitmap=" + (result != null ? "有效" : "null"));
                applyWallBitmap(result);
            });
        });
        pool.shutdown();
    }

    private void saveBitmapToCache(Bitmap bitmap) {
        if (bitmap == null) {
            Log.e(TAG, "保存缓存失败: bitmap为null");
            return;
        }

        File cacheFile = FileUtil.getWallCache();
        try (FileOutputStream out = new FileOutputStream(cacheFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            Log.i(TAG, "网络壁纸已保存到缓存: " + cacheFile.getAbsolutePath());
            Log.i(TAG, "缓存文件大小: " + cacheFile.length() + " bytes");

            // 修复：使用正确的静态方法发送事件
            RefreshEvent.wall();
            Log.d(TAG, "发送壁纸刷新事件");

        } catch (IOException e) {
            Log.e(TAG, "保存壁纸缓存失败", e);
        }
    }

    private boolean isNetOk() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Exception e) {
            Log.e(TAG, "检查网络状态失败", e);
            return false;
        }
    }

    private void addWallView(Bitmap bmp) {
        ViewGroup root = findViewById(android.R.id.content);

        // 移除已存在的CustomWallView
        for (int i = 0; i < root.getChildCount(); i++) {
            if (root.getChildAt(i) instanceof CustomWallView) {
                root.removeViewAt(i);
                break;
            }
        }

        wallView = new CustomWallView(this, null);
        if (bmp != null) {
            wallView.setBackground(new BitmapDrawable(getResources(), bmp));
            Log.i(TAG, "设置网络壁纸作为背景");
        } else {
            Log.w(TAG, "使用CustomWallView内置默认壁纸");
        }

        root.addView(wallView, 0, new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        Log.d(TAG, "CustomWallView已添加到根布局");
    }

    private void applyWallBitmap(Bitmap bmp) {
        if (bmp != null) {
            Log.i(TAG, "应用网络壁纸");
            addWallView(bmp);
        } else {
            Log.w(TAG, "网络壁纸加载失败，保持默认壁纸");
            // 这里不需要做任何操作，因为默认壁纸已经在onCreate中设置
        }
    }
    /* ===== 网络壁纸逻辑结束 ===== */

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
    }

    protected Activity getActivity() {
        return this;
    }

    protected boolean customWall() {
        return true;
    }

    protected void initView() {
    }

    protected void initEvent() {
    }

    protected boolean isVisible(View view) {
        return view.getVisibility() == View.VISIBLE;
    }

    protected boolean isGone(View view) {
        return view.getVisibility() == View.GONE;
    }

    protected void notifyItemChanged(RecyclerView view, ArrayObjectAdapter adapter) {
        view.post(() -> adapter.notifyArrayItemRangeChanged(0, adapter.size()));
    }

    private void setBackCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback = this::onBackInvoked);
        } else {
            getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    onBackInvoked();
                }
            });
        }
    }

    private Resources hackResources(Resources resources) {
        try {
            AutoSizeCompat.autoConvertDensityOfGlobal(resources);
            return resources;
        } catch (Exception ignored) {
            return resources;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSubscribe(Object o) {
    }

    @Override
    public Resources getResources() {
        return hackResources(super.getResources());
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Util.hideSystemUI(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Util.hideSystemUI(this);
    }

    protected void onBackInvoked() {
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(callback);
    }
}