package com.team23.trojanrideshare.ui.cost;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE;
import static androidx.test.espresso.matcher.ViewMatchers.Visibility.VISIBLE;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withParent;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.team23.trojanrideshare.testutil.UiActions.nestedScrollTo;
import static com.team23.trojanrideshare.testutil.UiActions.waitFor;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.team23.trojanrideshare.R;
import com.team23.trojanrideshare.domain.ActivityStatus;
import com.team23.trojanrideshare.domain.CostShare;
import com.team23.trojanrideshare.domain.JoinRequest;
import com.team23.trojanrideshare.domain.PaymentStatus;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.service.EqualSplit;
import com.team23.trojanrideshare.testutil.TestBackend;
import com.team23.trojanrideshare.ui.AppContainer;
import com.team23.trojanrideshare.ui.MainActivity;

import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;

/**
 * Feature 4 (Ride Cost and Sharing) black-box tests: Complete Ride screen, Ride Details cost
 * card, and the non-payment notice on Profile (BB 4.1 – BB 4.5). Written by Alijah Prado.
 * Runs on an emulator against a fresh on-device test database (TestBackend), never Firebase.
 *
 * Location: app/src/androidTest/java/com/team23/trojanrideshare/ui/cost/CostSharingTest.java
 * Run: start an emulator, then right-click this file in Android Studio → Run 'CostSharingTest',
 *      or ./gradlew connectedDebugAndroidTest
 *          -Pandroid.testInstrumentationRunnerArguments.class=com.team23.trojanrideshare.ui.cost.CostSharingTest
 *
 * A ride can only be completed after it departs, and a new ride must depart in the future, so
 * setup posts the ride an hour ahead, lets riders join, then moves the app clock forward two
 * hours (the same demo-clock the Profile screen offers). The clock is reset after every test.
 */
@RunWith(AndroidJUnit4.class)
public class CostSharingTest {

    private static final String ALEX = "alex.cost@usc.edu";
    private static final String BAILEY = "bailey.cost@usc.edu";
    private static final String CASEY = "casey.cost@usc.edu";
    private static final String DREW = "drew.cost@usc.edu";

    private AppContainer app;
    private ActivityScenario<MainActivity> scenario;

    @Before
    public void freshDatabase() {
        app = TestBackend.install();
        app.setClockOffset(Duration.ZERO);
        TestBackend.signUp(app, "Alex Rivera", ALEX, "2000000001");
    }

    @After
    public void close() {
        if (scenario != null) scenario.close();
        app.setClockOffset(Duration.ZERO);
    }

    // ------------------------------------------------------------------------ setup helpers

    /**
     * Alex posts a ride; each extra student signs up and is accepted; then the ride departs.
     * Ends logged in as Alex. {@code riders} alternates full name, email, USC ID.
     */
    private Ride departedRide(String title, String... riders) {
        TestBackend.logIn(app, ALEX);
        Ride ride = TestBackend.postRide(app, title, TestBackend.USC, TestBackend.LAX,
                app.clock.now().plusHours(1), 6);
        for (int i = 0; i < riders.length; i += 3) {
            TestBackend.signUp(app, riders[i], riders[i + 1], riders[i + 2]);
            JoinRequest q = TestBackend.await(cb -> app.joins.sendRequest(ride.getRideId(), "", cb));
            TestBackend.logIn(app, ALEX);
            TestBackend.<Ride>await(cb -> app.joins.accept(q, cb));
        }
        app.setClockOffset(Duration.ofHours(2));
        return ride;
    }

    /** A departed ride Alex has already completed for {@code cents}, split equally. */
    private Ride completedRide(String title, long cents, String... riders) {
        Ride r = departedRide(title, riders);
        return TestBackend.await(cb -> app.costs.completeRide(r.getRideId(), cents, new EqualSplit(null), cb));
    }

    private Ride reload(Ride r) {
        return TestBackend.await(cb -> app.rides.getRide(r.getRideId(), cb));
    }

    /** (Re)launches the app as whoever is logged in and opens the ride from the given tab. */
    private void openRide(int tab, String title) {
        if (scenario != null) scenario.close();
        scenario = ActivityScenario.launch(MainActivity.class);
        waitFor(withId(R.id.bottomNav), matches(isDisplayed()));
        onView(withId(tab)).perform(click());
        waitFor(withText(title), matches(isDisplayed()));
        onView(withText(title)).perform(click());
        waitFor(withId(R.id.capacityChip), matches(isDisplayed()));
    }

