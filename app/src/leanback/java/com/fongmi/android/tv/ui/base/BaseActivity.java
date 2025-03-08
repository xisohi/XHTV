package com.fongmi.android.tv.ui.base;

import android.app.Activity;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import me.jessyan.autosize.AutoSizeCompat;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public abstract class BaseActivity extends AppCompatActivity {

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
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        refreshWall();
    }

    protected Activity getActivity() {
        return this;
    }

    protected boolean customWall() {
        return true;
    }

    protected boolean handleBack() {
        return false;
    }

    protected void initView() {
    }

    protected void initEvent() {
    }

    protected void onBackPress() {
    }

    protected boolean isVisible(View view) {
        return view.getVisibility() == View.VISIBLE;
    }

    protected boolean isGone(View view) {
        return view.getVisibility() == View.GONE;
    }

    protected void notifyItemChanged(RecyclerView view, ArrayObjectAdapter adapter) {
        if (!view.isComputingLayout()) adapter.notifyArrayItemRangeChanged(0, adapter.size());
    }

    protected void notifyItemChanged(RecyclerView view, RecyclerView.Adapter<?> adapter) {
        if (!view.isComputingLayout()) adapter.notifyItemRangeChanged(0, adapter.getItemCount());
    }

    private void setBackCallback() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(handleBack()) {
            @Override
            public void handleOnBackPressed() {
                onBackPress();
            }
        });
    }

    private void refreshWall() {
        if (!customWall()) return;

        // 网络图片的 URL
        final String imageUrl = "https://example.com/your-background-image.jpg";

        // 在后台线程中加载网络图片
        new Thread(new Runnable() {
            @Override
            public void run() {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(imageUrl).build();
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        InputStream inputStream = response.body().byteStream();
                        final Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                        if (bitmap != null) {
                            // 网络图片加载成功，切换到主线程设置背景
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    BitmapDrawable drawable = new BitmapDrawable(getResources(), bitmap);
                                    getWindow().setBackgroundDrawable(drawable);
                                }
                            });
                        } else {
                            // 网络图片加载失败，加载本地文件或默认背景
                            loadFallbackWallpaper();
                        }
                    } else {
                        // 网络图片加载失败，加载本地文件或默认背景
                        loadFallbackWallpaper();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    // 网络图片加载失败，加载本地文件或默认背景
                    loadFallbackWallpaper();
                }
            }
        }).start();
    }

    private void loadFallbackWallpaper() {
        try {
            File file = FileUtil.getWall(Setting.getWall());
            if (file.exists() && file.length() > 0) {
                final Drawable drawable = Drawable.createFromPath(file.getAbsolutePath());
                if (drawable != null) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            getWindow().setBackgroundDrawable(drawable);
                        }
                    });
                } else {
                    // 本地文件加载失败，使用默认背景
                    useDefaultWallpaper();
                }
            } else {
                // 本地文件不存在，使用默认背景
                useDefaultWallpaper();
            }
        } catch (Exception e) {
            e.printStackTrace();
            // 出现异常，使用默认背景
            useDefaultWallpaper();
        }
    }

    private void useDefaultWallpaper() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                getWindow().setBackgroundDrawableResource(R.drawable.wallpaper_1);
            }
        });
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
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType() == RefreshEvent.Type.WALL) refreshWall();
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }
}