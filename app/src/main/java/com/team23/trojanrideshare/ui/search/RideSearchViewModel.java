package com.team23.trojanrideshare.ui.search;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.team23.trojanrideshare.domain.Callback;
import com.team23.trojanrideshare.domain.Place;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.domain.SearchCriteria;
import com.team23.trojanrideshare.ui.common.BaseViewModel;
import com.team23.trojanrideshare.ui.common.PlaceField;

import java.util.ArrayList;
import java.util.List;

/** Ride Search page state (FR-3.1 – FR-3.4). */
public class RideSearchViewModel extends BaseViewModel implements PlaceField.Source {

    public static final String NO_MATCH = "No rides match your search. Try widening your radius or filters.";

    private final MutableLiveData<SearchCriteria> criteria = new MutableLiveData<>(new SearchCriteria());
    private final MutableLiveData<List<Ride>> results = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> empty = new MutableLiveData<>();

    public RideSearchViewModel(@NonNull Application app) {
        super(app);
    }

    public LiveData<SearchCriteria> criteria() {
        return criteria;
    }

    public LiveData<List<Ride>> results() {
        return results;
    }

    /** Message to show instead of results, or null. */
    public LiveData<String> empty() {
        return empty;
    }

    /** Loads fresh rides from the central database and re-applies the current criteria. */
    public void open() {
        app().search.refresh(call(v -> apply()));
    }

    public void setOrigin(Place p) {
        SearchCriteria c = current();
        c.setOrigin(p);
        criteria.setValue(c);
    }

    public void setDestination(Place p) {
        SearchCriteria c = current();
        c.setDestination(p);
        criteria.setValue(c);
    }

    public void applyFilters(SearchCriteria c) {
        criteria.setValue(c.copy());
        apply();
    }

    public void clearFilters() {
        criteria.setValue(new SearchCriteria());
        apply();
    }

    /** A copy of the current criteria to edit. */
    public SearchCriteria current() {
        SearchCriteria c = criteria.getValue();
        return c == null ? new SearchCriteria() : c.copy();
    }

    private void apply() {
        List<Ride> list = app().search.search(current());
        results.setValue(list);
        empty.setValue(list.isEmpty() ? NO_MATCH : null);
    }

    public boolean isJoined(Ride r) {
        return app().search.isJoined(r);
    }

    public boolean hasPendingRequest(Ride r) {
        return r.hasPendingRequestFrom(app().session.uid());
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
