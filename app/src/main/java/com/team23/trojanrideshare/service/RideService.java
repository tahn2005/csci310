package com.team23.trojanrideshare.service;

import com.team23.trojanrideshare.data.RideRepository;
import com.team23.trojanrideshare.domain.ActivityStatus;
import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.Callback;
import com.team23.trojanrideshare.domain.CostShare;
import com.team23.trojanrideshare.domain.Place;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.domain.RideMember;
import com.team23.trojanrideshare.domain.util.Clock;
import com.team23.trojanrideshare.domain.util.GeoUtils;
import com.team23.trojanrideshare.gateway.LocationGateway;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** FR-2: create a ride post, edit it (On-Time/Delayed), and cancel it. */
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
        checkTitle(r, errors);
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
        checkDetails(r, errors);
        return errors;
    }

    private static void checkTitle(Ride r, Map<String, String> errors) {
        String title = r.getTitle() == null ? "" : r.getTitle().trim();
        if (title.isEmpty()) {
            errors.put(F_TITLE, "Enter a title.");
        } else if (title.length() > MAX_TITLE_LENGTH) {
            errors.put(F_TITLE, "Title must be " + MAX_TITLE_LENGTH + " characters or fewer.");
        }
    }

    /** Departure, seats, cost and note: the fields checked both when posting and when editing. */
    private void checkDetails(Ride r, Map<String, String> errors) {
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

    /** FR-2.5: one message per invalid field of the edit form; empty if it can be saved. */
    public Map<String, String> validateEdit(Ride changes) {
        Map<String, String> errors = new LinkedHashMap<>();
        checkTitle(changes, errors);
        checkDetails(changes, errors);
        return errors;
    }

    /**
     * FR-2.5: the organizer edits the title, departure, maximum riders, expected cost and note,
     * and marks the ride On-Time or Delayed. The route stays fixed because riders joined for it,
     * and the maximum can't drop below the riders already in the ride.
     */
    public void editRide(String rideId, Ride changes, ActivityStatus s, Callback<Ride> cb) {
        if (s != ActivityStatus.ON_TIME && s != ActivityStatus.DELAYED) {
            cb.onError(AppException.validation("Choose On-Time or Delayed."));
            return;
        }
        Map<String, String> errors = validateEdit(changes);
        if (!errors.isEmpty()) {
            cb.onError(AppException.validation(errors));
            return;
        }
        String uid = session.uid();
        rides.runTransaction(rideId, ctx -> {
            Ride r = ctx.getRide();
            String reason = editBlockReason(r, uid);
            if (reason != null) throw AppException.notAllowed(reason);
            if (changes.getMaxRiders() < r.riderCount()) {
                Map<String, String> f = new LinkedHashMap<>();
                f.put(F_MAX_RIDERS, "This ride already has " + r.riderCount()
                        + " riders, so the maximum can't be lower than that.");
                throw AppException.validation(f);
            }
            r.setTitle(changes.getTitle().trim());
            r.setDeparture(changes.getDeparture().withSecond(0).withNano(0));
            r.setMaxRiders(changes.getMaxRiders());
            r.setExpectedCostCents(changes.getExpectedCostCents());
            r.setNote(changes.getNote().trim());
            r.setActivityStatus(s);
            r.updateCapacity();
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
            r.updateCapacity();
            ctx.rejectPending();
            ctx.putRide(r);
            return r;
        }, cb);
    }

    /**
     * FR-1.7: after a nickname change, shows the new name on every ride the user is part of
     * (as organizer, rider, and on their cost share). Rides that already match are skipped.
     */
    public void syncDisplayName(Callback<Void> cb) {
        String uid = session.uid();
        String name = session.displayName();
        if (uid == null || name == null || name.isEmpty()) {
            cb.onError(AppException.notAllowed("Please log in again."));
            return;
        }
        rides.getRidesOf(uid, new Callback<List<Ride>>() {
            @Override
            public void onSuccess(List<Ride> mine) {
                List<Ride> stale = new ArrayList<>();
                for (Ride r : mine) {
                    if (renameIn(r, uid, name)) stale.add(r);
                }
                Gather<Void> g = new Gather<>(stale.size(), new Callback<List<Void>>() {
                    @Override
                    public void onSuccess(List<Void> done) {
                        cb.onSuccess(null);
                    }

                    @Override
                    public void onError(AppException e) {
                        cb.onError(e);
                    }
                });
                for (Ride r : stale) {
                    rides.runTransaction(r.getRideId(), ctx -> {
                        Ride fresh = ctx.getRide();
                        if (renameIn(fresh, uid, name)) ctx.putRide(fresh);
                        return null;
                    }, g.part());
                }
            }

            @Override
            public void onError(AppException e) {
                cb.onError(e);
            }
        });
    }

    /** Sets {@code uid}'s name everywhere it appears on the ride; true if anything changed. */
    static boolean renameIn(Ride r, String uid, String name) {
        boolean changed = false;
        if (r.isOrganizer(uid) && !name.equals(r.getOrganizerName())) {
            r.setOrganizerName(name);
            changed = true;
        }
        RideMember m = r.member(uid);
        if (m != null && !name.equals(m.getDisplayName())) {
            m.setDisplayName(name);
            changed = true;
        }
        CostShare s = r.shareOf(uid);
        if (s != null && !name.equals(s.getDisplayName())) {
            s.setDisplayName(name);
            changed = true;
        }
        return changed;
    }

    private static String editBlockReason(Ride r, String uid) {
        if (!r.isOrganizer(uid)) return "Only the ride organizer can change this ride.";
        if (!r.isActive()) return "This ride is " + r.getActivityStatus().label().toLowerCase() + " and can't be changed.";
        return null;
    }
}
