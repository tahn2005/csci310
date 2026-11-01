package com.team23.trojanrideshare.service;

import com.team23.trojanrideshare.data.RideRepository;
import com.team23.trojanrideshare.domain.ActivityStatus;
import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.Callback;
import com.team23.trojanrideshare.domain.Place;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.domain.RideMember;
import com.team23.trojanrideshare.domain.util.Clock;
import com.team23.trojanrideshare.domain.util.GeoUtils;
import com.team23.trojanrideshare.gateway.LocationGateway;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** FR-2: create a ride post, edit its departure (On-Time/Delayed), and cancel it. */
public class RideService {

    public static final String F_TITLE = "title";
    public static final String F_ORIGIN = "origin";
    public static final String F_DESTINATION = "destination";
    public static final String F_DEPARTURE = "departure";
    public static final String F_MAX_RIDERS = "maxRiders";
    public static final String F_COST = "expectedCost";
    public static final String F_NOTE = "note";

    public static final int MAX_TITLE_LENGTH = 60;
    public static final int MAX_NOTE_LENGTH = 300;
    /** Two places closer than this are treated as the same place. */
    static final double SAME_PLACE_MILES = 0.05;

    private final RideRepository rides;
    private final LocationGateway places;
    private final Clock clock;
    private final Session session;

    public RideService(RideRepository rides, LocationGateway places, Clock clock, Session session) {
        this.rides = rides;
        this.places = places;
        this.clock = clock;
        this.session = session;
    }

    /** Location keyword search (FR-2.2.2, FR-3.2.1, FR-5.3.1). */
    public void suggestPlaces(String query, Callback<List<Place>> cb) {
        places.suggest(query, cb);
    }

    /** Resolves a suggestion to a place with coordinates. */
    public void getPlace(Place suggestion, Callback<Place> cb) {
        if (suggestion.hasCoordinates()) {
            cb.onSuccess(suggestion);
        } else {
            places.getPlace(suggestion.getPlaceId(), cb);
        }
    }

    /** FR-2.2: one message per invalid field; empty if the ride can be posted. */
    public Map<String, String> validate(Ride r) {
        Map<String, String> errors = new LinkedHashMap<>();
        String title = r.getTitle() == null ? "" : r.getTitle().trim();
        if (title.isEmpty()) {
            errors.put(F_TITLE, "Enter a title.");
        } else if (title.length() > MAX_TITLE_LENGTH) {
            errors.put(F_TITLE, "Title must be " + MAX_TITLE_LENGTH + " characters or fewer.");
        }
        if (!isChosen(r.getOrigin())) {
            errors.put(F_ORIGIN, "Choose the origin from the location suggestions.");
        }
        if (!isChosen(r.getDestination())) {
            errors.put(F_DESTINATION, "Choose the destination from the location suggestions.");
        }
        if (isChosen(r.getOrigin()) && isChosen(r.getDestination())
                && (r.getOrigin().equals(r.getDestination())
                || GeoUtils.distanceMiles(r.getOrigin(), r.getDestination()) < SAME_PLACE_MILES)) {
            errors.put(F_DESTINATION, "The origin and destination must be different places.");
        }
        if (r.getDeparture() == null) {
            errors.put(F_DEPARTURE, "Choose the ride date and departure time.");
        } else if (!r.getDeparture().isAfter(clock.now())) {
            errors.put(F_DEPARTURE, "The ride date and departure time must be in the future.");
        }
        if (r.getMaxRiders() < Ride.MIN_RIDERS || r.getMaxRiders() > Ride.MAX_RIDERS) {
            errors.put(F_MAX_RIDERS, "Maximum riders must be a whole number from "
                    + Ride.MIN_RIDERS + " to " + Ride.MAX_RIDERS + ", including you.");
        }
        if (r.getExpectedCostCents() <= 0) {
            errors.put(F_COST, "Expected cost must be greater than $0.");
        }
        if (r.getNote().length() > MAX_NOTE_LENGTH) {
            errors.put(F_NOTE, "Note must be " + MAX_NOTE_LENGTH + " characters or fewer.");
        }
        return errors;
    }

    private static boolean isChosen(Place p) {
        return p != null && p.getPlaceId() != null && p.hasCoordinates();
    }

    /** FR-2.3: the organizer is the first rider; the ride starts On-Time and Open. */
    public void createRide(Ride form, Callback<Ride> cb) {
        if (!session.isSignedIn()) {
            cb.onError(AppException.notAllowed("Please log in again."));
            return;
        }
        Map<String, String> errors = validate(form);
        if (!errors.isEmpty()) {
            cb.onError(AppException.validation(errors));
            return;
        }
        Ride r = new Ride();
        r.setTitle(form.getTitle().trim());
        r.setOrigin(form.getOrigin());
        r.setDestination(form.getDestination());
        r.setDeparture(form.getDeparture().withSecond(0).withNano(0));
        r.setMaxRiders(form.getMaxRiders());
        r.setExpectedCostCents(form.getExpectedCostCents());
        r.setNote(form.getNote().trim());
        r.setOrganizerId(session.uid());
        r.setOrganizerName(session.displayName());
        List<RideMember> members = new ArrayList<>();
        members.add(new RideMember(session.uid(), session.displayName(), clock.instant()));
        r.setMembers(members);
        r.setActivityStatus(ActivityStatus.ON_TIME);
        r.updateCapacity();
        r.setCreatedAt(clock.instant());
        rides.createRide(r, cb);
    }

    public void getRide(String rideId, Callback<Ride> cb) {
        rides.getRide(rideId, cb);
    }

    /** Why the signed-in user can't edit or cancel the ride, or null if they can. */
    public String editBlockReason(Ride r) {
        return editBlockReason(r, session.uid());
    }

    /** FR-2.5.1: change only the departure time, marking the ride Delayed or back to On-Time. */
    public void updateDeparture(String rideId, LocalDateTime t, ActivityStatus s, Callback<Ride> cb) {
        if (s != ActivityStatus.ON_TIME && s != ActivityStatus.DELAYED) {
            cb.onError(AppException.validation("Choose On-Time or Delayed."));
            return;
        }
        if (t == null || !t.isAfter(clock.now())) {
            Map<String, String> f = new LinkedHashMap<>();
            f.put(F_DEPARTURE, "The new departure time must be in the future.");
            cb.onError(AppException.validation(f));
            return;
        }
        String uid = session.uid();
        rides.runTransaction(rideId, ctx -> {
            Ride r = ctx.getRide();
            String reason = editBlockReason(r, uid);
            if (reason != null) throw AppException.notAllowed(reason);
            r.setDeparture(t.withSecond(0).withNano(0));
            r.setActivityStatus(s);
            ctx.putRide(r);
            return r;
        }, cb);
    }

    /** FR-2.5.2: cancel at any time before Completed; pending requests are rejected (FR-3.11.2). */
    public void cancelRide(String rideId, Callback<Ride> cb) {
        String uid = session.uid();
        rides.runTransaction(rideId, ctx -> {
            Ride r = ctx.getRide();
            String reason = editBlockReason(r, uid);
            if (reason != null) throw AppException.notAllowed(reason);
            r.setActivityStatus(ActivityStatus.CANCELLED);
            ctx.rejectPending();
            ctx.putRide(r);
            return r;
        }, cb);
    }

    private static String editBlockReason(Ride r, String uid) {
        if (!r.isOrganizer(uid)) return "Only the ride organizer can change this ride.";
        if (!r.isActive()) return "This ride is " + r.getActivityStatus().label().toLowerCase() + " and can't be changed.";
        return null;
    }
}
