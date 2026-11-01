package com.team23.trojanrideshare.ui.ride;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.Callback;
import com.team23.trojanrideshare.domain.Place;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.service.RideService;
import com.team23.trojanrideshare.ui.common.BaseViewModel;
import com.team23.trojanrideshare.ui.common.Event;
import com.team23.trojanrideshare.ui.common.PlaceField;

import java.util.List;
import java.util.Map;

/** SD-2 Create a Ride Post (FR-2.1 – FR-2.3). */
public class CreateRideViewModel extends BaseViewModel implements PlaceField.Source {

    private final MutableLiveData<Event<Ride>> posted = new MutableLiveData<>();

    public CreateRideViewModel(@NonNull Application app) {
        super(app);
    }

    public LiveData<Event<Ride>> posted() {
        return posted;
    }

    /** @param costError a parse error for the cost field, or null. */
    public void post(Ride r, String costError) {
        Map<String, String> errors = app().rides.validate(r);
        if (costError != null) errors.put(RideService.F_COST, costError);
        if (!errors.isEmpty()) {
            error.setValue(new Event<>(AppException.validation(errors)));
            return;
        }
        app().rides.createRide(r, call(created -> posted.setValue(new Event<>(created))));
    }

    @Override
    public void suggest(String query, Callback<List<Place>> cb) {
        app().rides.suggestPlaces(query, cb);
    }

    @Override
    public void resolve(Place suggestion, Callback<Place> cb) {
        app().rides.getPlace(suggestion, cb);
    }
}
