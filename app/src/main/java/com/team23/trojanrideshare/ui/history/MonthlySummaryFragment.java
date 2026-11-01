package com.team23.trojanrideshare.ui.history;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.team23.trojanrideshare.databinding.FragmentMonthlySummaryBinding;
import com.team23.trojanrideshare.databinding.ItemRideBinding;
import com.team23.trojanrideshare.domain.HistoryEntry;
import com.team23.trojanrideshare.domain.MonthlySummary;
import com.team23.trojanrideshare.domain.util.Money;
import com.team23.trojanrideshare.ui.common.BaseFragment;
import com.team23.trojanrideshare.ui.common.RideViews;
import com.team23.trojanrideshare.ui.ride.RideDetailFragment;

import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** FR-5.6 – FR-5.10: Monthly Ride Summary for a chosen month and year. */
public class MonthlySummaryFragment extends BaseFragment {

    private FragmentMonthlySummaryBinding b;
    private HistoryViewModel vm;
    private final List<Integer> years = new ArrayList<>();
    private YearMonth selected;

    @Override
    protected String title() {
        return "Monthly Summary";
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentMonthlySummaryBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        vm = new ViewModelProvider(this).get(HistoryViewModel.class);
        observeCommon(vm, b.progress);
        vm.summary().observe(getViewLifecycleOwner(), this::show);

        YearMonth now = vm.currentMonth();
        if (selected == null) selected = now;
        List<String> months = new ArrayList<>();
        for (Month m : Month.values()) months.add(m.getDisplayName(TextStyle.FULL, Locale.US));
        years.clear();
        for (int y = now.getYear() - 4; y <= now.getYear() + 1; y++) years.add(y);
        List<String> yearLabels = new ArrayList<>();
        for (int y : years) yearLabels.add(String.valueOf(y));

        b.month.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, months));
        b.year.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, yearLabels));
        b.month.setText(months.get(selected.getMonthValue() - 1), false);
        b.year.setText(String.valueOf(selected.getYear()), false);
        b.month.setOnItemClickListener((p, v, pos, id) -> {
            selected = selected.withMonth(pos + 1);
            vm.loadSummary(selected);
        });
        b.year.setOnItemClickListener((p, v, pos, id) -> {
            selected = selected.withYear(years.get(pos));
            vm.loadSummary(selected);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        vm.loadSummary(selected);
    }

    private void show(MonthlySummary s) {
        if (s == null) return;
        boolean any = s.hasActivity();
        b.noActivity.setVisibility(any ? View.GONE : View.VISIBLE);
        b.stats.setVisibility(any ? View.VISIBLE : View.GONE);
        b.rides.removeAllViews();
        if (!any) return;
        b.completed.setText(String.valueOf(s.getCompletedRides()));
        b.total.setText(Money.format(s.getTotalCostCents()));
        b.organized.setText(String.valueOf(s.getOrganizedCount()));
        b.joined.setText(String.valueOf(s.getJoinedCount()));
        for (HistoryEntry e : s.getRides()) {
            ItemRideBinding ib = ItemRideBinding.inflate(getLayoutInflater(), b.rides, false);
            RideViews.bind(ib, e.getRide());
            if (e.getMyShare() != null) {
                RideViews.extra(ib, "Your share: " + Money.format(e.getMyShare().getAmountCents())
                        + " · " + e.getMyShare().getStatus().label());
            }
            ib.card.setOnClickListener(v -> push(RideDetailFragment.newInstance(e.getRide().getRideId())));
            b.rides.addView(ib.getRoot());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
