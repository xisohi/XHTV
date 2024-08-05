package com.fongmi.android.tv.player;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.util.EventLogger;
import androidx.media3.ui.PlayerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Drm;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.event.ActionEvent;
import com.fongmi.android.tv.event.ErrorEvent;
import com.fongmi.android.tv.event.PlayerEvent;
import com.fongmi.android.tv.impl.ParseCallback;
import com.fongmi.android.tv.impl.SessionCallback;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.utils.Path;
import com.google.common.net.HttpHeaders;
import com.orhanobut.logger.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Players implements Player.Listener, ParseCallback {

    private static final String TAG = Players.class.getSimpleName();

    public static final int SOFT = 0;
    public static final int HARD = 1;

    private final StringBuilder builder;
    private final Formatter formatter;
    private final Runnable runnable;

    private Map<String, String> headers;
    private MediaSessionCompat session;
    private ExoPlayer exoPlayer;
    private ParseJob parseJob;
    private List<Sub> subs;
    private String format;
    private String url;
    private Drm drm;
    private Sub sub;

    private long position;
    private int decode;
    private int retry;

    public static Players create(Activity activity) {
        Players player = new Players(activity);
        Server.get().setPlayer(player);
        return player;
    }

    public static boolean isHard(int decode) {
        return decode == HARD;
    }

    private Players(Activity activity) {
        decode = Setting.getDecode();
        builder = new StringBuilder();
        runnable = ErrorEvent::timeout;
        formatter = new Formatter(builder, Locale.getDefault());
        position = C.TIME_UNSET;
        createSession(activity);
    }

    private void createSession(Activity activity) {
        session = new MediaSessionCompat(activity, "TV");
        session.setCallback(SessionCallback.create(this));
        session.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setSessionActivity(PendingIntent.getActivity(App.get(), 0, new Intent(App.get(), activity.getClass()), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        MediaControllerCompat.setMediaController(activity, session.getController());
    }

    public void init(PlayerView exo) {
        releasePlayer();
        initExo(exo);
    }

    private void initExo(PlayerView exo) {
        exoPlayer = new ExoPlayer.Builder(App.get()).setLoadControl(ExoUtil.buildLoadControl()).setTrackSelector(ExoUtil.buildTrackSelector()).setRenderersFactory(ExoUtil.buildRenderersFactory(decode)).setMediaSourceFactory(ExoUtil.buildMediaSourceFactory()).build();
        exoPlayer.setAudioAttributes(AudioAttributes.DEFAULT, true);
        exoPlayer.addAnalyticsListener(new EventLogger());
        exoPlayer.setHandleAudioBecomingNoisy(true);
        //exo.setRender(Setting.getRender());
        exoPlayer.setPlayWhenReady(true);
        exoPlayer.addListener(this);
        exo.setPlayer(exoPlayer);
    }

    public ExoPlayer get() {
        return exoPlayer;
    }

    public MediaSessionCompat getSession() {
        return session;
    }

    public String getUrl() {
        return url;
    }

    public Map<String, String> getHeaders() {
        return headers == null ? new HashMap<>() : headers;
    }

    public void setSub(Sub sub) {
        this.sub = sub;
        setMediaItem();
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public void setPosition(long position) {
        this.position = position;
    }

    public void reset() {
        position = C.TIME_UNSET;
        removeTimeoutCheck();
        stopParse();
        retry = 0;
    }

    public void clear() {
        headers = null;
        format = null;
        subs = null;
        drm = null;
        url = null;
    }

    public String stringToTime(long time) {
        return Util.format(builder, formatter, time);
    }

    public int getVideoWidth() {
        return exoPlayer == null ? 0 : exoPlayer.getVideoSize().width;
    }

    public int getVideoHeight() {
        return exoPlayer == null ? 0 : exoPlayer.getVideoSize().height;
    }

    public float getSpeed() {
        return exoPlayer == null ? 1.0f : exoPlayer.getPlaybackParameters().speed;
    }

    public long getPosition() {
        return exoPlayer == null ? 0 : exoPlayer.getCurrentPosition();
    }

    public long getDuration() {
        return exoPlayer == null ? -1 : exoPlayer.getDuration();
    }

    public long getBuffered() {
        return exoPlayer == null ? 0 : exoPlayer.getBufferedPosition();
    }

    public boolean retried() {
        return ++retry > 2;
    }

    public boolean canAdjustSpeed() {
        return !Setting.isTunnel();
    }

    public boolean haveTrack(int type) {
        return exoPlayer != null && ExoUtil.haveTrack(exoPlayer.getCurrentTracks(), type);
    }

    public boolean isPlaying() {
        return exoPlayer != null && exoPlayer.isPlaying();
    }

    public boolean isEnd() {
        return exoPlayer != null && exoPlayer.getPlaybackState() == Player.STATE_ENDED;
    }

    public boolean isEmpty() {
        return TextUtils.isEmpty(getUrl());
    }

    public boolean isLive() {
        return getDuration() < 5 * 60 * 1000 || exoPlayer.isCurrentMediaItemLive();
    }

    public boolean isVod() {
        return getDuration() > 5 * 60 * 1000 && !exoPlayer.isCurrentMediaItemLive();
    }

    public boolean isHard() {
        return decode == HARD;
    }

    public boolean isPortrait() {
        return getVideoHeight() > getVideoWidth();
    }

    public String getSizeText() {
        return getVideoWidth() == 0 && getVideoHeight() == 0 ? "" : getVideoWidth() + " x " + getVideoHeight();
    }

    public String getSpeedText() {
        return String.format(Locale.getDefault(), "%.2f", getSpeed());
    }

    public String getDecodeText() {
        return ResUtil.getStringArray(R.array.select_decode)[decode];
    }

    public String setSpeed(float speed) {
        if (exoPlayer != null && !Setting.isTunnel()) exoPlayer.setPlaybackSpeed(speed);
        return getSpeedText();
    }

    public String addSpeed() {
        float speed = getSpeed();
        float addon = speed >= 2 ? 1f : 0.25f;
        speed = speed >= 5 ? 0.25f : Math.min(speed + addon, 5.0f);
        return setSpeed(speed);
    }

    public String addSpeed(float value) {
        float speed = getSpeed();
        speed = Math.min(speed + value, 5);
        return setSpeed(speed);
    }

    public String subSpeed(float value) {
        float speed = getSpeed();
        speed = Math.max(speed - value, 0.25f);
        return setSpeed(speed);
    }

    public String toggleSpeed() {
        float speed = getSpeed();
        speed = speed == 1 ? 3f : 1f;
        return setSpeed(speed);
    }

    public void toggleDecode(PlayerView exo) {
        Setting.putDecode(decode = isHard() ? SOFT : HARD);
        init(exo);
    }

    public String getPositionTime(long time) {
        time = getPosition() + time;
        if (time > getDuration()) time = getDuration();
        else if (time < 0) time = 0;
        return stringToTime(time);
    }

    public String getDurationTime() {
        long time = getDuration();
        if (time < 0) time = 0;
        return stringToTime(time);
    }

    public void seekTo(int time) {
        seekTo(getPosition() + time);
    }

    public void seekTo(long time) {
        if (exoPlayer != null) exoPlayer.seekTo(time);
    }

    public void play() {
        if (exoPlayer != null) exoPlayer.play();
        setPlaybackState(PlaybackStateCompat.STATE_PLAYING);
    }

    public void pause() {
        if (exoPlayer != null) exoPlayer.pause();
        setPlaybackState(PlaybackStateCompat.STATE_PAUSED);
    }

    public void stop() {
        if (exoPlayer != null) exoPlayer.stop();
        if (exoPlayer != null) exoPlayer.clearMediaItems();
        setPlaybackState(PlaybackStateCompat.STATE_STOPPED);
    }

    public void release() {
        stopParse();
        releasePlayer();
        session.release();
        removeTimeoutCheck();
        Server.get().setPlayer(null);
        App.execute(() -> Source.get().stop());
    }

    private void releasePlayer() {
        if (exoPlayer == null) return;
        exoPlayer.removeListener(this);
        exoPlayer.release();
        exoPlayer = null;
    }

    public void start(Channel channel, int timeout) {
        if (channel.hasMsg()) {
            ErrorEvent.extract(channel.getMsg());
        } else if (channel.getParse() == 1) {
            startParse(channel.result(), false);
        } else if (isIllegal(channel.getUrl())) {
            ErrorEvent.url();
        } else {
            setMediaItem(channel, timeout);
        }
    }

    public void start(Result result, boolean useParse, int timeout) {
        if (result.hasMsg()) {
            ErrorEvent.extract(result.getMsg());
        } else if (result.getParse(1) == 1 || result.getJx() == 1) {
            startParse(result, useParse);
        } else if (isIllegal(result.getRealUrl())) {
            ErrorEvent.url();
        } else {
            setMediaItem(result, timeout);
        }
    }

    private void startParse(Result result, boolean useParse) {
        stopParse();
        parseJob = ParseJob.create(this).start(result, useParse);
    }

    private void stopParse() {
        if (parseJob != null) parseJob.stop();
        parseJob = null;
    }

    private Map<String, String> checkUa(Map<String, String> headers) {
        for (Map.Entry<String, String> header : headers.entrySet()) if (HttpHeaders.USER_AGENT.equalsIgnoreCase(header.getKey())) return headers;
        headers.put(HttpHeaders.USER_AGENT, Setting.getUa().isEmpty() ? ExoUtil.getUa() : Setting.getUa());
        return headers;
    }

    private List<Sub> checkSub(List<Sub> subs) {
        if (sub == null) return subs;
        subs.add(0, sub);
        return subs;
    }

    public void setMediaItem() {
        setMediaItem(headers, url, format, drm, subs, Constant.TIMEOUT_PLAY);
    }

    public void setMediaItem(String url) {
        setMediaItem(new HashMap<>(), url);
    }

    private void setMediaItem(Map<String, String> headers, String url) {
        setMediaItem(headers, url, null, null, new ArrayList<>(), Constant.TIMEOUT_PLAY);
    }

    private void setMediaItem(Channel channel, int timeout) {
        setMediaItem(channel.getHeaders(), channel.getUrl(), channel.getFormat(), channel.getDrm(), new ArrayList<>(), timeout);
    }

    private void setMediaItem(Result result, int timeout) {
        setMediaItem(result.getHeaders(), result.getRealUrl(), result.getFormat(), result.getDrm(), result.getSubs(), timeout);
    }

    private void setMediaItem(Map<String, String> headers, String url, String format, Drm drm, List<Sub> subs, int timeout) {
        if (exoPlayer != null) exoPlayer.setMediaItem(ExoUtil.getMediaItem(this.headers = checkUa(headers), UrlUtil.uri(this.url = url), this.format = format, this.drm = drm, checkSub(this.subs = subs), decode), position);
        if (exoPlayer != null) exoPlayer.prepare();
        App.post(runnable, timeout);
        session.setActive(true);
        PlayerEvent.prepare();
        Logger.t(TAG).d(url);
    }

    private void removeTimeoutCheck() {
        App.removeCallbacks(runnable);
    }

    public void setTrack(List<Track> tracks) {
        for (Track track : tracks) setTrack(track);
    }

    private void setTrack(Track item) {
        if (item.isSelected()) {
            ExoUtil.selectTrack(exoPlayer, item.getGroup(), item.getTrack());
        } else {
            ExoUtil.deselectTrack(exoPlayer, item.getGroup(), item.getTrack());
        }
    }

    private void setPlaybackState(int state) {
        long actions = PlaybackStateCompat.ACTION_SEEK_TO | PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
        session.setPlaybackState(new PlaybackStateCompat.Builder().setActions(actions).setState(state, getPosition(), getSpeed()).build());
    }

    private boolean isIllegal(String url) {
        Uri uri = UrlUtil.uri(url);
        String host = UrlUtil.host(uri);
        String scheme = UrlUtil.scheme(uri);
        if ("data".equals(scheme)) return false;
        return scheme.isEmpty() || "file".equals(scheme) ? !Path.exists(url) : host.isEmpty();
    }

    private MediaMetadataCompat.Builder putBitmap(MediaMetadataCompat.Builder builder, Drawable drawable) {
        try {
            return builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, ((BitmapDrawable) drawable).getBitmap());
        } catch (Exception ignored) {
            return builder;
        }
    }

    public void setMetadata(String title, String artist, String artUri, Drawable drawable) {
        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();
        builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title);
        builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist);
        builder.putString(MediaMetadataCompat.METADATA_KEY_ART_URI, artUri);
        builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, artUri);
        builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, artUri);
        builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getDuration());
        session.setMetadata(putBitmap(builder, drawable).build());
        ActionEvent.update();
    }

    public void share(Activity activity, CharSequence title) {
        try {
            if (isEmpty()) return;
            Bundle bundle = new Bundle();
            for (Map.Entry<String, String> entry : getHeaders().entrySet()) bundle.putString(entry.getKey(), entry.getValue());
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(Intent.EXTRA_TEXT, getUrl());
            intent.putExtra("extra_headers", bundle);
            intent.putExtra("title", title);
            intent.putExtra("name", title);
            intent.setType("text/plain");
            activity.startActivity(Util.getChooser(intent));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void choose(Activity activity, CharSequence title) {
        try {
            if (isEmpty()) return;
            List<String> list = new ArrayList<>();
            for (Map.Entry<String, String> entry : getHeaders().entrySet()) list.addAll(Arrays.asList(entry.getKey(), entry.getValue()));
            Uri data = getUrl().startsWith("file://") || getUrl().startsWith("/") ? FileUtil.getShareUri(getUrl()) : Uri.parse(getUrl());
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setDataAndType(data, "video/*");
            intent.putExtra("title", title);
            intent.putExtra("return_result", isVod());
            intent.putExtra("headers", list.toArray(new String[0]));
            if (isVod()) intent.putExtra("position", (int) getPosition());
            activity.startActivityForResult(Util.getChooser(intent), 1001);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void checkData(Intent data) {
        try {
            if (data == null || data.getExtras() == null) return;
            int position = data.getExtras().getInt("position", 0);
            String endBy = data.getExtras().getString("end_by", "");
            if ("playback_completion".equals(endBy)) ActionEvent.next();
            if ("user".equals(endBy)) seekTo(position);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onParseSuccess(Map<String, String> headers, String url, String from) {
        if (!TextUtils.isEmpty(from)) Notify.show(ResUtil.getString(R.string.parse_from, from));
        setMediaItem(headers, url);
    }

    @Override
    public void onParseError() {
        ErrorEvent.parse();
    }

    @Override
    public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
        if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_MEDIA_METADATA_CHANGED, Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_PLAY_WHEN_READY_CHANGED, Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)) {
            setPlaybackState(isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
        }
    }

    @Override
    public void onPlayerError(@NonNull PlaybackException error) {
        setPlaybackState(PlaybackStateCompat.STATE_ERROR);
        Logger.t(TAG).e(error.errorCode + "," + url);
        ErrorEvent.url(error.errorCode);
    }

    @Override
    public void onPlaybackStateChanged(int state) {
        switch (state) {
            case Player.STATE_IDLE:
            case Player.STATE_READY:
            case Player.STATE_ENDED:
                PlayerEvent.state(state);
                break;
            case Player.STATE_BUFFERING:
                PlayerEvent.state(state);
                setPlaybackState(PlaybackStateCompat.STATE_BUFFERING);
                break;
        }
    }
}
