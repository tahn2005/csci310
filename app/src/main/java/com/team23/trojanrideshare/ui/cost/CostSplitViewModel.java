package com.team23.trojanrideshare.ui.cost;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.CostShare;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.domain.util.Money;
import com.team23.trojanrideshare.service.SplitStrategy;
import com.team23.trojanrideshare.ui.common.BaseViewModel;
import com.team23.trojanrideshare.ui.common.Event;

import java.util.ArrayList;
import java.util.List;

/** SD-4 Complete a Ride and Split the Cost (FR-4.1 – FR-4.4). */
public class CostSplitViewModel extends BaseViewModel {

    private final MutableLiveData<Ride> ride = new MutableLiveData<>();
    private final MutableLiveData<List<CostShare>> preview = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> problem = new MutableLiveData<>();
    private final MutableLiveData<Event<Ride>> saved = new MutableLiveData<>();
    private long cents;

    public CostSplitViewModel(@NonNull Application app) {
        super(app);
    }

    public LiveData<Ride> ride() {
        return ride;
    }

    /** FR-4.3: each rider's share, shown before saving. */
    public LiveData<List<CostShare>> preview() {
        return preview;
    }

    /** Why the split can't be saved yet, or null. */
    public LiveData<String> problem() {
        return problem;
    }

    public LiveData<Event<Ride>> saved() {
        return saved;
    }

    public void load(String rideId) {
        app().rides.getRide(rideId, call(ride::setValue));
    }

    /** Recomputes the preview; {@code strategy} is null when an amount field is invalid. */
    public void updatePreview(String costText, SplitStrategy strategy, String inputProblem) {
        Ride r = ride.getValue();
        if (r == null) return;
        try {
            cents = Money.parse(costText);
            if (cents <= 0) throw AppException.validation("Enter an actual cost greater than $0.");
        } catch (AppException e) {
            cents = 0;
            preview.setValue(new ArrayList<>());
            problem.setValue(e.getMessage());
            return;
        }
        if (strategy == null) {
            preview.setValue(new ArrayList<>());
            problem.setValue(inputProblem);
            return;
        }
        try {
            preview.setValue(app().costs.preview(r, cents, strategy));
            problem.setValue(null);
        } catch (AppException e) {
            preview.setValue(new ArrayList<>());
            problem.setValue(e.getMessage());
        }
    }

    public long cents() {
        return cents;
    }

    public void save(SplitStrategy strategy) {
        Ride r = ride.getValue();
        if (r == null || strategy == null || problem.getValue() != null) return;
        app().costs.completeRide(r.getRideId(), cents, strategy, call(done -> saved.setValue(new Event<>(done))));
    }
}
