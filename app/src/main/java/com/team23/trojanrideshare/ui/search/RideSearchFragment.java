package com.team23.trojanrideshare.ui.search;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.team23.trojanrideshare.databinding.FragmentSearchBinding;
import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.Callback;
import com.team23.trojanrideshare.domain.Place;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.domain.SearchCriteria;
import com.team23.trojanrideshare.domain.SortOption;
import com.team23.trojanrideshare.ui.common.BaseFragment;
import com.team23.trojanrideshare.ui.common.PlaceField;
import com.team23.trojanrideshare.ui.common.RideAdapter;
import com.team23.trojanrideshare.ui.common.RideViews;
import com.team23.trojanrideshare.ui.common.UiFormat;
import com.team23.trojanrideshare.ui.ride.CreateRideFragment;
import com.team23.trojanrideshare.ui.ride.RideDetailFragment;

import java.time.LocalDate;
import java.time.LocalTime;

/** FR-3.1 – FR-3.5: search, filter, and sort open rides posted by other students. */
public class RideSearchFragment extends BaseFragment {

    private static final double[] WINDOWS = {0.5, 1, 1.5, 2, 3, 4, 5, 6};

    private FragmentSearchBinding b;
    private RideSearchViewModel vm;
    private PlaceField origin;
    private PlaceField destination;
    private RideAdapter<Ride> adapter;

