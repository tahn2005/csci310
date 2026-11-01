package com.team23.trojanrideshare.ui.common;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.team23.trojanrideshare.databinding.ItemRideBinding;
import com.team23.trojanrideshare.domain.Ride;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** A list of ride cards. {@code decorate} adds screen-specific tags (e.g. "Joined"). */
public class RideAdapter<T> extends RecyclerView.Adapter<RideAdapter.Holder> {

    public interface Decorator<T> {
        void decorate(ItemRideBinding b, T item);
    }

    public interface RideOf<T> {
        Ride ride(T item);
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemRideBinding b;

        Holder(ItemRideBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    private final List<T> items = new ArrayList<>();
    private final RideOf<T> rideOf;
    private final Decorator<T> decorator;
    private final Consumer<T> onClick;

    public RideAdapter(RideOf<T> rideOf, Decorator<T> decorator, Consumer<T> onClick) {
        this.rideOf = rideOf;
        this.decorator = decorator;
        this.onClick = onClick;
    }

    public static RideAdapter<Ride> forRides(Decorator<Ride> decorator, Consumer<Ride> onClick) {
        return new RideAdapter<>(r -> r, decorator, onClick);
    }

    public void submit(List<T> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemRideBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        T item = items.get(position);
        RideViews.bind(h.b, rideOf.ride(item));
        if (decorator != null) decorator.decorate(h.b, item);
        h.b.card.setOnClickListener(v -> onClick.accept(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}
