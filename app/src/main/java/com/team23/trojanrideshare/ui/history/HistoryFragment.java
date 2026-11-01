package com.team23.trojanrideshare.ui.history;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.team23.trojanrideshare.R;
import com.team23.trojanrideshare.databinding.FragmentHistoryBinding;
import com.team23.trojanrideshare.databinding.ItemRideBinding;
import com.team23.trojanrideshare.domain.ActivityStatus;
import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.Callback;
import com.team23.trojanrideshare.domain.CostShare;
import com.team23.trojanrideshare.domain.HistoryEntry;
import com.team23.trojanrideshare.domain.HistoryFilter;
import com.team23.trojanrideshare.domain.Place;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.domain.RideMember;
import com.team23.trojanrideshare.domain.SearchCriteria;
import com.team23.trojanrideshare.domain.util.Money;
import com.team23.trojanrideshare.ui.common.BaseFragment;
import com.team23.trojanrideshare.ui.common.PlaceField;
import com.team23.trojanrideshare.ui.common.RideAdapter;
import com.team23.trojanrideshare.ui.common.RideViews;
import com.team23.trojanrideshare.ui.common.UiFormat;
import com.team23.trojanrideshare.ui.ride.RideDetailFragment;

import java.util.ArrayList;
import java.util.List;

/** FR-5.1 – FR-5.5: past (Completed and Cancelled) rides with search, filters, and sort. */
public class HistoryFragment extends BaseFragment {

    private FragmentHistoryBinding b;
    private HistoryViewModel vm;
    private PlaceField origin;
    private PlaceField destination;
    private RideAdapter<HistoryEntry> adapter;
    private final List<RideMember> participantOptions = new ArrayList<>();
    private String participantId;
    private boolean newestFirst = true;

    @Override
    protected String title() {
        return "Ride History";
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentHistoryBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        vm = new ViewModelProvider(this).get(HistoryViewModel.class);
        observeCommon(vm, b.progress);
        origin = new PlaceField(b.originLayout, b.origin, vm);
        destination = new PlaceField(b.destinationLayout, b.destination, vm);

        adapter = new RideAdapter<>(HistoryEntry::getRide, this::decorate,
                e -> push(RideDetailFragment.newInstance(e.getRide().getRideId())));
        b.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.list.setAdapter(adapter);

        vm.entries().observe(getViewLifecycleOwner(), list -> {
            adapter.submit(list);
            b.empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            HistoryFilter f = vm.filter();
            boolean filtered = f.hasLocation() || f.getStatus() != null || f.getParticipantId() != null;
            b.empty.setText(filtered
                    ? "No rides in your history match these filters. Try a wider radius or clear the filters."
                    : "No past rides yet. Completed and cancelled rides you took part in will show up here.");
        });
        vm.participants().observe(getViewLifecycleOwner(), this::showParticipants);
        vm.busy().observe(getViewLifecycleOwner(), busy -> {
            if (!Boolean.TRUE.equals(busy)) b.refresh.setRefreshing(false);
        });

        b.radius.addOnChangeListener((s, v, user) -> b.radiusLabel.setText("Radius: " + UiFormat.miles(v)));
        b.statusChips.setOnCheckedStateChangeListener((g, ids) -> apply());
        b.sortChip.setOnClickListener(v -> {
            newestFirst = !newestFirst;
            b.sortChip.setText(newestFirst ? "Newest first" : "Oldest first");
            apply();
        });
        b.participant.setOnItemClickListener((parent, v, position, id) -> {
            participantId = position == 0 ? null : participantOptions.get(position - 1).getUserId();
            apply();
        });
        b.searchButton.setOnClickListener(v -> apply());
        b.clearButton.setOnClickListener(v -> {
            origin.clear();
            destination.clear();
            b.radius.setValue((float) SearchCriteria.DEFAULT_RADIUS);
            b.statusChips.check(R.id.statusAll);
            participantId = null;
            b.participant.setText("Anyone", false);
            newestFirst = true;
            b.sortChip.setText("Newest first");
            apply();
        });
        b.summaryButton.setOnClickListener(v -> push(new MonthlySummaryFragment()));
        b.refresh.setOnRefreshListener(vm::reload);
    }

    @Override
    public void onResume() {
        super.onResume();
        vm.reload();
    }

    /** Gathers every search, filter, and sort choice; they apply together (FR-5.4.3). */
    private void apply() {
        hideKeyboard();
        origin.resolveTyped(new Callback<Place>() {
            @Override
            public void onSuccess(Place o) {
                destination.resolveTyped(new Callback<Place>() {
                    @Override
                    public void onSuccess(Place d) {
                        HistoryFilter f = new HistoryFilter();
                        f.setOrigin(o);
                        f.setDestination(d);
                        f.setRadiusMiles(b.radius.getValue());
                        int checked = b.statusChips.getCheckedChipId();
                        f.setStatus(checked == R.id.statusCompleted ? ActivityStatus.COMPLETED
                                : checked == R.id.statusCancelled ? ActivityStatus.CANCELLED : null);
                        f.setParticipantId(participantId);
                        f.setNewestFirst(newestFirst);
                        vm.loadHistory(f);
                    }

                    @Override
                    public void onError(AppException e) {
                        toast(e.getMessage());
                    }
                });
            }

            @Override
            public void onError(AppException e) {
                toast(e.getMessage());
            }
        });
    }

    private void showParticipants(List<RideMember> people) {
        participantOptions.clear();
        participantOptions.addAll(people);
        List<String> labels = new ArrayList<>();
        labels.add("Anyone");
        for (RideMember m : people) labels.add(m.getDisplayName());
        b.participant.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, labels));
    }

    /** FR-5.2: riders who took part, and my cost and whether it was paid. */
    private void decorate(ItemRideBinding ib, HistoryEntry e) {
        Ride r = e.getRide();
        ib.organizer.setText((r.isOrganizer(vm.uid()) ? "You organized" : "Organized by " + r.getOrganizerName())
                + " · " + r.riderCount() + (r.riderCount() == 1 ? " rider" : " riders") + " participated");
        CostShare s = e.getMyShare();
        if (s != null) {
            RideViews.extra(ib, "Your share: " + Money.format(s.getAmountCents()) + " · " + s.getStatus().label());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