    /** Opens a departed ride as Alex and taps Mark as Completed. */
    private void openCostScreen(String title) {
        openRide(R.id.nav_my_rides, title);
        onView(withId(R.id.markCompleted)).perform(nestedScrollTo(), click());
        waitFor(withId(R.id.save), matches(isDisplayed()));
    }

    /** A view with this id inside the Complete Ride row whose name starts with {@code name}. */
    private static Matcher<View> inSplitRow(String name, int id) {
        return allOf(withId(id), isDescendantOfA(allOf(withParent(withId(R.id.rows)),
                hasDescendant(withText(startsWith(name))))));
    }

    /** A view with this id inside the Ride Details share row whose name starts with {@code name}. */
    private static Matcher<View> inShareRow(String name, int id) {
        return allOf(withId(id), isDescendantOfA(allOf(withParent(withId(R.id.sharesList)),
                hasDescendant(withText(startsWith(name))))));
    }

    private void typeCost(String text) {
        onView(withId(R.id.cost)).perform(scrollTo(), replaceText(text), closeSoftKeyboard());
    }

    // ------------------------------------------------------------------------ BB 4.1

    /** BB 4.1: enter $10.00, split equally three ways, and the totals shown match what is saved. */
    @Test
    public void completeRideWithEqualSplitShowsAndSavesMatchingTotals() {
        Ride ride = departedRide("Airport run",
                "Bailey Chen", BAILEY, "2000000002",
                "Casey Park", CASEY, "2000000003");
        openCostScreen("Airport run");

        typeCost("10.00");

        // $10.00 / 3 = $3.33 r 1¢; the first rider (Alex) is ticked for the extra cent by default.
        waitFor(inSplitRow("Alex Rivera", R.id.share), matches(withText("$3.34")));
        onView(inSplitRow("Bailey Chen", R.id.share)).check(matches(withText("$3.33")));
        onView(inSplitRow("Casey Park", R.id.share)).check(matches(withText("$3.33")));
        onView(withId(R.id.total)).check(matches(withText("Total $10.00  ✓")));
        onView(withId(R.id.problem)).check(matches(withEffectiveVisibility(GONE)));

        onView(withId(R.id.save)).perform(scrollTo(), click());
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click()); // "Save"

        // Back on Ride Details: the cost card shows the same totals that were previewed.
        waitFor(withId(R.id.costSummary), matches(withText(startsWith("Actual cost $10.00 · Equal split"))));
        onView(inShareRow("Bailey Chen", R.id.amount)).check(matches(withText("$3.33")));

