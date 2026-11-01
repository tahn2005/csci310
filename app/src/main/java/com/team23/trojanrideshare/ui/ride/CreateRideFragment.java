package com.team23.trojanrideshare.ui.ride;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.team23.trojanrideshare.R;
import com.team23.trojanrideshare.databinding.FragmentCreateRideBinding;
import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.domain.util.Money;
import com.team23.trojanrideshare.service.RideService;
import com.team23.trojanrideshare.ui.common.BaseFragment;
import com.team23.trojanrideshare.ui.common.PlaceField;
import com.team23.trojanrideshare.ui.common.UiFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

/** FR-2.1 Create Ride form; origin and destination come from location suggestions. */
public class CreateRideFragment extends BaseFragment {

    private FragmentCreateRideBinding b;
    private CreateRideViewModel vm;
    private PlaceField origin;
    private PlaceField destination;
    private LocalDate date;
    private LocalTime time;

    @Override
    protected String title() {
        return "Create Ride";
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentCreateRideBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        vm = new ViewModelProvider(this).get(CreateRideViewModel.class);
        observeCommon(vm, b.progress);
        vm.busy().observe(getViewLifecycleOwner(), busy -> b.post.setEnabled(!Boolean.TRUE.equals(busy)));
        vm.posted().observe(getViewLifecycleOwner(), ev -> {
            Ride r = ev == null ? null : ev.take();
            if (r == null) return;
            toast("Ride posted!");
            main().replaceTop(RideDetailFragment.newInstance(r.getRideId()));
        });

        origin = new PlaceField(b.originLayout, b.origin, vm);
        destination = new PlaceField(b.destinationLayout, b.destination, vm);
        b.date.setOnClickListener(v -> pickDate());
        b.dateLayout.setStartIconOnClickListener(v -> pickDate());
        b.time.setOnClickListener(v -> pickTime());
        b.timeLayout.setStartIconOnClickListener(v -> pickTime());
        b.post.setOnClickListener(v -> submit());
    }

    private void pickDate() {
        LocalDate d = date != null ? date : app().clock.now().toLocalDate();
        new DatePickerDialog(requireContext(), (p, y, m, day) -> {
            date = LocalDate.of(y, m + 1, day);
            b.date.setText(UiFormat.date(date));
            b.dateLayout.setError(null);
        }, d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth()).show();
    }

    private void pickTime() {
        LocalTime t = time != null ? time : app().clock.now().toLocalTime().plusHours(1).withMinute(0);
        new TimePickerDialog(requireContext(), (p, h, m) -> {
            time = LocalTime.of(h, m);
            b.time.setText(UiFormat.time(time));
            b.timeLayout.setError(null);
        }, t.getHour(), t.getMinute(), false).show();
    }

    private int maxRiders() {
        int id = b.maxRiders.getCheckedButtonId();
        if (id == R.id.riders2) return 2;
        if (id == R.id.riders3) return 3;
        if (id == R.id.riders5) return 5;
        if (id == R.id.riders6) return 6;
        if (id == R.id.riders4) return 4;
        return 0;
    }

    private void submit() {
        hideKeyboard();
        clearErrors();
        Ride r = new Ride();
        r.setTitle(String.valueOf(b.title.getText()));
        r.setOrigin(origin.getSelected());
        r.setDestination(destination.getSelected());
        r.setDeparture(date == null || time == null ? null : LocalDateTime.of(date, time));
        r.setMaxRiders(maxRiders());
        r.setNote(String.valueOf(b.note.getText()));
        String costError = null;
        try {
            r.setExpectedCostCents(Money.parse(String.valueOf(b.cost.getText())));
        } catch (AppException e) {
            costError = e.getMessage();
        }
        vm.post(r, costError);
    }

    private void clearErrors() {
        b.titleLayout.setError(null);
        origin.setError(null);
        destination.setError(null);
        b.dateLayout.setError(null);
        b.timeLayout.setError(null);
        b.costLayout.setError(null);
        b.noteLayout.setError(null);
        b.maxRidersError.setVisibility(View.GONE);
    }

    @Override
    protected boolean onFieldErrors(Map<String, String> errors) {
        b.titleLayout.setError(errors.get(RideService.F_TITLE));
        origin.setError(errors.get(RideService.F_ORIGIN));
        destination.setError(errors.get(RideService.F_DESTINATION));
        String dep = errors.get(RideService.F_DEPARTURE);
        if (dep != null) {
            b.dateLayout.setError(date == null ? "Choose a date" : " ");
            b.timeLayout.setError(time == null ? "Choose a time" : " ");
            if (date != null && time != null) b.dateLayout.setError(dep);
        }
        String max = errors.get(RideService.F_MAX_RIDERS);
        b.maxRidersError.setVisibility(max == null ? View.GONE : View.VISIBLE);
        b.maxRidersError.setText(max);
        b.costLayout.setError(errors.get(RideService.F_COST));
        b.noteLayout.setError(errors.get(RideService.F_NOTE));
        toast("Please fix the highlighted fields.");
        return true;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
