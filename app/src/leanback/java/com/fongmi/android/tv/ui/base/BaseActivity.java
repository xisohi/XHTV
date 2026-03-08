package com.fongmi.android.tv.ui.base;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentActivity;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.event.ConfigEvent;  // 导入 ConfigEvent
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.ui.custom.CustomWallView;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Util;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

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
        initView();
        initEvent();
        loadNetworkWallpaper();
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        if (!customWall()) return;
        addWallView(null);
    }

    private void loadNetworkWallpaper() {
        if (!customWall()) return;

        int originalWall = Setting.getWall();
        Setting.putWall(0);

        ExecutorService pool = Executors.newSingleThreadExecutor();
        pool.execute(() -> {
            if (!isNetOk()) {
                Setting.putWall(originalWall);
                return;
            }

            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(WALL_URL).openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setRequestMethod("GET");

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    Bitmap bmp = BitmapFactory.decodeStream(connection.getInputStream());
                    if (bmp != null) saveBitmapToCache(bmp);
                }
                connection.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "loadNetworkWallpaper", e);
            }
        });
        pool.shutdown();
    }

    private void saveBitmapToCache(Bitmap bitmap) {
        try (FileOutputStream out = new FileOutputStream(FileUtil.getWallCache())) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            // 使用 ConfigEvent 而不是 RefreshEvent
            ConfigEvent.wall();  // 触发壁纸配置更新事件
        } catch (IOException e) {
            Log.e(TAG, "saveBitmapToCache", e);
        }
    }

    private boolean isNetOk() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    private void addWallView(Bitmap bmp) {
        ViewGroup root = findViewById(android.R.id.content);

        for (int i = 0; i < root.getChildCount(); i++) {
            if (root.getChildAt(i) instanceof CustomWallView) {
                root.removeViewAt(i);
                break;
            }
        }

        if (bmp != null) {
            saveBitmapToCache(bmp);
            new Handler().postDelayed(() -> {
                wallView = new CustomWallView(this, null);
                root.addView(wallView, 0, new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
                new Handler().postDelayed(() -> {
                    // 使用 ConfigEvent 而不是 RefreshEvent
                    ConfigEvent.wall();
                }, 300);
            }, 300);
        } else {
            wallView = new CustomWallView(this, null);
            root.addView(wallView, 0, new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        }
    }

    protected FragmentActivity getActivity() {
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
        // 处理 ConfigEvent 事件
        if (o instanceof ConfigEvent) {
            ConfigEvent event = (ConfigEvent) o;
            // 使用 type() 方法（record 自动生成的方法）
            if (event.type() == ConfigEvent.Type.WALL) {
                // 壁纸配置更新，刷新壁纸显示
                if (wallView != null) {
                    wallView.invalidate();  // 或者调用 wallView 的刷新方法
                }
            }
        }
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