package com.team23.trojanrideshare.ui.cost;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.team23.trojanrideshare.R;
import com.team23.trojanrideshare.databinding.FragmentCostSplitBinding;
import com.team23.trojanrideshare.databinding.ItemSplitRowBinding;
import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.CostShare;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.domain.RideMember;
import com.team23.trojanrideshare.domain.util.Money;
import com.team23.trojanrideshare.service.CustomSplit;
import com.team23.trojanrideshare.service.EqualSplit;
import com.team23.trojanrideshare.service.SplitStrategy;
import com.team23.trojanrideshare.ui.common.BaseFragment;
import com.team23.trojanrideshare.ui.common.UiFormat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * FR-4.1 – FR-4.4: the organizer enters the actual cost, chooses Equal (and who pays any
 * leftover cents) or Custom amounts, sees every rider's share, then saves.
 */
public class CostSplitFragment extends BaseFragment {

    private static final String ARG_RIDE = "rideId";

    private FragmentCostSplitBinding b;
    private CostSplitViewModel vm;
    private final Map<String, ItemSplitRowBinding> rows = new LinkedHashMap<>();
    private final Set<String> extraIds = new LinkedHashSet<>();
    private int lastLeftover = -1;
    private boolean building;

    public static CostSplitFragment newInstance(String rideId) {
        CostSplitFragment f = new CostSplitFragment();
        Bundle args = new Bundle();
        args.putString(ARG_RIDE, rideId);
        f.setArguments(args);
        return f;
    }

    @Override
    protected String title() {
        return "Complete Ride";
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentCostSplitBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        vm = new ViewModelProvider(this).get(CostSplitViewModel.class);
        observeCommon(vm, b.progress);
        vm.ride().observe(getViewLifecycleOwner(), this::buildRows);
        vm.preview().observe(getViewLifecycleOwner(), this::showPreview);
        vm.problem().observe(getViewLifecycleOwner(), p -> {
            b.problem.setVisibility(p == null ? View.GONE : View.VISIBLE);
            b.problem.setText(p);
            b.save.setEnabled(p == null && vm.cents() > 0);
        });
        vm.busy().observe(getViewLifecycleOwner(), busy -> {
            if (Boolean.TRUE.equals(busy)) b.save.setEnabled(false);
        });
        vm.saved().observe(getViewLifecycleOwner(), ev -> {
            if (ev != null && ev.take() != null) {
                toast("Ride completed and cost split saved.");
                main().back();
            }
        });
        b.cost.addTextChangedListener(watcher(this::recompute));
        b.method.addOnButtonCheckedListener((g, id, checked) -> {
            if (checked) recompute();
        });
        b.save.setOnClickListener(v -> confirmSave());
        if (vm.ride().getValue() == null) vm.load(requireArguments().getString(ARG_RIDE));
    }

    private boolean isEqual() {
        return b.method.getCheckedButtonId() == R.id.equal;
    }

    private void buildRows(Ride r) {
        if (r == null) return;
        building = true;
        b.rideTitle.setText(r.getTitle());
        b.rideInfo.setText(UiFormat.route(r.getOrigin(), r.getDestination()) + "\n"
                + UiFormat.dateTime(r.getDeparture()) + " · " + r.riderCount() + " riders · expected "
                + Money.format(r.getExpectedCostCents()));
        if (String.valueOf(b.cost.getText()).isEmpty()) b.cost.setText(Money.plain(r.getExpectedCostCents()));
        b.rows.removeAllViews();
        rows.clear();
        for (RideMember m : r.getMembers()) {
            ItemSplitRowBinding row = ItemSplitRowBinding.inflate(getLayoutInflater(), b.rows, false);
            row.name.setText(m.getDisplayName() + (r.isOrganizer(m.getUserId()) ? " (organizer)" : ""));
            String id = m.getUserId();
            row.extraCent.setOnCheckedChangeListener((c, checked) -> {
                if (building) return;
                if (checked) extraIds.add(id);
                else extraIds.remove(id);
                recompute();
            });
            row.amount.addTextChangedListener(watcher(this::recompute));
            rows.put(m.getUserId(), row);
            b.rows.addView(row.getRoot());
        }
        building = false;
        recompute();
    }

