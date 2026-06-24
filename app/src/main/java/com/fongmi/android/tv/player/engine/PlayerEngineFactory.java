package com.fongmi.android.tv.player.engine;

import androidx.media3.common.Player;

import com.fongmi.android.tv.player.exo.ExoPlayerEngine;
import com.fongmi.android.tv.player.media.PlaySpec;
import com.fongmi.android.tv.player.mpv.MpvPlayerEngine;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.utils.UrlUtil;

public final class PlayerEngineFactory {

    public static PlayerEngine create(int decode, PlaySpec spec, Player.Listener listener) {
        return switch (resolve(spec)) {
            case MPV -> new MpvPlayerEngine(decode, listener);
            case EXO -> new ExoPlayerEngine(decode, listener);
        };
    }

    public static boolean matches(PlayerEngine engine, PlaySpec spec) {
        return engine != null && engine.getType() == resolve(spec);
    }

    private static PlayerEngine.Type resolve(PlaySpec spec) {
        if (canUseMpv(spec)) return PlayerEngine.Type.MPV;
        return PlayerEngine.Type.EXO;
    }

    private static boolean canUseMpv(PlaySpec spec) {
        if (spec.getDrm() != null) return false;
        if (!PlayerSetting.isMpv()) return false;
        if (!MpvPlayerEngine.isAvailable()) return false;
        return !"smb".equals(UrlUtil.scheme(spec.getUrl()));
    }
}
