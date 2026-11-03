package com.team23.trojanrideshare.ui.common;

import android.view.View;

import com.team23.trojanrideshare.R;
import com.team23.trojanrideshare.databinding.ItemRideBinding;
import com.team23.trojanrideshare.domain.ActivityStatus;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.domain.util.Money;

/** Binds a ride to the shared ride card layout (item_ride.xml). */
public final class RideViews {

    private RideViews() {
    }

    public static void bind(ItemRideBinding b, Ride r) {
        b.title.setText(r.getTitle());
        StatusStyle.activity(b.activityChip, r.getActivityStatus());
        b.route.setText(UiFormat.route(r.getOrigin(), r.getDestination()));
        b.when.setText(UiFormat.dateTime(r.getDeparture()));
        StatusStyle.capacity(b.capacityChip, r.displayCapacity(), r.capacityLabel());
        if (r.getActivityStatus() == ActivityStatus.COMPLETED) {
            b.cost.setText("Actual " + Money.format(r.getActualCostCents()));
        } else {
            b.cost.setText("Est. " + Money.format(r.getExpectedCostCents()));
        }
        b.organizer.setText("Organized by " + r.getOrganizerName());
        b.tag.setVisibility(View.GONE);
        b.extra.setVisibility(View.GONE);
        b.publicSwitch.setVisibility(View.GONE);
        b.publicSwitch.setOnCheckedChangeListener(null);
    }

    public static void tag(ItemRideBinding b, String text, boolean highlight) {
        b.tag.setVisibility(View.VISIBLE);
        if (highlight) StatusStyle.apply(b.tag, text, R.color.ink, R.color.gold);
        else StatusStyle.apply(b.tag, text, R.color.ink_muted, R.color.surface_variant);
    }

    public static void extra(ItemRideBinding b, CharSequence text) {
        if (text == null || text.length() == 0) {
            b.extra.setVisibility(View.GONE);
        } else {
            b.extra.setVisibility(View.VISIBLE);
            b.extra.setText(text);
        }
    }
}
