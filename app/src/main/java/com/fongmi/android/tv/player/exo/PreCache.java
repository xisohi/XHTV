package com.fongmi.android.tv.player.exo;

import android.os.HandlerThread;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.datasource.DataSource;
import androidx.media3.exoplayer.source.preload.PreCacheHelper;

import com.fongmi.android.tv.setting.PreloadSetting;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PreCache implements Player.Listener {

    private ExecutorService executor;
    private PreCacheHelper helper;
    private HandlerThread worker;
    private Player player;
    private int threads;
    private long pendingStartMs;
    private boolean pending;

    public void start(Player player, MediaItem mediaItem) {
        stop();
        if (!PreloadSetting.isPreload() || !canPreCache(mediaItem)) return;
        this.player = player;
        this.helper = createHelper(mediaItem);
        this.player.addListener(this);
        setPending();
        preCacheIfReady();
    }

    public void stop() {
        if (player != null) player.removeListener(this);
        if (helper != null) helper.release(false);
        helper = null;
        player = null;
        clearPending();
    }

    public void release() {
        stop();
        releaseExecutor();
        releaseWorker();
    }

    @Override
    public void onPlaybackStateChanged(int state) {
        if (state == Player.STATE_READY && pending) preCacheIfReady();
    }

    @Override
    public void onPositionDiscontinuity(@NonNull Player.PositionInfo oldPosition, @NonNull Player.PositionInfo newPosition, int reason) {
        if (!isSeek(reason) || helper == null) return;
        helper.stop();
        setPending(newPosition.positionMs);
        preCacheIfReady();
    }

    private void preCacheIfReady() {
        if (helper == null || player == null) return;
        if (!PreloadSetting.isPreload()) {
            stop();
            return;
        }
        if (player.getPlaybackState() != Player.STATE_READY) return;
        if (player.isCurrentMediaItemLive()) {
            stop();
            return;
        }
        long startMs = getStartPosition();
        long lengthMs = getPreCacheLengthMs(startMs);
        if (lengthMs <= 0) {
            clearPending();
            return;
        }
        helper.preCache(startMs, lengthMs);
        clearPending();
    }

    private PreCacheHelper createHelper(MediaItem mediaItem) {
        DataSource.Factory upstreamFactory = MediaSourceFactory.createUpstreamDataSourceFactory(ExoUtil.extractHeaders(mediaItem));
        return new PreCacheHelper.Factory(MediaSourceFactory.getCache(), upstreamFactory, ExoUtil.buildRenderersFactory(), getWorker().getLooper()).setDownloadExecutor(getExecutor()).create(mediaItem);
    }

    private boolean canPreCache(MediaItem mediaItem) {
        if (mediaItem == null || mediaItem.localConfiguration == null) return false;
        MediaItem.LocalConfiguration local = mediaItem.localConfiguration;
        String scheme = local.uri.getScheme();
        String url = local.uri.toString();
        return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) && !MediaSourceFactory.isConcatenatingUrl(url);
    }

    private long getStartPosition() {
        return Math.max(0, pendingStartMs != C.TIME_UNSET ? pendingStartMs : player.getCurrentPosition());
    }

    private long getPreCacheLengthMs(long startMs) {
        long durationMs = player.getDuration();
        if (durationMs <= 0) return 0;
        return Math.min(PreloadSetting.getPreloadDurationMs(), durationMs - startMs);
    }

    private void setPending() {
        setPending(C.TIME_UNSET);
    }

    private void setPending(long startMs) {
        pendingStartMs = startMs;
        pending = true;
    }

    private void clearPending() {
        pendingStartMs = C.TIME_UNSET;
        pending = false;
    }

    private Executor getExecutor() {
        int count = PreloadSetting.getPreloadThreads();
        if (executor != null && threads == count) return executor;
        releaseExecutor();
        threads = count;
        return executor = Executors.newFixedThreadPool(count);
    }

    private void releaseExecutor() {
        if (executor == null) return;
        executor.shutdownNow();
        executor = null;
    }

    private HandlerThread getWorker() {
        if (worker != null) return worker;
        worker = new HandlerThread("CurrentMediaPreCache");
        worker.start();
        return worker;
    }

    private void releaseWorker() {
        if (worker == null) return;
        worker.quitSafely();
        worker = null;
    }

    private boolean isSeek(int reason) {
        return reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT;
    }
}
