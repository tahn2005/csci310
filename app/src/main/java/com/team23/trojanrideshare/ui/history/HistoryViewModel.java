package com.team23.trojanrideshare.ui.history;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.team23.trojanrideshare.domain.Callback;
import com.team23.trojanrideshare.domain.HistoryEntry;
import com.team23.trojanrideshare.domain.HistoryFilter;
import com.team23.trojanrideshare.domain.MonthlySummary;
import com.team23.trojanrideshare.domain.Place;
import com.team23.trojanrideshare.domain.RideMember;
import com.team23.trojanrideshare.service.RideHistoryService;
import com.team23.trojanrideshare.ui.common.BaseViewModel;
import com.team23.trojanrideshare.ui.common.PlaceField;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/** SD-5 Ride History and Monthly Summary (FR-5). */
public class HistoryViewModel extends BaseViewModel implements PlaceField.Source {

    private final MutableLiveData<List<HistoryEntry>> entries = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<RideMember>> participants = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<MonthlySummary> summary = new MutableLiveData<>();
    private HistoryFilter filter = new HistoryFilter();

    public HistoryViewModel(@NonNull Application app) {
        super(app);
    }

    public LiveData<List<HistoryEntry>> entries() {
        return entries;
    }

    /** Everyone the user has ridden with (FR-5.4.2). */
    public LiveData<List<RideMember>> participants() {
        return participants;
    }

    public LiveData<MonthlySummary> summary() {
        return summary;
    }

    public HistoryFilter filter() {
        return filter;
    }

    public String uid() {
        return app().session.uid();
    }

    /** FR-5.1 – FR-5.5. */
    public void loadHistory(HistoryFilter f) {
        filter = f;
        app().history.getHistory(new HistoryFilter(), call(all -> {
            participants.setValue(RideHistoryService.participantsOf(all, uid()));
            app().history.getHistory(f, call(entries::setValue));
        }));
    }

    public void reload() {
        loadHistory(filter);
    }

    /** FR-5.6 – FR-5.9. */
    public void loadSummary(YearMonth m) {
        app().history.getMonthlySummary(m, call(summary::setValue));
    }

    public YearMonth currentMonth() {
        return app().history.currentMonth();
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
