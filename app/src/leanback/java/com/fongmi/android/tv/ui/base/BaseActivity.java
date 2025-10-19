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
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
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
import java.util.Arrays;
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

    /* ===== 网络壁纸逻辑（增强调试版） ===== */
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
                connection.setConnectTimeout(10000);
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
        Log.d(TAG, "=== 缓存文件调试信息 ===");
        Log.d(TAG, "缓存文件路径: " + cacheFile.getAbsolutePath());
        Log.d(TAG, "缓存文件父目录: " + cacheFile.getParent());
        Log.d(TAG, "父目录是否存在: " + cacheFile.getParentFile().exists());

        try (FileOutputStream out = new FileOutputStream(cacheFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush(); // 确保数据写入

            Log.i(TAG, "网络壁纸已保存到缓存: " + cacheFile.getAbsolutePath());
            Log.i(TAG, "缓存文件大小: " + cacheFile.length() + " bytes");
            Log.i(TAG, "缓存文件是否存在: " + cacheFile.exists());

            // 列出目录内容
            File parentDir = cacheFile.getParentFile();
            if (parentDir.exists() && parentDir.isDirectory()) {
                String[] files = parentDir.list();
                Log.d(TAG, "目录内容: " + (files != null ? Arrays.toString(files) : "空或无法访问"));
            }

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

        Log.d(TAG, "=== 开始添加壁纸视图 ===");
        Log.d(TAG, "根布局类型: " + root.getClass().getSimpleName());

        // 移除已存在的CustomWallView
        int removedCount = 0;
        for (int i = 0; i < root.getChildCount(); i++) {
            if (root.getChildAt(i) instanceof CustomWallView) {
                root.removeViewAt(i);
                removedCount++;
                Log.d(TAG, "移除已存在的CustomWallView, 索引: " + i);
                break;
            }
        }
        Log.d(TAG, "共移除 " + removedCount + " 个CustomWallView");

        wallView = new CustomWallView(this, null);

        // 设置壁纸视图属性
        wallView.setId(View.generateViewId());
        if (bmp != null) {
            wallView.setBackground(new BitmapDrawable(getResources(), bmp));
            Log.i(TAG, "设置网络壁纸作为背景");
        } else {
            Log.w(TAG, "使用CustomWallView内置默认壁纸");
        }

        // 添加到最底层
        root.addView(wallView, 0, new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));

        // 设置Z轴顺序确保在最底层
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            wallView.setZ(-1f);
        }

        Log.d(TAG, "CustomWallView已添加到根布局，索引: 0");

        // 记录当前视图层次
        logChildViews(root);
    }

    // 调试方法：打印所有子视图信息
    private void logChildViews(ViewGroup root) {
        Log.d(TAG, "=== 根布局子视图详细信息 ===");
        Log.d(TAG, "子视图总数: " + root.getChildCount());
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            Log.d(TAG, "索引 " + i + ": " + child.getClass().getSimpleName() +
                    ", ID: " + child.getId() +
                    ", 可见性: " + (child.getVisibility() == View.VISIBLE ? "VISIBLE" :
                    child.getVisibility() == View.INVISIBLE ? "INVISIBLE" : "GONE") +
                    ", 宽度: " + child.getWidth() +
                    ", 高度: " + child.getHeight());
        }
        Log.d(TAG, "=== 子视图信息结束 ===");
    }

    private void applyWallBitmap(Bitmap bmp) {
        if (bmp != null) {
            Log.i(TAG, "应用网络壁纸");

            runOnUiThread(() -> {
                Toast.makeText(this, "网络壁纸加载成功!", Toast.LENGTH_LONG).show();
            });

            addWallView(bmp);

            // 延迟检查壁纸显示状态和截屏
            new Handler().postDelayed(() -> {
                checkWallpaperVisibility();
                takeScreenshotForVerification();  // 新增截屏验证
            }, 2000);

        } else {
            Log.w(TAG, "网络壁纸加载失败，保持默认壁纸");
            runOnUiThread(() -> {
                Toast.makeText(this, "使用默认壁纸", Toast.LENGTH_SHORT).show();
            });
        }
    }

    // 截屏验证方法
    private void takeScreenshotForVerification() {
        Log.d(TAG, "=== 开始截屏验证 ===");

        try {
            // 获取根视图
            View rootView = getWindow().getDecorView().getRootView();
            rootView.setDrawingCacheEnabled(true);

            // 创建截屏
            Bitmap screenshot = Bitmap.createBitmap(rootView.getDrawingCache());
            rootView.setDrawingCacheEnabled(false);

            if (screenshot != null) {
                // 保存截屏到文件
                File screenshotDir = getExternalFilesDir(null);
                File screenshotFile = new File(screenshotDir, "wallpaper_verification_" + System.currentTimeMillis() + ".jpg");

                try (FileOutputStream out = new FileOutputStream(screenshotFile)) {
                    screenshot.compress(Bitmap.CompressFormat.JPEG, 80, out);
                    out.flush();

                    Log.i(TAG, "✅ 截屏验证成功！");
                    Log.i(TAG, "截屏文件路径: " + screenshotFile.getAbsolutePath());
                    Log.i(TAG, "截屏文件大小: " + screenshotFile.length() + " bytes");
                    Log.i(TAG, "截屏尺寸: " + screenshot.getWidth() + "x" + screenshot.getHeight());

                    // 显示成功Toast
                    runOnUiThread(() -> {
                        Toast.makeText(this, "📸 截屏已保存！路径: " + screenshotFile.getName(), Toast.LENGTH_LONG).show();
                    });

                } catch (IOException e) {
                    Log.e(TAG, "保存截屏文件失败", e);
                }
            } else {
                Log.e(TAG, "截屏失败 - Bitmap为null");
            }

        } catch (Exception e) {
            Log.e(TAG, "截屏过程出错", e);
        }
    }

    // 检查壁纸可见性
    private void checkWallpaperVisibility() {
        ViewGroup root = findViewById(android.R.id.content);
        CustomWallView foundWallView = null;

        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof CustomWallView) {
                foundWallView = (CustomWallView) child;
                break;
            }
        }

        if (foundWallView != null) {
            Log.d(TAG, "=== 壁纸视图状态检查 ===");
            Log.d(TAG, "CustomWallView 找到: 是");
            Log.d(TAG, "可见性: " + (foundWallView.getVisibility() == View.VISIBLE ? "VISIBLE" : "不可见"));
            Log.d(TAG, "宽度: " + foundWallView.getWidth());
            Log.d(TAG, "高度: " + foundWallView.getHeight());
            Log.d(TAG, "Alpha: " + foundWallView.getAlpha());
            Log.d(TAG, "背景: " + (foundWallView.getBackground() != null ? "已设置" : "未设置"));
        } else {
            Log.e(TAG, "=== 壁纸视图状态检查 ===");
            Log.e(TAG, "CustomWallView 找到: 否 - 壁纸视图未找到！");
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