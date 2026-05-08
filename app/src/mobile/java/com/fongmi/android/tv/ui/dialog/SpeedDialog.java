package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogSpeedBinding;
import com.fongmi.android.tv.impl.SpeedCallback;
import com.fongmi.android.tv.setting.PlayerSetting;

public class SpeedDialog extends BaseAlertDialog {

    private DialogSpeedBinding binding;
    private float value;

    public static void show(Fragment fragment) {
        new SpeedDialog().show(fragment.getChildFragmentManager(), null);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        setBinding();
        initView();
        return builder().setTitle(R.string.player_speed).setView(binding.getRoot()).setPositiveButton(R.string.dialog_positive, this::onPositive).setNegativeButton(R.string.dialog_negative, this::onNegative).create();
    }

    private void setBinding() {
        binding = DialogSpeedBinding.inflate(getLayoutInflater());
    }

    private void initView() {
        binding.slider.setValue(value = PlayerSetting.getSpeed());
    }

    private void onPositive(DialogInterface dialog, int which) {
        ((SpeedCallback) requireParentFragment()).setSpeed(binding.slider.getValue());
    }

    private void onNegative(DialogInterface dialog, int which) {
        ((SpeedCallback) requireParentFragment()).setSpeed(value);
    }
}