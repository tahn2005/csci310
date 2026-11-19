package com.team23.trojanrideshare.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.team23.trojanrideshare.domain.ActivityStatus;
import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.CostShare;
import com.team23.trojanrideshare.domain.ErrorCode;
import com.team23.trojanrideshare.domain.NonPaymentFlag;
import com.team23.trojanrideshare.domain.PaymentStatus;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.domain.util.Money;
import com.team23.trojanrideshare.testutil.Fixtures;
import com.team23.trojanrideshare.testutil.Results;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Feature 4 (Ride Cost and Sharing) white-box tests for {@link CostSplitService},
 * {@link EqualSplit}, {@link CustomSplit} and {@link Money#parse}.
 * Written by Alijah Prado. Each "WB 4.x" group below is one test case in the testing document.
 *
 * Location: app/src/test/java/com/team23/trojanrideshare/service/CostSplitServiceTest.java
 * Run: right-click this file in Android Studio → Run 'CostSplitServiceTest', or
 *      ./gradlew testDebugUnitTest --tests "*CostSplitServiceTest"
 */
public class CostSplitServiceTest extends ServiceTestBase {

    /** A ride Alice organizes that departed an hour ago, so it can be completed now. */
    private Ride departedRide(String... riders) {
        return db.put(Fixtures.ride("alice", Fixtures.USC, Fixtures.LAX, NOW.minusHours(1), 6, riders));
    }

    private static long sum(List<CostShare> shares) {
        long s = 0;
        for (CostShare c : shares) s += c.getAmountCents();
        return s;
    }

    private static Map<String, Long> amounts(Object... idThenCents) {
        Map<String, Long> m = new HashMap<>();
        for (int i = 0; i < idThenCents.length; i += 2) {
            m.put((String) idThenCents[i], ((Number) idThenCents[i + 1]).longValue());
        }
        return m;
    }

    private Ride complete(Ride r, long cents, SplitStrategy s) {
        as("alice");
        Results<Ride> res = new Results<>();
        costs.completeRide(r.getRideId(), cents, s, res);
        return res.get();
    }

    private static void assertValidation(ThrowingRunnable r) {
        try {
            r.run();
            fail("Expected a VALIDATION error");
        } catch (AppException e) {
            assertEquals(ErrorCode.VALIDATION, e.getCode());
        }
    }

    private interface ThrowingRunnable {
        void run() throws AppException;
    }

    // ------------------------------------------------- WB 4.1: equal split of an odd total

    @Test
    public void equalSplitOfTenDollarsThreeWaysSumsToTheCent() throws AppException {
        Ride r = departedRide("bob", "carol");

        List<CostShare> shares = costs.preview(r, 1000, new EqualSplit(null));

        assertEquals(3, shares.size());
        // 1000 / 3 = 333 r 1 → one rider (the first, by default) pays the extra cent.
        assertEquals(334, shares.get(0).getAmountCents());
        assertEquals(333, shares.get(1).getAmountCents());
        assertEquals(333, shares.get(2).getAmountCents());
        assertEquals(1000, sum(shares));
    }

    @Test
    public void equalSplitStoresSharesThatSumToTotalAfterCompleting() {
        Ride r = departedRide("bob", "carol");

        // The organizer picks Carol to pay the leftover cent.
        complete(r, 1000, new EqualSplit(Arrays.asList("carol")));

        Ride saved = db.ride(r.getRideId());
        assertEquals(1000, sum(saved.getShares()));
        assertEquals(333, saved.shareOf("alice").getAmountCents());
        assertEquals(333, saved.shareOf("bob").getAmountCents());
        assertEquals(334, saved.shareOf("carol").getAmountCents());
        assertEquals(1000, saved.getActualCostCents());
        assertEquals(EqualSplit.METHOD, saved.getSplitMethod());
    }

    @Test
    public void equalSplitAlwaysSumsExactlyAndDiffersByAtMostOneCent() throws AppException {
        // Exhaustive check over small totals and every allowed group size (1–6 riders).
        String[] ids = {"bob", "carol", "dave", "erin", "frank"};
        for (int extra = 0; extra <= 5; extra++) {
            Ride r = departedRide(Arrays.copyOf(ids, extra));
            for (long cents = 1; cents <= 1001; cents++) {
                List<CostShare> shares = costs.preview(r, cents, new EqualSplit(null));
                long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
                for (CostShare c : shares) {
                    min = Math.min(min, c.getAmountCents());
                    max = Math.max(max, c.getAmountCents());
                }
                assertEquals("sum for " + cents + "¢ / " + (extra + 1), cents, sum(shares));
                assertTrue("fair for " + cents + "¢ / " + (extra + 1), max - min <= 1);
            }
        }
    }

    @Test
    public void equalSplitRejectsWrongNumberOfExtraCentRiders() {
        Ride r = departedRide("bob", "carol");
        // $10.00 / 3 leaves 1 cent, so choosing two riders (or none) is wrong.
        assertValidation(() -> costs.preview(r, 1000, new EqualSplit(Arrays.asList("bob", "carol"))));
        assertValidation(() -> costs.preview(r, 1000, new EqualSplit(new ArrayList<>())));
        // $9.00 / 3 divides evenly, so nobody may be picked.
        assertValidation(() -> costs.preview(r, 900, new EqualSplit(Arrays.asList("bob"))));
    }

    // ------------------------------------------------- WB 4.2: custom split with wrong total

    @Test
    public void customSplitThatAddsUpToLessIsRejectedWithTheDifference() {
        Ride r = departedRide("bob", "carol");
        try {
            costs.preview(r, 3000, new CustomSplit(amounts("alice", 1000, "bob", 1000, "carol", 900)));
            fail("Expected the mismatched total to be rejected");
        } catch (AppException e) {
            assertEquals(ErrorCode.VALIDATION, e.getCode());
            assertEquals("The shares add up to $29.00, which is $1.00 less than the actual cost of $30.00.",
                    e.getMessage());
        }
    }

    @Test
    public void customSplitThatAddsUpToMoreIsRejectedAndNothingIsSaved() {
        Ride r = departedRide("bob", "carol");
        as("alice");
        Results<Ride> res = new Results<>();

        // Off by a single cent over.
        costs.completeRide(r.getRideId(), 3000,
                new CustomSplit(amounts("alice", 1000, "bob", 1000, "carol", 1001)), res);

        AppException e = res.err();
        assertEquals(ErrorCode.VALIDATION, e.getCode());
        assertTrue(e.getMessage().contains("$0.01 more"));
        Ride saved = db.ride(r.getRideId());
        assertEquals("ride is not completed", ActivityStatus.ON_TIME, saved.getActivityStatus());
        assertTrue("no shares saved", saved.getShares().isEmpty());
    }

    @Test
    public void customSplitThatAddsUpExactlyIsAccepted() throws AppException {
        Ride r = departedRide("bob", "carol");
        List<CostShare> shares = costs.preview(r, 3000,
                new CustomSplit(amounts("alice", 500, "bob", 1500, "carol", 1000)));
        assertEquals(3000, sum(shares));
        assertEquals(1500, shares.get(1).getAmountCents());
    }

    @Test
    public void customSplitRejectsMissingAndNegativeAmounts() {
        Ride r = departedRide("bob", "carol");
        // Carol has no amount at all.
        assertValidation(() -> costs.preview(r, 3000, new CustomSplit(amounts("alice", 1500, "bob", 1500))));
        // Total matches, but one amount is negative.
        assertValidation(() -> costs.preview(r, 3000,
                new CustomSplit(amounts("alice", 2000, "bob", 1500, "carol", -500))));
    }

    // ------------------------------------------------- WB 4.3: actual cost input rules

    @Test
    public void costEntryRejectsNegativeAndTooManyDecimals() {
        assertValidation(() -> Money.parse("-5.00"));
        assertValidation(() -> Money.parse("-0.01"));
        assertValidation(() -> Money.parse("10.999"));
        assertValidation(() -> Money.parse("0.001"));
        assertValidation(() -> Money.parse("12.345"));
    }

    @Test
    public void costEntryRejectsZeroBeforeSplitting() {
        Ride r = departedRide("bob");
        // "0" and "0.00" are well-formed numbers, so the zero check lives in the service.
        assertValidation(() -> costs.preview(r, safeParse("0"), new EqualSplit(null)));
        assertValidation(() -> costs.preview(r, safeParse("0.00"), new EqualSplit(null)));
        assertValidation(() -> costs.preview(r, -100, new EqualSplit(null)));

        as("alice");
        Results<Ride> res = new Results<>();
        costs.completeRide(r.getRideId(), 0, new EqualSplit(null), res);
        assertEquals("Enter an actual cost greater than $0.", res.err().getMessage());
        assertEquals(ActivityStatus.ON_TIME, db.ride(r.getRideId()).getActivityStatus());
    }

    @Test
    public void costEntryAcceptsValuesAtTheBoundaries() throws AppException {
        assertEquals(1, Money.parse("0.01"));      // smallest valid cost
        assertEquals(1250, Money.parse("12.5"));   // one decimal place
        assertEquals(1250, Money.parse("12.50"));  // exactly two
        assertEquals(1200, Money.parse("12"));     // none
        assertEquals(120000, Money.parse("$1,200.00"));
        Ride r = departedRide();
        assertEquals(1, sum(costs.preview(r, 1, new EqualSplit(null))));
    }

    private static long safeParse(String s) {
        try {
            return Money.parse(s);
        } catch (AppException e) {
            throw new AssertionError(e);
        }
    }

    // ------------------------------------------------- WB 4.4: solo organizer bears all

    @Test
    public void soloOrganizerCompletingRidePaysTheWholeCost() {
        Ride r = departedRide(); // Alice only

        Ride done = complete(r, 2537, new EqualSplit(null));

        assertEquals(ActivityStatus.COMPLETED, done.getActivityStatus());
        Ride saved = db.ride(r.getRideId());
        assertEquals(1, saved.getShares().size());
        CostShare only = saved.shareOf("alice");
        assertNotNull(only);
        assertEquals(2537, only.getAmountCents());
        // FR-4.6.1: the organizer's own share is marked Paid automatically.
        assertEquals(PaymentStatus.PAID, only.getStatus());
        assertNotNull(only.getPaidAt());
    }

    @Test
    public void soloOrganizerCustomSplitMustAlsoCoverWholeCost() throws AppException {
        Ride r = departedRide();
        assertValidation(() -> costs.preview(r, 2000, new CustomSplit(amounts("alice", 1999))));
        assertEquals(2000, costs.preview(r, 2000, new CustomSplit(amounts("alice", 2000))).get(0).getAmountCents());
    }

    // ------------------------------------------------- WB 4.5: Mark Paid affects only my share

    @Test
    public void markingPaidChangesOnlyThatRidersShare() {
        Ride r = departedRide("bob", "carol", "dave");
        complete(r, 4000, new EqualSplit(null)); // $10.00 each

        as("bob");
        Results<Ride> res = new Results<>();
        costs.markPaid(r.getRideId(), res);
        res.get();

        Ride saved = db.ride(r.getRideId());
        assertEquals(PaymentStatus.PAID, saved.shareOf("bob").getStatus());
        assertEquals(PaymentStatus.UNPAID, saved.shareOf("carol").getStatus());
        assertEquals(PaymentStatus.UNPAID, saved.shareOf("dave").getStatus());
        assertEquals("organizer stays Paid", PaymentStatus.PAID, saved.shareOf("alice").getStatus());
        assertNull(saved.shareOf("carol").getPaidAt());
        // Amounts are untouched.
        for (CostShare c : saved.getShares()) assertEquals(1000, c.getAmountCents());
        assertEquals(4000, sum(saved.getShares()));
    }

    @Test
    public void markingPaidClearsOnlyMyOwnFlag() {
        Ride r = departedRide("bob", "carol");
        complete(r, 3000, new EqualSplit(null));
        costs.flag(r.getRideId(), "bob", new Results<NonPaymentFlag>());
        costs.flag(r.getRideId(), "carol", new Results<NonPaymentFlag>());
        assertEquals(2, db.flags.size());

        as("bob");
        costs.markPaid(r.getRideId(), new Results<Ride>());

        assertEquals(1, db.flags.size());
        assertEquals("carol", db.flags.values().iterator().next().getUserId());
    }

    @Test
    public void nonRiderCannotMarkAnyShareAndPayingTwiceChangesNothing() {
        Ride r = departedRide("bob");
        complete(r, 2000, new EqualSplit(null));

        as("carol"); // not in this ride
        Results<Ride> stranger = new Results<>();
        costs.markPaid(r.getRideId(), stranger);
        assertEquals(ErrorCode.NOT_ALLOWED, stranger.err().getCode());
        assertEquals(PaymentStatus.UNPAID, db.ride(r.getRideId()).shareOf("bob").getStatus());

        as("bob");
        costs.markPaid(r.getRideId(), new Results<Ride>());
        java.time.Instant firstPaidAt = db.ride(r.getRideId()).shareOf("bob").getPaidAt();
        clock.advance(java.time.Duration.ofHours(1));
        costs.markPaid(r.getRideId(), new Results<Ride>());
        assertEquals("second tap keeps the original time", firstPaidAt,
                db.ride(r.getRideId()).shareOf("bob").getPaidAt());
    }

    @Test
    public void sharesCannotBePaidBeforeRideIsCompleted() {
        Ride r = departedRide("bob");
        as("bob");
        Results<Ride> res = new Results<>();
        costs.markPaid(r.getRideId(), res);
        assertEquals(ErrorCode.NOT_ALLOWED, res.err().getCode());
        assertFalse(db.ride(r.getRideId()).getShares().iterator().hasNext());
    }
}
