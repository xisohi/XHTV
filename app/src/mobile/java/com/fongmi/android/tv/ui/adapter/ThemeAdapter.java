package com.fongmi.android.tv.ui.adapter;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.databinding.AdapterThemeBinding;

public class ThemeAdapter extends RecyclerView.Adapter<ThemeAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final int[] mItems;
    private int selected;

    public ThemeAdapter(OnClickListener listener, int[] items, int selected) {
        this.listener = listener;
        this.mItems = items;
        this.selected = selected;
    }

    public interface OnClickListener {
        void onItemClick(int color);
    }

    public void setSelected(int color) {
        selected = color;
        notifyItemRangeChanged(0, getItemCount());
    }

    @Override
    public int getItemCount() {
        return mItems.length;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterThemeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        int color = mItems[position];
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        if (color == 0) {
            circle.setColors(new int[]{0xFFE53935, 0xFF2196F3, 0xFF4CAF50, 0xFFE53935});
            circle.setGradientType(GradientDrawable.SWEEP_GRADIENT);
        } else {
            circle.setColor(color);
        }
        holder.binding.circle.setBackground(circle);
        holder.binding.check.setVisibility(selected == color ? View.VISIBLE : View.INVISIBLE);
        holder.binding.getRoot().setOnClickListener(v -> listener.onItemClick(color));
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterThemeBinding binding;

        ViewHolder(@NonNull AdapterThemeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
