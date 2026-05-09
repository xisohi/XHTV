package com.fongmi.android.tv.ui.adapter;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.databinding.AdapterThemeBinding;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;

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
        holder.binding.circle.setBackground(getCircle(color));
        holder.binding.getRoot().setOnClickListener(v -> listener.onItemClick(color));
        holder.binding.check.setVisibility(selected == color ? View.VISIBLE : View.INVISIBLE);
    }

    private GradientDrawable getCircle(int color) {
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(color == 0 ? getPrimaryColor() : color);
        return circle;
    }

    private int getPrimaryColor() {
        return MaterialColors.getColor(DynamicColors.wrapContextIfAvailable(App.get()), android.R.attr.colorPrimary, 0xFF6750A4);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterThemeBinding binding;

        ViewHolder(@NonNull AdapterThemeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
