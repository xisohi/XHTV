package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.databinding.DialogConfigBinding;
import com.fongmi.android.tv.impl.ConfigListener;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.UrlUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ConfigDialog extends BaseAlertDialog {

    private DialogConfigBinding binding;
    private boolean append = true;
    private boolean edit;
    private String ori;
    private int type;

    public static ConfigDialog create() {
        return new ConfigDialog();
    }

    public ConfigDialog vod() {
        type = 0;
        return this;
    }

    public ConfigDialog live() {
        type = 1;
        return this;
    }

    public ConfigDialog wall() {
        type = 2;
        return this;
    }

    public ConfigDialog edit() {
        edit = true;
        return this;
    }

    public void show(Fragment fragment) {
        show(fragment.getChildFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogConfigBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setTitle(type == 0 ? R.string.setting_vod : type == 1 ? R.string.setting_live : R.string.setting_wall).setView(getBinding().getRoot()).setPositiveButton(edit ? R.string.dialog_edit : R.string.dialog_positive, this::onPositive).setNegativeButton(R.string.dialog_negative, null);
    }

    @Override
    protected void initView() {
        Config config = getConfig();
        if (config == null) return;

        // ===== 修改开始：获取显示用的 URL =====
        String realUrl = config.getUrl();
        String displayUrl = getDisplayUrl(realUrl);

        // 保存原始 URL（用于编辑时判断）
        ori = realUrl;

        // 显示名称
        binding.name.setText(config.getName());

        // 显示 URL（内置源显示为"内置源"）
        binding.url.setText(displayUrl);
        binding.url.setSelection(TextUtils.isEmpty(displayUrl) ? 0 : displayUrl.length());

        binding.input.setVisibility(edit ? View.VISIBLE : View.GONE);
    }

    // ===== 新增：获取显示用的 URL =====
    private String getDisplayUrl(String url) {
        if (TextUtils.isEmpty(url) || Config.BUILTIN_URL.equals(url)) {
            return Config.BUILTIN_NAME;
        }
        return url;
    }

    @Override
    protected void initEvent() {
        binding.choose.setEndIconOnClickListener(this::onChoose);
        binding.url.addTextChangedListener(new CustomTextListener() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                detect(s.toString());
            }
        });
        binding.url.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) onPositive(null, 0);
            return true;
        });
    }

    private Config getConfig() {
        return switch (type) {
            case 0 -> VodConfig.get().getConfig();
            case 1 -> LiveConfig.get().getConfig();
            case 2 -> WallConfig.get().getConfig();
            default -> null;
        };
    }

    private void onChoose(View view) {
        FileChooser.from(launcher).show();
    }

    private void detect(String s) {
        if (append && "h".equalsIgnoreCase(s)) {
            append = false;
            binding.url.append("ttp://");
        } else if (append && "f".equalsIgnoreCase(s)) {
            append = false;
            binding.url.append("ile://");
        } else if (append && "a".equalsIgnoreCase(s)) {
            append = false;
            binding.url.append("ssets://");
        } else if (s.length() > 1) {
            append = false;
        } else if (s.isEmpty()) {
            append = true;
        }
    }

    // ===== 修改 onPositive 方法 =====
    private void onPositive(DialogInterface dialog, int which) {
        String inputText = binding.url.getText().toString().trim();
        String name = binding.name.getText().toString().trim();

        // 如果用户输入的是"内置源"或空，则保存为空（触发内置源）
        String finalUrl = inputText;
        if (TextUtils.isEmpty(inputText) || Config.BUILTIN_NAME.equals(inputText)) {
            finalUrl = "";
            name = Config.BUILTIN_NAME;
        }

        // 编辑模式：更新已有配置
        if (edit) {
            Config.find(ori, type).url(finalUrl).name(name).update();
        }

        // 如果最终 URL 为空，删除原配置
        if (finalUrl.isEmpty()) {
            Config.delete(ori, type);
        }

        // 通知监听器
        ((ConfigListener) requireParentFragment()).setConfig(Config.find(finalUrl, type));
        dismiss();
    }

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
                ((ConfigListener) requireParentFragment()).setConfig(Config.find(
                        "file:/" + FileChooser.getPathFromUri(result.getData().getData()).replace(Path.rootPath(), ""),
                        type
                ));
                dismiss();
            }
    );
}
