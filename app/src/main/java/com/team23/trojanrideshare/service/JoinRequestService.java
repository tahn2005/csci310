package com.team23.trojanrideshare.service;

import com.team23.trojanrideshare.data.JoinRequestRepository;
import com.team23.trojanrideshare.data.RideRepository;
import com.team23.trojanrideshare.domain.ActivityStatus;
import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.Callback;
import com.team23.trojanrideshare.domain.JoinRequest;
import com.team23.trojanrideshare.domain.RequestStatus;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.domain.RideMember;
import com.team23.trojanrideshare.domain.util.Clock;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * FR-3.5 – FR-3.11: join eligibility, sending/cancelling/answering requests, and leaving a
 * ride (3-hour rule and organizer handoff). Every change runs as one ride transaction.
 */
public class JoinRequestService {

    public static final String OWN_RIDE = "You can't join your own ride.";
    public static final String FULL = "This ride is full.";
    public static final String UNAVAILABLE = "This ride is no longer available.";
    public static final String ALREADY_RIDER = "You're already in this ride.";
    public static final String ALREADY_PENDING = "You already have a pending request for this ride.";
    public static final String DEPARTED = "This ride has already departed.";

    public static final String WITHDRAWN = "This request was withdrawn.";
    public static final String ANSWERED = "This request has already been answered.";
    public static final String FULL_ON_ACCEPT = "This ride is full. Reject the request or wait for a seat to open.";
    public static final String LEAVE_TOO_LATE = "You can't leave a ride less than 3 hours before departure.";

    public static final Duration LEAVE_CUTOFF = Duration.ofHours(3);

    /** Result of leaving a ride, so the screen can name the new organizer (FR-3.10.2). */
    public static class LeaveOutcome {
        public final boolean rideCancelled;
        public final String newOrganizerName;

        LeaveOutcome(boolean rideCancelled, String newOrganizerName) {
            this.rideCancelled = rideCancelled;
            this.newOrganizerName = newOrganizerName;
        }
    }

    private final RideRepository rides;
    private final JoinRequestRepository requests;
    private final Clock clock;
    private final Session session;

    public JoinRequestService(RideRepository rides, JoinRequestRepository requests, Clock clock, Session session) {
        this.rides = rides;
        this.requests = requests;
        this.clock = clock;
        this.session = session;
    }

    // ------------------------------------------------------------------ eligibility

    /** FR-3.E3: why the signed-in user can't request to join, or null if they can. */
    public String checkEligibility(Ride r) {
        return checkEligibility(r, session.uid(), clock.now());
    }

    static String checkEligibility(Ride r, String uid, LocalDateTime now) {
        if (r.isOrganizer(uid)) return OWN_RIDE;
        if (r.getActivityStatus() == ActivityStatus.COMPLETED
                || r.getActivityStatus() == ActivityStatus.CANCELLED) return UNAVAILABLE;
        if (r.hasDeparted(now)) return DEPARTED;
        if (r.hasMember(uid)) return ALREADY_RIDER;
        if (r.hasPendingRequestFrom(uid)) return ALREADY_PENDING;
        if (r.isFull()) return FULL;
        return null;
    }

    /** FR-3.10 / FR-3.E7: why the signed-in user can't leave the ride, or null if they can. */
    public String leaveBlockReason(Ride r) {
        return leaveBlockReason(r, session.uid(), clock.now());
    }

    public boolean canLeave(Ride r) {
        return leaveBlockReason(r) == null;
    }

    static String leaveBlockReason(Ride r, String uid, LocalDateTime now) {
        if (!r.hasMember(uid)) return "You're not in this ride.";
        if (!r.isActive()) return "This ride has ended, so it can't be left.";
        if (r.getDeparture() == null || Duration.between(now, r.getDeparture()).compareTo(LEAVE_CUTOFF) < 0) {
            return LEAVE_TOO_LATE;
        }
        return null;
    }

    // ------------------------------------------------------------------ requests

    /** SD-3 / FR-3.6: eligibility is checked again inside the transaction (FR-3.E4). */
    public void sendRequest(String rideId, String msg, Callback<JoinRequest> cb) {
        String message = msg == null ? "" : msg.trim();
        if (message.length() > JoinRequest.MAX_MESSAGE_LENGTH) {
            cb.onError(AppException.validation("Message must be " + JoinRequest.MAX_MESSAGE_LENGTH
                    + " characters or fewer."));
            return;
        }
        String uid = session.uid();
        String name = session.displayName();
        rides.runTransaction(rideId, ctx -> {
            Ride r = ctx.getRide();
            String reason = checkEligibility(r, uid, clock.now());
            if (reason != null) throw AppException.notAllowed(reason);
            JoinRequest q = new JoinRequest();
            q.setRequestId(ctx.newRequestId());
            q.setRideId(rideId);
            q.setRequesterId(uid);
            q.setRequesterName(name);
            q.setOrganizerId(r.getOrganizerId());
            q.setMessage(message);
            q.setCreatedAt(clock.instant());
            q.setStatus(RequestStatus.PENDING);
            r.getPendingRequests().put(uid, q.getRequestId());
            ctx.putRide(r);
            ctx.putRequest(q);
            return q;
        }, cb);
    }