    /** Builds the split strategy from the inputs and asks the ViewModel for a preview. */
    private void recompute() {
        if (building || vm.ride().getValue() == null) return;
        Ride r = vm.ride().getValue();
        boolean equal = isEqual();
        long cents;
        try {
            cents = Money.parse(String.valueOf(b.cost.getText()));
            b.costLayout.setError(null);
        } catch (AppException e) {
            cents = -1;
            b.costLayout.setError(e.getMessage());
        }
        int leftover = cents > 0 ? EqualSplit.leftoverCents(cents, r.riderCount()) : 0;

        SplitStrategy strategy;
        String inputProblem = null;
        if (equal) {
            b.hint.setText(leftover == 0
                    ? "Everyone pays the same amount."
                    : "The cost doesn't divide evenly. Choose " + leftover + (leftover == 1 ? " rider" : " riders")
                    + " to pay one extra cent.");
            if (leftover != lastLeftover) {
                // Default to the first riders whenever the number of leftover cents changes.
                extraIds.clear();
                int i = 0;
                for (String id : rows.keySet()) if (i++ < leftover) extraIds.add(id);
                lastLeftover = leftover;
            }
            building = true;
            for (Map.Entry<String, ItemSplitRowBinding> e : rows.entrySet()) {
                ItemSplitRowBinding row = e.getValue();
                row.amountLayout.setVisibility(View.GONE);
                row.extraCent.setVisibility(leftover > 0 ? View.VISIBLE : View.GONE);
                row.extraCent.setChecked(extraIds.contains(e.getKey()));
            }
            building = false;
            List<String> extra = new ArrayList<>(extraIds);
            strategy = new EqualSplit(extra);
        } else {
            b.hint.setText("Enter each rider's amount. They must add up exactly to the actual cost.");
            Map<String, Long> amounts = new HashMap<>();
            long sum = 0;
            boolean bad = false;
            for (Map.Entry<String, ItemSplitRowBinding> e : rows.entrySet()) {
                ItemSplitRowBinding row = e.getValue();
                row.extraCent.setVisibility(View.GONE);
                row.amountLayout.setVisibility(View.VISIBLE);
                String text = String.valueOf(row.amount.getText());
                try {
                    long v = Money.parse(text);
                    amounts.put(e.getKey(), v);
                    sum += v;
                    row.amountLayout.setError(null);
                } catch (AppException ex) {
                    bad = true;
                    row.amountLayout.setError(text.trim().isEmpty() ? null : " ");
                    if (inputProblem == null) {
                        inputProblem = text.trim().isEmpty() ? "Enter an amount for every rider." : ex.getMessage();
                    }
                }
            }
            strategy = bad ? null : new CustomSplit(amounts);
            b.total.setText("");
            if (!bad && cents > 0) {
                long diff = cents - sum;
                b.total.setText("Total " + Money.format(sum) + " of " + Money.format(cents)
                        + (diff == 0 ? "  ✓" : diff > 0 ? "  (" + Money.format(diff) + " left)" : "  (" + Money.format(-diff) + " over)"));
            }
        }
        vm.updatePreview(String.valueOf(b.cost.getText()), strategy, inputProblem);
    }

    private void showPreview(List<CostShare> shares) {
        Map<String, Long> byUser = new HashMap<>();
        long sum = 0;
        for (CostShare s : shares) {
            byUser.put(s.getUserId(), s.getAmountCents());
            sum += s.getAmountCents();
        }
        for (Map.Entry<String, ItemSplitRowBinding> e : rows.entrySet()) {
            Long v = byUser.get(e.getKey());
            e.getValue().share.setText(v == null ? "—" : Money.format(v));
        }
        if (!shares.isEmpty()) {
            b.total.setText("Total " + Money.format(sum) + "  ✓");
        } else if (isEqual()) {
            b.total.setText("");
        }
    }

    private void confirmSave() {
        List<CostShare> shares = vm.preview().getValue();
        if (shares == null || shares.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (CostShare s : shares) {
            sb.append(s.getDisplayName()).append(": ").append(Money.format(s.getAmountCents())).append('\n');
        }
        sb.append("\nThe ride will be marked Completed. Your own share is marked Paid automatically.");
        SplitStrategy strategy = currentStrategy();
        confirm("Save cost split?", sb.toString(), "Save", () -> vm.save(strategy));
    }

    private SplitStrategy currentStrategy() {
        if (isEqual()) return new EqualSplit(new ArrayList<>(extraIds));
        Map<String, Long> amounts = new HashMap<>();
        for (Map.Entry<String, ItemSplitRowBinding> e : rows.entrySet()) {
            try {
                amounts.put(e.getKey(), Money.parse(String.valueOf(e.getValue().amount.getText())));
            } catch (AppException ignored) {
                return null;
            }
        }
        return new CustomSplit(amounts);
    }

    private static TextWatcher watcher(Runnable r) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                r.run();
            }
        };
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        rows.clear();
        b = null;
    }
}
