package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogBufferBinding;
import com.fongmi.android.tv.impl.BufferCallback;
import com.fongmi.android.tv.setting.PlayerSetting;

public class BufferDialog extends BaseAlertDialog {

    private DialogBufferBinding binding;
    private int value;

    public static void show(Fragment fragment) {
        new BufferDialog().show(fragment.getChildFragmentManager(), null);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        setBinding();
        initView();
        return builder().setTitle(R.string.player_buffer).setView(binding.getRoot()).setPositiveButton(R.string.dialog_positive, this::onPositive).setNegativeButton(R.string.dialog_negative, this::onNegative).create();
    }

    private void setBinding() {
        binding = DialogBufferBinding.inflate(getLayoutInflater());
    }

    private void initView() {
        binding.slider.setValue(value = PlayerSetting.getBuffer());
    }

    private void onPositive(DialogInterface dialog, int which) {
        ((BufferCallback) requireParentFragment()).setBuffer((int) binding.slider.getValue());
    }

    private void onNegative(DialogInterface dialog, int which) {
        ((BufferCallback) requireParentFragment()).setBuffer(value);
    }
}