package com.team23.trojanrideshare.ui.common;

import android.content.res.ColorStateList;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.team23.trojanrideshare.R;
import com.team23.trojanrideshare.domain.ActivityStatus;
import com.team23.trojanrideshare.domain.CapacityStatus;
import com.team23.trojanrideshare.domain.PaymentStatus;
import com.team23.trojanrideshare.domain.RequestStatus;

/** Colors for status chips. */
public final class StatusStyle {

    private StatusStyle() {
    }

    public static void apply(TextView chip, String text, int fg, int bg) {
        chip.setText(text);
        chip.setTextColor(ContextCompat.getColor(chip.getContext(), fg));
        chip.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(chip.getContext(), bg)));
    }

    public static void activity(TextView chip, ActivityStatus s) {
        switch (s) {
            case DELAYED:
                apply(chip, s.label(), R.color.status_delayed, R.color.status_delayed_bg);
                break;
            case CANCELLED:
                apply(chip, s.label(), R.color.status_cancelled, R.color.status_cancelled_bg);
                break;
            case COMPLETED:
                apply(chip, s.label(), R.color.status_completed, R.color.status_completed_bg);
                break;
            default:
                apply(chip, s.label(), R.color.status_ontime, R.color.status_ontime_bg);
        }
    }

    public static void capacity(TextView chip, CapacityStatus s, String label) {
        if (s == CapacityStatus.FULL) {
            apply(chip, label, R.color.cardinal_dark, R.color.cardinal_light);
        } else if (s == CapacityStatus.OPEN) {
            apply(chip, label, R.color.status_ontime, R.color.status_ontime_bg);
        } else {
            apply(chip, label, R.color.status_cancelled, R.color.status_cancelled_bg);
        }
    }

    public static void request(TextView chip, RequestStatus s) {
        switch (s) {
            case ACCEPTED:
                apply(chip, s.label(), R.color.status_ontime, R.color.status_ontime_bg);
                break;
            case REJECTED:
                apply(chip, s.label(), R.color.cardinal_dark, R.color.cardinal_light);
                break;
            default:
                apply(chip, s.label(), R.color.status_delayed, R.color.status_delayed_bg);
        }
    }

    public static void payment(TextView chip, PaymentStatus s) {
        if (s == PaymentStatus.PAID) apply(chip, "Paid", R.color.status_ontime, R.color.status_ontime_bg);
        else apply(chip, "Unpaid", R.color.status_delayed, R.color.status_delayed_bg);
    }
}
