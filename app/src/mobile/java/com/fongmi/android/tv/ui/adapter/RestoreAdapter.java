package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.databinding.AdapterRestoreBinding;
import com.fongmi.android.tv.impl.Callback;
import com.github.catvod.utils.Path;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RestoreAdapter extends RecyclerView.Adapter<RestoreAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final SimpleDateFormat format;
    private final List<File> mItems;

    private final Callback callback = new Callback() {
        @Override
        public void success() {
            notifyDataSetChanged();
        }
    };

    public RestoreAdapter(OnClickListener listener) {
        this.format = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        this.mItems = new ArrayList<>();
        this.mListener = listener;
        this.addAll();
    }

    public interface OnClickListener {

        void onItemClick(File item);

        void onDeleteClick(File item);
    }

    private void addAll() {
        App.execute(() -> {
            File[] files = Path.tv().listFiles();
            if (files == null || files.length == 0) return;
            for (File file : files) if (file.getName().startsWith("tv") && file.getName().endsWith(".bk.gz")) mItems.add(file);
            callback.success();
        });
    }

    public void remove(File item) {
        int position = mItems.indexOf(item);
        if (position == -1) return;
        Path.clear(item);
        mItems.remove(position);
        notifyItemRemoved(position);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterRestoreBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        File item = mItems.get(position);
        holder.binding.name.setText(item.getName());
        holder.binding.time.setText(format.format(item.lastModified()));
        holder.binding.delete.setOnClickListener(v -> mListener.onDeleteClick(item));
        holder.binding.getRoot().setOnClickListener(v -> mListener.onItemClick(item));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterRestoreBinding binding;

        ViewHolder(@NonNull AdapterRestoreBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
