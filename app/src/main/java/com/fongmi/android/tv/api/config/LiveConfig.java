package com.fongmi.android.tv.api.config;

import android.net.Uri;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.Decoder;
import com.fongmi.android.tv.api.LiveParser;
import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Depot;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Rule;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.ui.activity.LiveActivity;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.bean.Header;
import com.github.catvod.bean.Proxy;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LiveConfig {

    private Live home;
    private Config config;
    private List<Live> lives;
    private List<Rule> rules;
    private List<String> ads;
    private Future<?> future;
    private boolean sync;

    private static class Loader {
        static volatile LiveConfig INSTANCE = new LiveConfig();
    }

    public static LiveConfig get() {
        return Loader.INSTANCE;
    }

    public static String getUrl() {
        return get().getConfig().getUrl();
    }

    public static String getDesc() {
        // 修改：使用Config的getDisplayName方法
        return get().getConfig().getDisplayName();
    }

    public static String getResp() {
        return get().getHome().getCore().getResp();
    }

    public static int getHomeIndex() {
        return get().getLives().indexOf(get().getHome());
    }

    public static boolean isOnly() {
        return get().getLives().size() == 1;
    }

    public static boolean isEmpty() {
        return get().getHome().isEmpty();
    }

    public static boolean hasUrl() {
        return getUrl() != null && !getUrl().isEmpty();
    }

    public static void load(Config config, Callback callback) {
        get().clear().config(config).load(callback);
    }

    public LiveConfig init() {
        this.home = null;
        this.ads = new ArrayList<>();
        this.rules = new ArrayList<>();
        this.lives = new ArrayList<>();

        // 确保配置URL不为空
        Config liveConfig = Config.live();
        if (TextUtils.isEmpty(liveConfig.getUrl())) {
            liveConfig.url(Constants.BUILTIN_PLACEHOLDER);
        }

        return config(liveConfig);
    }

    public LiveConfig config(Config config) {
        this.config = config;
        if (config.isEmpty()) return this;
        this.sync = config.getUrl().equals(VodConfig.getUrl());
        return this;
    }

    public LiveConfig clear() {
        this.home = null;
        this.ads.clear();
        this.rules.clear();
        this.lives.clear();
        return this;
    }

    public void load() {
        load(new Callback());
    }

    public void load(Callback callback) {
        if (future != null && !future.isDone()) future.cancel(true);
        future = App.submit(() -> loadConfig(callback));
        callback.start();
    }

    private void loadConfig(Callback callback) {
        try {
            // 添加URL空值检查
            if (TextUtils.isEmpty(config.getUrl()) || config.isBuiltin()) {
                // 如果是内置配置，使用真实URL加载
                String loadUrl = config.isBuiltin() ? Constants.BUILTIN_URL : config.getUrl();
                if (TextUtils.isEmpty(loadUrl)) {
                    App.post(() -> callback.error("直播配置URL为空，请检查配置"));
                    return;
                }

                Server.get().start();
                String text = Decoder.getJson(UrlUtil.convert(loadUrl));
                if (!Json.isObj(text)) clear().parseText(text, callback);
                else checkJson(Json.parse(text).getAsJsonObject(), callback);
                config.update();
            } else {
                Server.get().start();
                String text = Decoder.getJson(UrlUtil.convert(config.getUrl()));
                if (!Json.isObj(text)) clear().parseText(text, callback);
                else checkJson(Json.parse(text).getAsJsonObject(), callback);
                config.update();
            }
        } catch (Throwable e) {
            if (TextUtils.isEmpty(config.getUrl())) App.post(() -> callback.error(""));
            else App.post(() -> callback.error(Notify.getError(R.string.error_config_get, e)));
            e.printStackTrace();
        }
    }

    private void parseText(String text, Callback callback) {
        try {
            Live live = new Live(parseName(config.getUrl()), config.getUrl()).sync();
            lives = new ArrayList<>(List.of(live));
            LiveParser.text(live, text);
            setHome(live, false);
            App.post(callback::success);
        } catch (Exception e) {
            e.printStackTrace();
            App.post(() -> callback.error(Notify.getError(R.string.error_config_parse, e)));
        }
    }

    private String parseName(String url) {
        if (TextUtils.isEmpty(url)) return "默认直播";

        Uri uri = Uri.parse(url);
        if ("file".equals(uri.getScheme())) return new File(url).getName();
        if (uri.getLastPathSegment() != null) return uri.getLastPathSegment();
        if (uri.getQuery() != null) return uri.getQuery();
        if (uri.getHost() != null) return uri.getHost();
        return url;
    }

    private void checkJson(JsonObject object, Callback callback) {
        if (object == null || object.entrySet().isEmpty()) {
            App.post(() -> callback.error("直播配置内容为空"));
            return;
        }

        if (object.has("msg")) {
            App.post(() -> callback.error(object.get("msg").getAsString()));
        } else if (object.has("urls")) {
            parseDepot(object, callback);
        } else {
            parseConfig(object, callback);
        }
    }

    private void parseDepot(JsonObject object, Callback callback) {
        try {
            List<Depot> items = Depot.arrayFrom(object.getAsJsonArray("urls").toString());
            List<Config> configs = new ArrayList<>();
            for (Depot item : items) configs.add(Config.find(item, 1));
            Config.delete(config.getUrl());
            config = configs.get(0);
            loadConfig(callback);
        } catch (Exception e) {
            e.printStackTrace();
            App.post(() -> callback.error(Notify.getError(R.string.error_config_parse, e)));
        }
    }

    private void parseConfig(JsonObject object, Callback callback) {
        try {
            clear();

            // 检查配置对象是否有效
            if (object == null || object.entrySet().isEmpty()) {
                App.post(() -> callback.error("直播配置内容为空"));
                return;
            }

            initLive(object);
            initOther(object);
            if (callback != null) App.post(callback::success);
        } catch (Throwable e) {
            e.printStackTrace();
            if (callback != null) App.post(() -> callback.error(Notify.getError(R.string.error_config_parse, e)));
        }
    }

    private void initLive(JsonObject object) {
        try {
            String spider = Json.safeString(object, "spider");
            BaseLoader.get().parseJar(spider, false);

            // 检查lives配置是否存在
            if (Json.isEmpty(object, "lives")) {
                // 如果lives为空，创建默认直播配置
                createDefaultLive();
                return;
            }

            setLives(Json.safeListElement(object, "lives").stream().map(element -> Live.objectFrom(element, spider)).distinct().collect(Collectors.toCollection(ArrayList::new)));
            Map<String, Live> items = Live.findAll().stream().collect(Collectors.toMap(Live::getName, Function.identity()));
            for (Live live : getLives()) {
                Live item = items.get(live.getName());
                if (item != null) live.sync(item);
                if (live.getName().equals(config.getHome())) setHome(live, false);
            }

            // 如果没有有效的直播配置，创建默认配置
            if (getLives().isEmpty()) {
                createDefaultLive();
            }
        } catch (Exception e) {
            // 如果解析失败，创建默认直播配置
            createDefaultLive();
            e.printStackTrace();
        }
    }

    private void createDefaultLive() {
        Live defaultLive = new Live("默认直播", config.getUrl()).sync();
        lives = new ArrayList<>(List.of(defaultLive));
        setHome(defaultLive, false);
    }

    private void initOther(JsonObject object) {
        if (home == null) setHome(lives.isEmpty() ? new Live() : lives.get(0), false);
        setHeaders(Header.arrayFrom(object.getAsJsonArray("headers")));
        setProxy(Proxy.arrayFrom(object.getAsJsonArray("proxy")));
        setRules(Rule.arrayFrom(object.getAsJsonArray("rules")));
        setHosts(Json.safeListString(object, "hosts"));
        setAds(Json.safeListString(object, "ads"));
    }

    private void bootLive() {
        Setting.putBootLive(false);
        LiveActivity.start(App.get());
    }

    public void parse(JsonObject object) {
        parseConfig(object, null);
    }

    public void setKeep(Channel channel) {
        if (home != null && !channel.getGroup().isHidden()) home.keep(channel).save();
    }

    public void setKeep(List<Group> items) {
        Set<String> key = Keep.getLive().stream().map(Keep::getKey).collect(Collectors.toSet());
        items.stream().filter(group -> !group.isKeep())
                .flatMap(group -> group.getChannel().stream())
                .filter(channel -> key.contains(channel.getName()))
                .forEach(channel -> items.get(0).add(channel));
    }

    public int[] find(List<Group> items) {
        String[] splits = getHome().getKeep().split(AppDatabase.SYMBOL);
        if (splits.length < 3) return new int[]{1, 0};
        for (int i = 0; i < items.size(); i++) {
            Group group = items.get(i);
            if (group.getName().equals(splits[0])) {
                int j = group.find(splits[1]);
                if (j != -1) {
                    group.getChannel().get(j).setLine(splits[2]);
                    return new int[]{i, j};
                }
            }
        }
        return new int[]{1, 0};
    }

    public int[] find(String number, List<Group> items) {
        for (int i = 0; i < items.size(); i++) {
            int j = items.get(i).find(Integer.parseInt(number));
            if (j != -1) return new int[]{i, j};
        }
        return new int[]{-1, -1};
    }

    public boolean needSync(String url) {
        if (TextUtils.isEmpty(url) || TextUtils.isEmpty(config.getUrl())) {
            return false;
        }
        return sync || TextUtils.isEmpty(config.getUrl()) || url.equals(config.getUrl());
    }

    public List<Live> getLives() {
        return lives == null ? lives = new ArrayList<>() : lives;
    }

    private void setLives(List<Live> lives) {
        this.lives = lives;
    }

    public List<Rule> getRules() {
        return rules == null ? Collections.emptyList() : rules;
    }

    private void setRules(List<Rule> rules) {
        this.rules = rules;
    }

    private void setHeaders(List<Header> headers) {
        OkHttp.responseInterceptor().addAll(headers);
    }

    private void setProxy(List<Proxy> proxy) {
        OkHttp.authenticator().addAll(proxy);
        OkHttp.selector().addAll(proxy);
    }

    private void setHosts(List<String> hosts) {
        OkHttp.dns().addAll(hosts);
    }

    public List<String> getAds() {
        return ads == null ? Collections.emptyList() : ads;
    }

    private void setAds(List<String> ads) {
        this.ads = ads;
    }

    public Config getConfig() {
        return config == null ? Config.live() : config;
    }

    public Live getHome() {
        return home == null ? new Live() : home;
    }

    public Live getLive(String key) {
        int index = getLives().indexOf(Live.get(key));
        return index == -1 ? new Live() : getLives().get(index);
    }

    public void setHome(Live home) {
        setHome(home, true);
    }

    private void setHome(Live live, boolean save) {
        home = live;
        home.setActivated(true);
        config.home(home.getName());
        if (save) config.save();
        getLives().forEach(item -> item.setActivated(home));
        if (App.activity() != null && App.activity() instanceof LiveActivity) return;
        if (!save && (home.isBoot() || Setting.isBootLive())) App.post(this::bootLive);
    }
}