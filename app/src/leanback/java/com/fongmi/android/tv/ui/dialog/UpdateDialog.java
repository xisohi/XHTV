package com.fongmi.android.tv.ui.dialog;

import android.util.Log;
import android.view.View;

import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.DialogUpdateBinding;
import com.fongmi.android.tv.impl.UpdateListener;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;

public class UpdateDialog extends BaseAlertDialog {

    private static final String TAG = "UpdateDialog";

    private DialogUpdateBinding binding;
    private UpdateListener listener;
    private String title;
    private String desc;

    public static UpdateDialog create() {
        return new UpdateDialog();
    }

    public UpdateDialog title(String title) {
        this.title = title;
        return this;
    }

    public UpdateDialog desc(String desc) {
        this.desc = desc;
        return this;
    }

    public UpdateDialog listener(UpdateListener listener) {
        this.listener = listener;
        return this;
    }

    public UpdateDialog show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
        return this;
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogUpdateBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot()).setCancelable(false);
    }

    @Override
    protected void initView() {
        binding.version.setText(title);
        binding.desc.setText(desc);
    }

    @Override
    protected void initEvent() {
        binding.confirm.setOnClickListener(this::onConfirm);
        binding.cancel.setOnClickListener(this::onCancel);
    }

    /**
     * 设置下载进度（百分比），自动显示为 "xx%"
     */
    public void setProgress(int progress) {
        Log.d(TAG, "setProgress called, progress=" + progress + ", binding.confirm=" + binding.confirm);
        if (progress < 0) {
            Log.w(TAG, "progress < 0, ignoring");
            return;
        }
        String text = String.format(Locale.getDefault(), "%1$d%%", progress);
        binding.confirm.setText(text);
        Log.d(TAG, "setProgress set text: " + text);
    }

    /**
     * 设置按钮状态文字（如“正在测速…”）
     */
    public void setStatus(String status) {
        Log.d(TAG, "setStatus called, status=" + status);
        if (status == null) {
            // 如果传 null，可以恢复默认文字？这里暂不处理
            return;
        }
        binding.confirm.setText(status);
    }

    private void onConfirm(View view) {
        listener.onConfirm(view);
    }

    private void onCancel(View view) {
        listener.onCancel(view);
    }
}