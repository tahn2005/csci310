package com.team23.trojanrideshare.service;

import com.team23.trojanrideshare.data.RideRepository;
import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.Callback;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.domain.SearchCriteria;
import com.team23.trojanrideshare.domain.SortOption;
import com.team23.trojanrideshare.domain.util.Clock;
import com.team23.trojanrideshare.domain.util.GeoUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * FR-3.1 – FR-3.4: fetches future rides once, then filters and sorts them on the device
 * (Firestore can't query a radius around two points at once).
 */
public class RideSearchEngine {

    private final RideRepository rides;
    private final Clock clock;
    private final Session session;
    private List<Ride> cache = new ArrayList<>();

    public RideSearchEngine(RideRepository rides, Clock clock, Session session) {
        this.rides = rides;
        this.clock = clock;
        this.session = session;
    }

    /** Reloads future rides from the central database. */
    public void refresh(Callback<Void> cb) {
        rides.getUpcomingRides(clock.now(), new Callback<List<Ride>>() {
            @Override
            public void onSuccess(List<Ride> result) {
                cache = new ArrayList<>(result);
                cb.onSuccess(null);
            }

            @Override
            public void onError(AppException e) {
                cb.onError(e);
            }
        });
    }

    /** Filters and sorts the cached rides. */
    public List<Ride> search(SearchCriteria c) {
        List<Ride> out = new ArrayList<>();
        for (Ride r : cache) {
            if (matches(r, c)) out.add(r);
        }
        out.sort(comparator(c));
        return out;
    }

    /** FR-3.1.4: rides the user has already joined are labeled "Joined". */
    public boolean isJoined(Ride r) {
        return r.hasMember(session.uid());
    }

    boolean matches(Ride r, SearchCriteria c) {
        LocalDateTime now = clock.now();
        // FR-3.1.1 – FR-3.1.3: eligible rides.
        if (r.isOrganizer(session.uid())) return false;
        if (!r.isActive()) return false;
        if (r.getDeparture() == null || !r.getDeparture().isAfter(now)) return false;
        if (r.isFull() && c.isSeatsOnly()) return false;
        // FR-3.2.3: origin and destination within the radius.
        if (c.getOrigin() != null
                && GeoUtils.distanceMiles(r.getOrigin(), c.getOrigin()) > c.getRadiusMiles()) return false;
        if (c.getDestination() != null
                && GeoUtils.distanceMiles(r.getDestination(), c.getDestination()) > c.getRadiusMiles()) return false;
        // FR-3.3.1: one calendar date.
        if (c.getDate() != null && !r.getDeparture().toLocalDate().equals(c.getDate())) return false;
        // FR-3.3.2: departure within the time window.
        if (c.getTargetTime() != null && minutesFromTarget(r, c) > c.getWindowHours() * 60 + 1e-9) return false;
        return true;
    }

    /** FR-3.4.2: distance from the desired origin plus distance from the desired destination. */
    double proximity(Ride r, SearchCriteria c) {
        double d = 0;
        if (c.getOrigin() != null) d += GeoUtils.distanceMiles(r.getOrigin(), c.getOrigin());
        if (c.getDestination() != null) d += GeoUtils.distanceMiles(r.getDestination(), c.getDestination());
        return d;
    }

    /**
     * Minutes between the ride's departure and the target time. With a date filter the
     * target is that date and time; otherwise the time of day is compared on any date.
     */
    static double minutesFromTarget(Ride r, SearchCriteria c) {
        if (c.getTargetTime() == null) return 0;
        if (c.getDate() != null) {
            LocalDateTime target = LocalDateTime.of(c.getDate(), c.getTargetTime());
            return Math.abs(Duration.between(target, r.getDeparture()).toMinutes());
        }
        int a = r.getDeparture().toLocalTime().toSecondOfDay() / 60;
        int b = c.getTargetTime().toSecondOfDay() / 60;
        int diff = Math.abs(a - b);
        return Math.min(diff, 24 * 60 - diff);
    }

    private Comparator<Ride> comparator(SearchCriteria c) {
        Comparator<Ride> byTime = Comparator.comparing(Ride::getDeparture);
        SortOption sort = c.getSort();
        if (sort.needsLocation() && !c.hasLocation()) sort = SortOption.TIME_EARLIEST;
        switch (sort) {
            case TIME_LATEST:
                return byTime.reversed();
            case PROXIMITY:
                return Comparator.comparingDouble((Ride r) -> proximity(r, c)).thenComparing(byTime);
            case PROXIMITY_THEN_TIME:
                // FR-3.4.3: proximity rounded to 0.1 mile, ties go to the closest time.
                Comparator<Ride> rounded = Comparator.comparingLong(r -> Math.round(proximity(r, c) * 10));
                if (c.getTargetTime() != null) {
                    return rounded.thenComparingDouble((Ride r) -> minutesFromTarget(r, c)).thenComparing(byTime);
                }
                return rounded.thenComparing(byTime);
            case TIME_EARLIEST:
            default:
                return byTime;
        }
    }
}