        Ride saved = reload(ride);
        assertEquals(ActivityStatus.COMPLETED, saved.getActivityStatus());
        long sum = 0;
        for (CostShare s : saved.getShares()) sum += s.getAmountCents();
        assertEquals(1000, sum);
    }

    // ------------------------------------------------------------------------ BB 4.2

    /** BB 4.2: custom amounts that fall $1.00 short show an on-screen error and can't be saved. */
    @Test
    public void customSplitThatDoesNotAddUpShowsErrorAndBlocksSave() {
        Ride ride = departedRide("Concert ride",
                "Bailey Chen", BAILEY, "2000000002",
                "Casey Park", CASEY, "2000000003");
        openCostScreen("Concert ride");
        typeCost("10.00");

        onView(withId(R.id.custom)).perform(scrollTo(), click());
        for (String name : new String[]{"Alex Rivera", "Bailey Chen", "Casey Park"}) {
            onView(inSplitRow(name, R.id.amount)).perform(scrollTo(), replaceText("3.00"), closeSoftKeyboard());
        }

        waitFor(withId(R.id.problem), matches(withText(
                "The shares add up to $9.00, which is $1.00 less than the actual cost of $10.00.")));
        onView(withId(R.id.problem)).check(matches(isDisplayed()));
        onView(withId(R.id.total)).check(matches(withText(containsString("($1.00 left)"))));
        onView(withId(R.id.save)).check(matches(not(isEnabled())));

        // Fixing one amount clears the error and enables Save.
        onView(inSplitRow("Casey Park", R.id.amount)).perform(scrollTo(), replaceText("4.00"), closeSoftKeyboard());
        waitFor(withId(R.id.problem), matches(withEffectiveVisibility(GONE)));
        onView(withId(R.id.save)).check(matches(isEnabled()));

        assertNotEquals("nothing saved yet", ActivityStatus.COMPLETED, reload(ride).getActivityStatus());
    }

    // ------------------------------------------------------------------------ BB 4.3

    /** BB 4.3: Bailey marks her share Paid; both Bailey and Alex then see it as Paid. */
    @Test
    public void markingShareAsPaidUpdatesRiderAndOrganizerViews() {
        Ride ride = completedRide("Beach day", 2000, "Bailey Chen", BAILEY, "2000000002");

        TestBackend.logIn(app, BAILEY);
        openRide(R.id.nav_history, "Beach day");
        onView(inShareRow("Bailey Chen", R.id.chip)).check(matches(withText("Unpaid")));
        onView(withId(R.id.markPaid)).perform(nestedScrollTo(), click());
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click()); // "Mark Paid"

        waitFor(inShareRow("Bailey Chen", R.id.chip), matches(withText("Paid")));
        onView(withId(R.id.markPaid)).check(matches(withEffectiveVisibility(GONE)));
        onView(withId(R.id.costSummary)).check(matches(withText(containsString("2 of 2 paid"))));

        // The organizer sees the same change.
        TestBackend.logIn(app, ALEX);
        openRide(R.id.nav_history, "Beach day");
        waitFor(inShareRow("Bailey Chen", R.id.chip), matches(withText("Paid")));
        onView(inShareRow("Bailey Chen", R.id.subtitle)).check(matches(withText(startsWith("Paid "))));
        onView(withId(R.id.costSummary)).check(matches(withText(containsString("2 of 2 paid"))));
        for (CostShare s : reload(ride).getShares()) {
            assertEquals(s.getDisplayName(), PaymentStatus.PAID, s.getStatus()); // Alex auto-paid, Bailey paid
        }
    }

    // ------------------------------------------------------------------------ BB 4.4

    /** BB 4.4: Alex flags Bailey's unpaid share; Bailey sees the flag on her own profile. */
    @Test
    public void flaggingUnpaidRiderShowsFlagOnTheirProfile() {
        completedRide("Dinner downtown", 2000, "Bailey Chen", BAILEY, "2000000002");

        openRide(R.id.nav_history, "Dinner downtown"); // as Alex
        onView(inShareRow("Bailey Chen", R.id.action)).check(matches(withText("Flag")));
        onView(inShareRow("Bailey Chen", R.id.action)).perform(nestedScrollTo(), click());
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click()); // "Flag"
        waitFor(inShareRow("Bailey Chen", R.id.subtitle), matches(withText("⚑ Flagged for non-payment")));
        // The organizer can never flag their own share.
        onView(inShareRow("Alex Rivera", R.id.action)).check(matches(withEffectiveVisibility(GONE)));

        TestBackend.logIn(app, BAILEY);
        if (scenario != null) scenario.close();
        scenario = ActivityScenario.launch(MainActivity.class);
        waitFor(withId(R.id.bottomNav), matches(isDisplayed()));
        onView(withId(R.id.nav_profile)).perform(click());
        waitFor(withText(containsString("Alex Rivera flagged your unpaid share of $10.00")),
                matches(isDisplayed()));
        onView(withText(allOf(containsString("flagged your unpaid share"), containsString("Dinner downtown"))))
                .check(matches(isDisplayed()));
    }

    // ------------------------------------------------------------------------ BB 4.5

    /** BB 4.5: before saving, the screen and the confirm dialog list every rider's amount. */
    @Test
    public void costScreenShowsEveryParticipantsAmountBeforeSaving() {
        Ride ride = departedRide("Ski trip",
                "Bailey Chen", BAILEY, "2000000002",
                "Casey Park", CASEY, "2000000003",
                "Drew Kim", DREW, "2000000004");
        openCostScreen("Ski trip");

        typeCost("40.00");

        String[] names = {"Alex Rivera", "Bailey Chen", "Casey Park", "Drew Kim"};
        waitFor(inSplitRow("Drew Kim", R.id.share), matches(withText("$10.00")));
        for (String n : names) {
            onView(inSplitRow(n, R.id.share)).perform(scrollTo()).check(matches(withText("$10.00")));
        }
        onView(inSplitRow("Alex Rivera", R.id.name)).check(matches(withText("Alex Rivera (organizer)")));

        // The confirm dialog repeats each rider's amount; backing out saves nothing.
        onView(withId(R.id.save)).perform(scrollTo(), click());
        for (String n : names) {
            onView(withText(containsString(n + ": $10.00"))).inRoot(isDialog())
                    .check(matches(withEffectiveVisibility(VISIBLE)));
        }
        onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click()); // "Back"

        onView(withId(R.id.save)).check(matches(isDisplayed()));
        Ride unsaved = reload(ride);
        assertNotEquals(ActivityStatus.COMPLETED, unsaved.getActivityStatus());
        assertTrue(unsaved.getShares().isEmpty());
    }
}
