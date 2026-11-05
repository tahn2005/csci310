package com.team23.trojanrideshare.service;

import com.team23.trojanrideshare.data.RideRepository;
import com.team23.trojanrideshare.domain.ActivityStatus;
import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.Callback;
import com.team23.trojanrideshare.domain.CostShare;
import com.team23.trojanrideshare.domain.HistoryEntry;
import com.team23.trojanrideshare.domain.HistoryFilter;
import com.team23.trojanrideshare.domain.MonthlySummary;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.domain.RideMember;
import com.team23.trojanrideshare.domain.User;
import com.team23.trojanrideshare.domain.util.Clock;
import com.team23.trojanrideshare.domain.util.GeoUtils;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FR-5 and FR-1.9: the user's ride history (Completed and Cancelled rides), upcoming rides,
 * public rides, and the monthly summary (Completed rides only).
 */
public class RideHistoryService {

    private final RideRepository rides;
    private final Clock clock;
    private final Session session;

    public RideHistoryService(RideRepository rides, Clock clock, Session session) {
        this.rides = rides;
        this.clock = clock;
        this.session = session;
    }

    /** FR-5.1 – FR-5.5. */
    public void getHistory(HistoryFilter f, Callback<List<HistoryEntry>> cb) {
        String uid = session.uid();
        rides.getRidesOf(uid, new Callback<List<Ride>>() {
            @Override
            public void onSuccess(List<Ride> result) {
                cb.onSuccess(filter(result, f, uid));
            }

            @Override
            public void onError(AppException e) {
                cb.onError(e);
            }
        });
    }

    /** Filtering step of {@link #getHistory}: no I/O, just the history rules. */
    static List<HistoryEntry> filter(List<Ride> all, HistoryFilter f, String uid) {
        List<HistoryEntry> out = new ArrayList<>();
        for (Ride r : all) {
            ActivityStatus s = r.getActivityStatus();
            if (s != ActivityStatus.COMPLETED && s != ActivityStatus.CANCELLED) continue;
            if (f.getStatus() != null && s != f.getStatus()) continue;
            if (f.getParticipantId() != null
                    && (f.getParticipantId().equals(uid) || !r.hasMember(f.getParticipantId()))) continue;
            if (f.getOrigin() != null
                    && GeoUtils.distanceMiles(r.getOrigin(), f.getOrigin()) > f.getRadiusMiles()) continue;
            if (f.getDestination() != null
                    && GeoUtils.distanceMiles(r.getDestination(), f.getDestination()) > f.getRadiusMiles()) continue;
            out.add(new HistoryEntry(r, s == ActivityStatus.COMPLETED ? r.shareOf(uid) : null));
        }
        Comparator<HistoryEntry> byDate = Comparator.comparing(e -> e.getRide().getDeparture());
        out.sort(f.isNewestFirst() ? byDate.reversed() : byDate);
        return out;
    }

    /** Everyone else who rode with the user, for the participant filter (FR-5.4.2). */
    public static List<RideMember> participantsOf(List<HistoryEntry> entries, String uid) {
        Map<String, RideMember> byId = new LinkedHashMap<>();
        for (HistoryEntry e : entries) {
            for (RideMember m : e.getRide().getMembers()) {
                if (!m.getUserId().equals(uid)) byId.putIfAbsent(m.getUserId(), m);
            }
        }
        List<RideMember> out = new ArrayList<>(byId.values());
        out.sort(Comparator.comparing(m -> m.getDisplayName() == null ? "" : m.getDisplayName().toLowerCase()));
        return out;
    }

    /** FR-1.9: On-Time and Delayed rides the user is in, soonest first. */
    public void getUpcoming(Callback<List<Ride>> cb) {
        rides.getRidesOf(session.uid(), new Callback<List<Ride>>() {
            @Override
            public void onSuccess(List<Ride> result) {
                List<Ride> out = new ArrayList<>();
                for (Ride r : result) {
                    if (r.isActive()) out.add(r);
                }
                out.sort(Comparator.comparing(Ride::getDeparture));
                cb.onSuccess(out);
            }

            @Override
            public void onError(AppException e) {
                cb.onError(e);
            }
        });
    }

    /** FR-1.10: the rides a user chose to show on their public profile that they are still in. */
    public void getPublicRides(User u, Callback<List<Ride>> cb) {
        rides.getRidesByIds(u.getPublicRideIds(), new Callback<List<Ride>>() {
            @Override
            public void onSuccess(List<Ride> result) {
                List<Ride> upcoming = new ArrayList<>();
                List<Ride> completed = new ArrayList<>();
                for (Ride r : result) {
                    if (!r.hasMember(u.getUid())) continue;
                    if (r.isActive()) upcoming.add(r);
                    else if (r.getActivityStatus() == ActivityStatus.COMPLETED) completed.add(r);
                }
                upcoming.sort(Comparator.comparing(Ride::getDeparture));
                completed.sort(Comparator.comparing(Ride::getDeparture).reversed());
                List<Ride> out = new ArrayList<>(upcoming);
                out.addAll(completed);
                cb.onSuccess(out);
            }

            @Override
            public void onError(AppException e) {
                cb.onError(e);
            }
        });
    }

    /** FR-5.7 – FR-5.9. */
    public void getMonthlySummary(YearMonth m, Callback<MonthlySummary> cb) {
        String uid = session.uid();
        rides.getRidesOf(uid, new Callback<List<Ride>>() {
            @Override
            public void onSuccess(List<Ride> result) {
                cb.onSuccess(summarize(result, m, uid));
            }

            @Override
            public void onError(AppException e) {
                cb.onError(e);
            }
        });
    }

    /** Computes the monthly summary from the user's rides (FR-5.7, FR-5.8). */
    static MonthlySummary summarize(List<Ride> all, YearMonth m, String uid) {
        int completed = 0;
        long total = 0;
        int organized = 0;
        int joined = 0;
        List<HistoryEntry> entries = new ArrayList<>();
        for (Ride r : all) {
            if (r.getActivityStatus() != ActivityStatus.COMPLETED) continue; // FR-5.8
            if (r.getDeparture() == null || !YearMonth.from(r.getDeparture()).equals(m)) continue;
            if (!r.hasMember(uid)) continue;
            completed++;
            CostShare mine = r.shareOf(uid);
            if (mine != null) total += mine.getAmountCents();
            if (r.isOrganizer(uid)) organized++;
            else joined++;
            entries.add(new HistoryEntry(r, mine));
        }
        entries.sort(Comparator.comparing(e -> e.getRide().getDeparture()));
        return new MonthlySummary(m, completed, total, organized, joined, entries);
    }

    /** The current month according to the app clock (default selection). */
    public YearMonth currentMonth() {
        return YearMonth.from(clock.now());
    }
}
