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
import android.util.Log;               // ← 新增
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

import com.fongmi.android.tv.ui.custom.CustomWallView;
import com.fongmi.android.tv.utils.Util;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.jessyan.autosize.AutoSizeCompat;

public abstract class BaseActivity extends AppCompatActivity {

    private static final String TAG = "WallpaperLogger";   // ← 新增
    private static final String WALL_URL = "https://xhys.lcjly.cn/image/bg.jpg"; // ← 新增

    private OnBackInvokedCallback callback;

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
        loadNetworkWallpaper();   // ← 新增
    }

    /* ===== 网络壁纸逻辑（带日志） ===== */
    private void loadNetworkWallpaper() {
        if (!customWall()) {
            Log.w(TAG, "customWall() returned false → skip network wallpaper");
            return;
        }
        Log.d(TAG, "loadNetworkWallpaper() start, url=" + WALL_URL);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        pool.execute(() -> {
            boolean netOk = isNetOk();
            Log.d(TAG, "network available=" + netOk);
            Bitmap bmp = null;
            if (netOk) {
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(WALL_URL).openConnection();
                    c.setConnectTimeout(3000);
                    c.setReadTimeout(3000);
                    int code = c.getResponseCode();
                    Log.d(TAG, "http responseCode=" + code);
                    if (code == 200) {
                        bmp = BitmapFactory.decodeStream(c.getInputStream());
                        Log.i(TAG, "network picture decoded, bitmap=" + bmp);
                    } else {
                        Log.w(TAG, "http not 200, fallback to default");
                    }
                    c.disconnect();
                } catch (Exception e) {
                    Log.e(TAG, "network load error", e);
                }
            } else {
                Log.w(TAG, "no network → will use default wallpaper");
            }
            final Bitmap result = bmp;
            runOnUiThread(() -> {
                Log.d(TAG, "applyWallBitmap on UI thread, bitmap=" + result);
                applyWallBitmap(result);
            });
        });
    }

    private boolean isNetOk() {
        NetworkInfo ni = ((ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    private void applyWallBitmap(Bitmap bmp) {
        ViewGroup root = findViewById(android.R.id.content);
        CustomWallView wallView = new CustomWallView(this, null);
        if (bmp != null) {
            wallView.setBackground(new BitmapDrawable(getResources(), bmp));
            Log.i(TAG, "network wallpaper set as background");
        } else {
            Log.w(TAG, "bitmap==null → CustomWallView will use built-in default");
        }
        root.addView(wallView, 0, new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        Log.d(TAG, "CustomWallView added to root");
    }
    /* ===== 网络壁纸逻辑结束 ===== */

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        // 不再这里 addView，避免重复
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