    /** FR-3.9: a pending request can be cancelled before the ride departs; it is deleted. */
    public void cancelRequest(String rideId, String requestId, Callback<Void> cb) {
        String uid = session.uid();
        rides.runTransaction(rideId, ctx -> {
            Ride r = ctx.getRide();
            JoinRequest q = ctx.getRequest(requestId);
            if (q == null) throw AppException.conflict(WITHDRAWN);
            if (!uid.equals(q.getRequesterId())) throw AppException.notAllowed("You can only cancel your own requests.");
            if (q.getStatus() == RequestStatus.ACCEPTED) {
                throw AppException.notAllowed("This request was already accepted. Use Leave Ride instead.");
            }
            if (q.getStatus() != RequestStatus.PENDING) {
                throw AppException.notAllowed("Only pending requests can be cancelled.");
            }
            if (r.hasDeparted(clock.now())) throw AppException.notAllowed(DEPARTED);
            r.getPendingRequests().remove(uid);
            ctx.deleteRequest(requestId);
            ctx.putRide(r);
            return null;
        }, cb);
    }

    /** FR-3.8.1: the requester becomes a rider; the ride becomes Full at the maximum. */
    public void accept(JoinRequest request, Callback<Ride> cb) {
        String uid = session.uid();
        rides.runTransaction(request.getRideId(), ctx -> {
            Ride r = ctx.getRide();
            if (!r.isOrganizer(uid)) throw AppException.notAllowed("Only the ride organizer can answer requests.");
            JoinRequest q = ctx.getRequest(request.getRequestId());
            if (q == null) throw AppException.conflict(WITHDRAWN);
            if (q.getStatus() != RequestStatus.PENDING) throw AppException.conflict(ANSWERED);
            if (!r.isActive() || r.hasDeparted(clock.now())) throw AppException.notAllowed(UNAVAILABLE);
            if (r.isFull()) throw AppException.notAllowed(FULL_ON_ACCEPT);
            Instant now = clock.instant();
            if (!r.hasMember(q.getRequesterId())) {
                r.getMembers().add(new RideMember(q.getRequesterId(), q.getRequesterName(), now));
            }
            r.getPendingRequests().remove(q.getRequesterId());
            r.updateCapacity();
            q.setStatus(RequestStatus.ACCEPTED);
            q.setRespondedAt(now);
            ctx.putRide(r);
            ctx.putRequest(q);
            return r;
        }, cb);
    }

    /** FR-3.8.2: the request is Rejected and the ride does not change otherwise. */
    public void reject(JoinRequest request, Callback<Ride> cb) {
        String uid = session.uid();
        rides.runTransaction(request.getRideId(), ctx -> {
            Ride r = ctx.getRide();
            if (!r.isOrganizer(uid)) throw AppException.notAllowed("Only the ride organizer can answer requests.");
            JoinRequest q = ctx.getRequest(request.getRequestId());
            if (q == null) throw AppException.conflict(WITHDRAWN);
            if (q.getStatus() != RequestStatus.PENDING) throw AppException.conflict(ANSWERED);
            r.getPendingRequests().remove(q.getRequesterId());
            q.setStatus(RequestStatus.REJECTED);
            q.setRespondedAt(clock.instant());
            ctx.putRide(r);
            ctx.putRequest(q);
            return r;
        }, cb);
    }

    /** FR-3.7.2: requests the user has sent, newest first, each with its ride attached. */
    public void getMyRequests(Callback<List<JoinRequest>> cb) {
        requests.getRequestsBy(session.uid(), new Callback<List<JoinRequest>>() {
            @Override
            public void onSuccess(List<JoinRequest> list) {
                attachRides(list, cb);
            }

            @Override
            public void onError(AppException e) {
                cb.onError(e);
            }
        });
    }

