package com.fongmi.android.tv.ui.dialog;

import android.view.View;

import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.DialogUpdateBinding;
import com.fongmi.android.tv.impl.UpdateListener;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;

public class UpdateDialog extends BaseAlertDialog {

    private DialogUpdateBinding binding;
    private UpdateListener listener;
    private String title;
    private String desc;
    private boolean isDownloading = false; // 标记下载状态

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
        // 添加空指针保护
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return this;
        }
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
        if (binding == null) return;
        binding.version.setText(title);
        binding.desc.setText(desc);
        // 初始化时确认按钮可用
        binding.confirm.setEnabled(true);
        binding.confirm.setText(com.fongmi.android.tv.R.string.update_confirm);
    }

    @Override
    protected void initEvent() {
        if (binding == null) return;
        binding.confirm.setOnClickListener(this::onConfirm);
        binding.cancel.setOnClickListener(this::onCancel);
    }

    /**
     * 设置下载进度
     * @param progress 0-100
     */
    public void setProgress(int progress) {
        if (binding == null) return;

        if (progress >= 0 && progress <= 100) {
            isDownloading = true;
            binding.confirm.setText(String.format(Locale.getDefault(), "%d%%", progress));
            binding.confirm.setEnabled(false); // 下载中禁止点击
            binding.cancel.setEnabled(false);   // 下载中禁止取消（可选）
        }

        // 下载完成恢复按钮
        if (progress >= 100) {
            resetConfirmButton();
        }
    }

    /**
     * 重置确认按钮（用于重试场景）
     */
    public void resetConfirmButton() {
        if (binding == null) return;
        isDownloading = false;
        binding.confirm.setText(com.fongmi.android.tv.R.string.update_confirm);
        binding.confirm.setEnabled(true);
        binding.cancel.setEnabled(true);
    }

    /**
     * 显示重试状态（不覆盖进度）
     */
    public void showRetryStatus(int retryCount, int maxRetry) {
        if (binding == null) return;
        if (!isDownloading) {
            binding.confirm.setText(String.format("重试(%d/%d)", retryCount, maxRetry));
            binding.confirm.setEnabled(false);
        }
    }

    private void onConfirm(View view) {
        if (listener == null) return;
        if (isDownloading) return; // 下载中不响应确认
        if (view != null) view.setEnabled(false);
        listener.onConfirm(view);
    }

    private void onCancel(View view) {
        if (listener == null) return;
        listener.onCancel(view);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // 防止内存泄漏
    }
}