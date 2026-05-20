package com.fongmi.android.tv.ui.dialog;

import android.view.View;

import androidx.media3.common.C;

import com.fongmi.android.tv.databinding.DialogOffsetBinding;
import com.fongmi.android.tv.player.PlayerManager;

import java.util.Locale;

final class OffsetPanel {

    private static final long OFFSET_MIN_MS = -10_000;
    private static final long OFFSET_MAX_MS = 10_000;
    private static final long OFFSET_STEP_MS = 100;

    private final DialogOffsetBinding binding;
    private final PlayerManager player;
    private final int type;

    OffsetPanel(DialogOffsetBinding binding, PlayerManager player, int type) {
        this.binding = binding;
        this.player = player;
        this.type = type;
    }

    void bind() {
        binding.audioSection.setVisibility(type == C.TRACK_TYPE_AUDIO ? View.VISIBLE : View.GONE);
        binding.textSection.setVisibility(type == C.TRACK_TYPE_TEXT ? View.VISIBLE : View.GONE);
        binding.audioSlider.setLabelFormatter(v -> String.format(Locale.getDefault(), "%+.1fs", v / 1000f));
        binding.textSlider.setLabelFormatter(v -> String.format(Locale.getDefault(), "%+.1fs", v / 1000f));
        binding.audioSlider.setValue(clamp(player.getAudioOffsetMs()));
        binding.textSlider.setValue(clamp(player.getTextOffsetMs()));
        setAudioValue(binding.audioSlider.getValue());
        setTextValue(binding.textSlider.getValue());
        binding.audioSlider.addOnChangeListener((slider, value, fromUser) -> onAudioChange(value));
        binding.textSlider.addOnChangeListener((slider, value, fromUser) -> onTextChange(value));
        binding.reset.setOnClickListener(this::onReset);
    }

    private void onReset(View view) {
        if (type == C.TRACK_TYPE_AUDIO) binding.audioSlider.setValue(0);
        if (type == C.TRACK_TYPE_TEXT) binding.textSlider.setValue(0);
    }

    private void onAudioChange(float value) {
        player.setAudioOffsetMs(Math.round(value / OFFSET_STEP_MS) * OFFSET_STEP_MS);
        setAudioValue(value);
    }

    private void onTextChange(float value) {
        player.setTextOffsetMs(Math.round(value / OFFSET_STEP_MS) * OFFSET_STEP_MS);
        setTextValue(value);
    }

    private void setAudioValue(float value) {
        binding.audioValue.setText(String.format(Locale.getDefault(), "%+.1fs", value / 1000f));
    }

    private void setTextValue(float value) {
        binding.textValue.setText(String.format(Locale.getDefault(), "%+.1fs", value / 1000f));
    }

    private float clamp(long value) {
        return Math.max(OFFSET_MIN_MS, Math.min(OFFSET_MAX_MS, value));
    }
}
