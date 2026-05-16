package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogUpdateBinding;
import com.fongmi.android.tv.impl.UpdateListener;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;

public class UpdateDialog extends BaseAlertDialog {

    private DialogUpdateBinding binding;
    private UpdateListener listener;
    private String title;
    private String desc;
    private boolean isDownloading = false;

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
        // 添加 Activity 状态检查
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
        MaterialAlertDialogBuilder builder = builder()
                .setView(getBinding().getRoot())
                .setPositiveButton(R.string.update_confirm, null)  // listener 设为 null，手动设置
                .setNegativeButton(R.string.dialog_negative, null)
                .setCancelable(false);

        // 设置标题（避免 null）
        if (title != null && !title.isEmpty()) {
            builder.setTitle(title);
        }

        return builder;
    }

    @Override
    protected void initView() {
        if (binding == null) return;
        // 设置更新内容
        if (desc != null) {
            binding.desc.setText(desc);
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        Dialog dialog = getDialog();
        if (dialog == null || listener == null) return;

        AlertDialog alertDialog = (AlertDialog) dialog;

        // 获取按钮
        Button positiveButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negativeButton = alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE);

        // 设置初始状态
        if (positiveButton != null) {
            positiveButton.setEnabled(true);
            positiveButton.setText(R.string.update_confirm);
            positiveButton.setOnClickListener(view -> {
                if (!isDownloading && listener != null) {
                    listener.onConfirm(view);
                }
            });
        }

        // 取消按钮
        if (negativeButton != null) {
            negativeButton.setOnClickListener(view -> {
                if (listener != null) {
                    listener.onCancel(view);
                }
            });
        }
    }

    /**
     * 设置下载进度
     * @param progress 0-100
     */
    public void setProgress(int progress) {
        AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog == null) return;

        Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positiveButton == null) return;

        if (progress >= 0 && progress <= 100) {
            isDownloading = true;
            positiveButton.setText(String.format(Locale.getDefault(), "%d%%", progress));
            positiveButton.setEnabled(false);  // 下载中不可点击
        }

        // 下载完成，恢复按钮
        if (progress >= 100) {
            resetButton();
        }
    }

    /**
     * 重置按钮（用于重试场景）
     */
    public void resetButton() {
        AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog == null) return;

        Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positiveButton == null) return;

        isDownloading = false;
        positiveButton.setText(R.string.update_confirm);
        positiveButton.setEnabled(true);
    }

    /**
     * 显示重试状态（可选）
     */
    public void setRetryStatus(int retryCount, int maxRetry) {
        AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog == null) return;

        Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positiveButton == null || isDownloading) return;

        positiveButton.setText(String.format("重试(%d/%d)", retryCount, maxRetry));
        positiveButton.setEnabled(false);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;  // 防止内存泄漏
    }
}