package com.team23.trojanrideshare.service;

import com.team23.trojanrideshare.data.RideRepository;
import com.team23.trojanrideshare.domain.ActivityStatus;
import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.Callback;
import com.team23.trojanrideshare.domain.CostShare;
import com.team23.trojanrideshare.domain.NonPaymentFlag;
import com.team23.trojanrideshare.domain.PaymentStatus;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.domain.util.Clock;

import java.util.ArrayList;
import java.util.List;

/**
 * FR-4: record the actual cost, split it (Strategy pattern), complete the ride, mark shares
 * Paid, and set or clear Non-Payment flags.
 */
public class CostSplitService {

    private final RideRepository rides;
    private final Clock clock;
    private final Session session;

    public CostSplitService(RideRepository rides, Clock clock, Session session) {
        this.rides = rides;
        this.clock = clock;
        this.session = session;
    }

    /** Why the signed-in user can't mark the ride Completed now, or null if they can. */
    public String completeBlockReason(Ride r) {
        return completeBlockReason(r, session.uid());
    }

    private String completeBlockReason(Ride r, String uid) {
        if (!r.isOrganizer(uid)) return "Only the ride organizer can mark the ride as completed.";
        if (r.getActivityStatus() == ActivityStatus.COMPLETED) return "This ride is already completed.";
        if (r.getActivityStatus() == ActivityStatus.CANCELLED) return "A cancelled ride can't be completed.";
        if (!r.hasDeparted(clock.now())) {
            return "You can mark the ride as completed after it departs (" + Formats.dateTime(r.getDeparture()) + ").";
        }
        return null;
    }

    /** FR-4.3: the shares the organizer sees before saving. */
    public List<CostShare> preview(Ride r, long cents, SplitStrategy s) throws AppException {
        if (cents <= 0) throw AppException.validation("Enter an actual cost greater than $0.");
        List<CostShare> shares = s.split(cents, r.getMembers());
        long sum = 0;
        for (CostShare c : shares) {
            c.setRideId(r.getRideId());
            sum += c.getAmountCents();
        }
        if (sum != cents) throw AppException.validation("The shares must add up to the actual cost.");
        return shares;
    }

    /**
     * SD-4 / FR-4.4: saves the shares and the Completed status together, or nothing.
     * The organizer's own share is marked Paid automatically (FR-4.6.1).
     */
    public void completeRide(String rideId, long cents, SplitStrategy s, Callback<Ride> cb) {
        String uid = session.uid();
        rides.runTransaction(rideId, ctx -> {
            Ride r = ctx.getRide();
            String reason = completeBlockReason(r, uid);
            if (reason != null) throw AppException.notAllowed(reason);
            List<CostShare> shares = preview(r, cents, s);
            for (CostShare c : shares) {
                if (r.isOrganizer(c.getUserId())) {
                    c.setStatus(PaymentStatus.PAID);
                    c.setPaidAt(clock.instant());
                }
            }
            r.setActualCostCents(cents);
            r.setSplitMethod(s.method());
            r.setActivityStatus(ActivityStatus.COMPLETED);
            r.setCompletedAt(clock.instant());
            r.updateCapacity();
            ctx.putShares(shares);
            ctx.rejectPending();
            ctx.putRide(r);
            return r;
        }, cb);
    }

    /** FR-4.6: a rider marks their own share Paid; any Non-Payment flag is cleared (FR-4.7.1). */
    public void markPaid(String rideId, Callback<Ride> cb) {
        String uid = session.uid();
        rides.runTransaction(rideId, ctx -> {
            Ride r = ctx.getRide();
            if (r.getActivityStatus() != ActivityStatus.COMPLETED) {
                throw AppException.notAllowed("Shares can be paid once the ride is completed.");
            }
            CostShare mine = r.shareOf(uid);
            if (mine == null) throw AppException.notAllowed("You don't have a share in this ride.");
            if (!mine.isPaid()) {
                List<CostShare> shares = new ArrayList<>();
                for (CostShare c : r.getShares()) {
                    CostShare copy = c.copy();
                    if (uid.equals(c.getUserId())) {
                        copy.setStatus(PaymentStatus.PAID);
                        copy.setPaidAt(clock.instant());
                    }
                    shares.add(copy);
                }
                ctx.putShares(shares);
                ctx.removeFlag(uid);
                ctx.putRide(r);
            }
            return r;
        }, cb);
    }

    /** FR-4.7: the organizer flags a rider's unpaid share (never their own, FR-4.E6). */
    public void flag(String rideId, String userId, Callback<NonPaymentFlag> cb) {
        String uid = session.uid();
        rides.runTransaction(rideId, ctx -> {
            Ride r = ctx.getRide();
            checkCanFlag(r, uid, userId);
            CostShare share = r.shareOf(userId);
            NonPaymentFlag f = new NonPaymentFlag(rideId, userId, uid);
            f.setRideTitle(r.getTitle());
            f.setUserName(share.getDisplayName());
            f.setOrganizerName(r.getOrganizerName());
            f.setAmountCents(share.getAmountCents());
            f.setCreatedAt(clock.instant());
            ctx.putFlag(f);
            return f;
        }, cb);
    }

    /** The organizer removes a flag they placed. */
    public void unflag(String rideId, String userId, Callback<Void> cb) {
        String uid = session.uid();
        rides.runTransaction(rideId, ctx -> {
            Ride r = ctx.getRide();
            if (!r.isOrganizer(uid)) throw AppException.notAllowed("Only the ride organizer can change flags.");
            ctx.removeFlag(userId);
            return null;
        }, cb);
    }

    private static void checkCanFlag(Ride r, String uid, String userId) throws AppException {
        if (!r.isOrganizer(uid)) throw AppException.notAllowed("Only the ride organizer can flag non-payment.");
        if (r.getActivityStatus() != ActivityStatus.COMPLETED) {
            throw AppException.notAllowed("Riders can be flagged once the ride is completed.");
        }
        if (uid.equals(userId)) throw AppException.notAllowed("You can't flag your own share.");
        CostShare share = r.shareOf(userId);
        if (share == null) throw AppException.notAllowed("This rider has no share in the ride.");
        if (share.isPaid()) throw AppException.notAllowed("This rider has already paid.");
    }

    /** Flags on the riders of one ride; only the organizer can read them. */
    public void getFlagsForRide(String rideId, Callback<List<NonPaymentFlag>> cb) {
        rides.getFlagsForRide(rideId, session.uid(), cb);
    }

    /** Flags organizers have placed on the signed-in user (shown on their profile). */
    public void getMyFlags(Callback<List<NonPaymentFlag>> cb) {
        rides.getFlagsAgainst(session.uid(), cb);
    }
}
