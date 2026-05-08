package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogThemeBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.adapter.ThemeAdapter;

public class ThemeDialog extends BaseAlertDialog implements ThemeAdapter.OnClickListener {

    private static final int[] COLORS = {0, 0xFF6750A4, 0xFF3949AB, 0xFF1E88E5, 0xFF00ACC1, 0xFF00897B, 0xFF43A047, 0xFF7CB342, 0xFFFB8C00, 0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF6D4C41,};
    private DialogThemeBinding binding;

    public static void show(Fragment fragment) {
        new ThemeDialog().show(fragment.getChildFragmentManager(), null);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        setBinding();
        initView();
        return builder().setTitle(R.string.setting_theme_color).setView(binding.getRoot()).create();
    }

    private void setBinding() {
        binding = DialogThemeBinding.inflate(getLayoutInflater());
    }

    private void initView() {
        binding.recycler.setAdapter(new ThemeAdapter(this, COLORS, Setting.getThemeColor()));
    }

    @Override
    public void onItemClick(int color) {
        Setting.putThemeColor(color);
        requireActivity().recreate();
        dismiss();
    }
}