    /** Pending requests for one ride, oldest first (organizer only). */
    public void getPending(String rideId, Callback<List<JoinRequest>> cb) {
        requests.getPending(rideId, session.uid(), new Callback<List<JoinRequest>>() {
            @Override
            public void onSuccess(List<JoinRequest> list) {
                List<JoinRequest> out = new ArrayList<>(list);
                out.sort(Comparator.comparing(JoinRequest::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())));
                cb.onSuccess(out);
            }

            @Override
            public void onError(AppException e) {
                cb.onError(e);
            }
        });
    }

    /** Pending requests for every active ride the user organizes, oldest first. */
    public void getIncomingRequests(Callback<List<JoinRequest>> cb) {
        String uid = session.uid();
        rides.getRidesOf(uid, new Callback<List<Ride>>() {
            @Override
            public void onSuccess(List<Ride> mine) {
                List<Ride> organizing = new ArrayList<>();
                for (Ride r : mine) {
                    if (r.isOrganizer(uid) && r.isActive() && r.pendingCount() > 0) organizing.add(r);
                }
                Gather<List<JoinRequest>> g = new Gather<>(organizing.size(), new Callback<List<List<JoinRequest>>>() {
                    @Override
                    public void onSuccess(List<List<JoinRequest>> parts) {
                        Map<String, Ride> byId = new HashMap<>();
                        for (Ride r : organizing) byId.put(r.getRideId(), r);
                        List<JoinRequest> all = new ArrayList<>();
                        for (List<JoinRequest> p : parts) {
                            for (JoinRequest q : p) {
                                q.setRide(byId.get(q.getRideId()));
                                all.add(q);
                            }
                        }
                        all.sort(Comparator.comparing(JoinRequest::getCreatedAt,
                                Comparator.nullsLast(Comparator.naturalOrder())));
                        cb.onSuccess(all);
                    }

                    @Override
                    public void onError(AppException e) {
                        cb.onError(e);
                    }
                });
                for (Ride r : organizing) requests.getPending(r.getRideId(), uid, g.part());
            }

            @Override
            public void onError(AppException e) {
                cb.onError(e);
            }
        });
    }

    /**
     * FR-1.7: after a nickname change, updates the name on the user's pending requests, so
     * the organizer sees it and an accepted rider joins under the new name.
     */
    public void syncDisplayName(Callback<Void> cb) {
        String uid = session.uid();
        String name = session.displayName();
        if (uid == null || name == null || name.isEmpty()) {
            cb.onError(AppException.notAllowed("Please log in again."));
            return;
        }
        requests.getRequestsBy(uid, new Callback<List<JoinRequest>>() {
            @Override
            public void onSuccess(List<JoinRequest> sent) {
                List<JoinRequest> stale = new ArrayList<>();
                for (JoinRequest q : sent) {
                    if (q.getStatus() == RequestStatus.PENDING && !name.equals(q.getRequesterName())) stale.add(q);
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
                for (JoinRequest stored : stale) {
                    rides.runTransaction(stored.getRideId(), ctx -> {
                        JoinRequest q = ctx.getRequest(stored.getRequestId());
                        if (q != null && q.getStatus() == RequestStatus.PENDING) {
                            q.setRequesterName(name);
                            ctx.putRequest(q);
                        }
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

    private void attachRides(List<JoinRequest> list, Callback<List<JoinRequest>> cb) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (JoinRequest q : list) unique.add(q.getRideId());
        List<String> ids = new ArrayList<>(unique);
        rides.getRidesByIds(ids, new Callback<List<Ride>>() {
            @Override
            public void onSuccess(List<Ride> found) {
                Map<String, Ride> byId = new HashMap<>();
                for (Ride r : found) byId.put(r.getRideId(), r);
                List<JoinRequest> out = new ArrayList<>();
                for (JoinRequest q : list) {
                    q.setRide(byId.get(q.getRideId()));
                    if (q.getRide() != null) out.add(q);
                }
                out.sort(Comparator.comparing(JoinRequest::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())));
                cb.onSuccess(out);
            }

            @Override
            public void onError(AppException e) {
                cb.onError(e);
            }
        });
    }

    // ------------------------------------------------------------------ leaving

    /** The rider who would become organizer if the signed-in organizer left now. */
    public RideMember successorIfLeaving(Ride r) {
        return r.isOrganizer(session.uid()) ? r.successorAfter(session.uid()) : null;
    }

    /**
     * FR-3.10: a rider leaves at least 3 hours before departure. An organizer hands the ride
     * to the most recently accepted rider, or cancels it if they are the only rider.
     */
    public void leaveRide(String rideId, Callback<LeaveOutcome> cb) {
        String uid = session.uid();
        rides.runTransaction(rideId, ctx -> {
            Ride r = ctx.getRide();
            String reason = leaveBlockReason(r, uid, clock.now());
            if (reason != null) throw AppException.notAllowed(reason);
            LeaveOutcome outcome;
            if (r.isOrganizer(uid)) {
                RideMember next = r.successorAfter(uid);
                if (next == null) {
                    // FR-3.10.3: the only rider leaves -> the ride is cancelled.
                    r.setActivityStatus(ActivityStatus.CANCELLED);
                    ctx.rejectPending();
                    outcome = new LeaveOutcome(true, null);
                } else {
                    r.getMembers().remove(r.member(uid));
                    r.setOrganizerId(next.getUserId());
                    r.setOrganizerName(next.getDisplayName());
                    ctx.reassignPending(next.getUserId());
                    outcome = new LeaveOutcome(false, next.getDisplayName());
                }
            } else {
                r.getMembers().remove(r.member(uid));
                outcome = new LeaveOutcome(false, null);
            }
            r.updateCapacity();
            ctx.putRide(r);
            return outcome;
        }, cb);
    }
}