    @Override
    protected String title() {
        return "Find a Ride";
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentSearchBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        vm = new ViewModelProvider(this).get(RideSearchViewModel.class);
        observeCommon(vm, b.progress);

        origin = new PlaceField(b.originLayout, b.origin, vm);
        destination = new PlaceField(b.destinationLayout, b.destination, vm);
        SearchCriteria c0 = vm.current();
        if (c0.getOrigin() != null) origin.setSelected(c0.getOrigin());
        if (c0.getDestination() != null) destination.setSelected(c0.getDestination());

        adapter = RideAdapter.forRides((ib, r) -> {
            if (vm.isJoined(r)) RideViews.tag(ib, "Joined", true);
            else if (vm.hasPendingRequest(r)) RideViews.tag(ib, "Requested", false);
        }, r -> push(RideDetailFragment.newInstance(r.getRideId())));
        b.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.list.setAdapter(adapter);

        vm.results().observe(getViewLifecycleOwner(), list -> adapter.submit(list));
        vm.empty().observe(getViewLifecycleOwner(), msg -> {
            b.empty.setVisibility(msg == null ? View.GONE : View.VISIBLE);
            b.empty.setText(msg);
        });
        vm.criteria().observe(getViewLifecycleOwner(), this::showCriteria);
        vm.busy().observe(getViewLifecycleOwner(), busy -> {
            if (!Boolean.TRUE.equals(busy)) b.refresh.setRefreshing(false);
        });

        b.radius.addOnChangeListener((s, value, fromUser) -> b.radiusLabel.setText("Radius: " + UiFormat.miles(value)));
        b.chipDate.setOnClickListener(v -> pickDate());
        b.chipDate.setOnCloseIconClickListener(v -> {
            SearchCriteria c = vm.current();
            c.setDate(null);
            vm.applyFilters(c);
        });
        b.chipTime.setOnClickListener(v -> pickTime());
        b.chipTime.setOnCloseIconClickListener(v -> {
            SearchCriteria c = vm.current();
            c.setTargetTime(null);
            vm.applyFilters(c);
        });
        b.chipSeats.setOnClickListener(v -> {
            SearchCriteria c = vm.current();
            c.setSeatsOnly(b.chipSeats.isChecked());
            vm.applyFilters(c);
        });
        b.chipSort.setOnClickListener(this::pickSort);
        b.searchButton.setOnClickListener(v -> runSearch());
        b.destination.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch();
                return true;
            }
            return false;
        });
        b.clearButton.setOnClickListener(v -> {
            origin.clear();
            destination.clear();
            b.radius.setValue((float) SearchCriteria.DEFAULT_RADIUS);
            vm.clearFilters();
        });
        b.refresh.setOnRefreshListener(() -> vm.open());
        b.fab.setOnClickListener(v -> push(new CreateRideFragment()));
    }

    @Override
    public void onResume() {
        super.onResume();
        vm.open();
    }

    private void showCriteria(SearchCriteria c) {
        if (Math.abs(b.radius.getValue() - c.getRadiusMiles()) > 1e-6) b.radius.setValue((float) c.getRadiusMiles());
        b.radiusLabel.setText("Radius: " + UiFormat.miles(c.getRadiusMiles()));
        b.chipDate.setText(c.getDate() == null ? "Any date" : UiFormat.shortDate(c.getDate()));
        b.chipDate.setCloseIconVisible(c.getDate() != null);
        b.chipTime.setText(c.getTargetTime() == null ? "Any time"
                : UiFormat.time(c.getTargetTime()) + " ± " + UiFormat.hours(c.getWindowHours()));
        b.chipTime.setCloseIconVisible(c.getTargetTime() != null);
        b.chipSeats.setChecked(c.isSeatsOnly());
        b.chipSort.setText("Sort: " + sortLabel(c.getSort()));
    }

    private static String sortLabel(SortOption s) {
        switch (s) {
            case TIME_LATEST:
                return "Latest";
            case PROXIMITY:
                return "Proximity";
            case PROXIMITY_THEN_TIME:
                return "Proximity + time";
            default:
                return "Earliest";
        }
    }

    /** FR-3.2: resolve typed keywords to places, then apply every filter together. */
    private void runSearch() {
        hideKeyboard();
        origin.resolveTyped(new Callback<Place>() {
            @Override
            public void onSuccess(Place o) {
                destination.resolveTyped(new Callback<Place>() {
                    @Override
                    public void onSuccess(Place d) {
                        SearchCriteria c = vm.current();
                        c.setOrigin(o);
                        c.setDestination(d);
                        c.setRadiusMiles(b.radius.getValue());
                        if (c.getSort().needsLocation() && !c.hasLocation()) c.setSort(SortOption.TIME_EARLIEST);
                        vm.applyFilters(c);
                    }

                    @Override
                    public void onError(AppException e) {
                        toast(e.getMessage()); // FR-3.E1: results stay as they were
                    }
                });
            }

            @Override
            public void onError(AppException e) {
                toast(e.getMessage());
            }
        });
    }

    private void pickDate() {
        SearchCriteria c = vm.current();
        LocalDate d = c.getDate() != null ? c.getDate() : app().clock.now().toLocalDate();
        DatePickerDialog dlg = new DatePickerDialog(requireContext(), (picker, y, m, day) -> {
            SearchCriteria next = vm.current();
            next.setDate(LocalDate.of(y, m + 1, day));
            vm.applyFilters(next);
        }, d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth());
        dlg.show();
    }

    private void pickTime() {
        SearchCriteria c = vm.current();
        LocalTime t = c.getTargetTime() != null ? c.getTargetTime() : LocalTime.of(15, 0);
        new TimePickerDialog(requireContext(), (picker, h, m) -> {
            String[] labels = new String[WINDOWS.length];
            for (int i = 0; i < WINDOWS.length; i++) labels[i] = "± " + UiFormat.hours(WINDOWS[i]);
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Departing within " + UiFormat.time(LocalTime.of(h, m)) + " …")
                    .setItems(labels, (d, which) -> {
                        SearchCriteria next = vm.current();
                        next.setTargetTime(LocalTime.of(h, m));
                        next.setWindowHours(WINDOWS[which]);
                        vm.applyFilters(next);
                    })
                    .show();
        }, t.getHour(), t.getMinute(), false).show();
    }

    private void pickSort(View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        SortOption[] options = SortOption.values();
        for (int i = 0; i < options.length; i++) menu.getMenu().add(Menu.NONE, i, i, options[i].label());
        menu.setOnMenuItemClickListener(item -> {
            SortOption s = options[item.getItemId()];
            SearchCriteria c = vm.current();
            if (s.needsLocation() && !c.hasLocation()) {
                toast("Enter an origin or destination and tap Search to sort by proximity.");
                return true;
            }
            c.setSort(s);
            vm.applyFilters(c);
            return true;
        });
        menu.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
