package com.team23.trojanrideshare.ui.myrides;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE;
import static androidx.test.espresso.matcher.ViewMatchers.Visibility.VISIBLE;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isNotEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.team23.trojanrideshare.testutil.UiActions.nestedScrollTo;
import static com.team23.trojanrideshare.testutil.UiActions.waitFor;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertEquals;

import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.team23.trojanrideshare.R;
import com.team23.trojanrideshare.domain.JoinRequest;
import com.team23.trojanrideshare.domain.RequestStatus;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.service.JoinRequestService;
import com.team23.trojanrideshare.testutil.TestBackend;
import com.team23.trojanrideshare.ui.AppContainer;
import com.team23.trojanrideshare.ui.MainActivity;

import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Feature 3 black-box tests for requests to join and leaving a ride (BB 3.3 – BB 3.5).
 * Written by Person 3. Runs on an emulator against a fresh test database.
 */
@RunWith(AndroidJUnit4.class)
public class JoinRequestFlowTest {

    private static final String ALEX = "alex.test@usc.edu";
    private static final String BAILEY = "bailey.test@usc.edu";
    private static final String CASEY = "casey.test@usc.edu";

    private final LocalDate day = LocalDate.now().plusDays(7);
    private AppContainer app;
    private ActivityScenario<MainActivity> scenario;

    @Before
    public void freshDatabase() {
        app = TestBackend.install();
        TestBackend.signUp(app, "Alex Rivera", ALEX, "1000000001");
        TestBackend.signUp(app, "Bailey Chen", BAILEY, "1000000002");
        TestBackend.signUp(app, "Casey Kim", CASEY, "1000000003");
        TestBackend.logIn(app, ALEX);
    }

    /**
     * Leaves the app the way the next test expects it: logged out, with My Rides set back to its
     * first tab. Otherwise later test classes start logged in, or on "Joined"/"My Requests".
     */
    @After
    public void cleanUp() {
        if (scenario != null) scenario.close();
        if (app != null) app.account.logOut();
        resetMyRidesTab();
    }

    /** My Rides remembers its last tab in a static field, so it outlives this test. Put it back to Organizing. */
    private static void resetMyRidesTab() {
        try {
            Field lastTab = MyRidesFragment.class.getDeclaredField("lastTab");
            lastTab.setAccessible(true);
            lastTab.setInt(null, 0);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Couldn't reset the My Rides tab", e);
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Logs in (closing any open screen first) and opens the app on the given bottom tab. */
    private void openAs(String email, int tab) {
        if (scenario != null) scenario.close();
        TestBackend.logIn(app, email);
        scenario = ActivityScenario.launch(MainActivity.class);
        // The saved login is restored in the background; wait for it before using the tabs.
        waitFor(withId(R.id.splash), matches(withEffectiveVisibility(GONE)));
        waitFor(withId(R.id.bottomNav), matches(isDisplayed()));
        if (tab != R.id.nav_search) onView(withId(tab)).perform(click()); // Search opens by default
    }

    /** On My Rides & Requests, switches to a tab by its label ("Organizing", "Joined", "My Requests"). */
    private void myRidesTab(String label) {
        waitFor(withText(label), matches(isDisplayed()));
        onView(withText(label)).perform(click());
    }

    private void openRideFromList(String title) {
        waitFor(allOf(withId(R.id.title), withText(title)), matches(isDisplayed()));
        onView(allOf(withId(R.id.title), withText(title))).perform(click());
        waitFor(withId(R.id.capacityChip), matches(isDisplayed()));
    }

    /** A view inside the request card whose heading or title is {@code heading}. */
    private static Matcher<View> inCard(int id, String heading) {
        return allOf(withId(id), isDescendantOfA(allOf(withId(R.id.card), hasDescendant(withText(heading)))));
    }

    private Ride post(String title, LocalDateTime when, int max) {
        return TestBackend.postRide(app, title, TestBackend.USC, TestBackend.LAX, when, max);
    }

    /** Sends a request as {@code email}, then logs back in as Alex. */
    private JoinRequest requestAs(String email, Ride r, String message) {
        TestBackend.logIn(app, email);
        JoinRequest q = TestBackend.await(cb -> app.joins.sendRequest(r.getRideId(), message, cb));
        TestBackend.logIn(app, ALEX);
        return q;
    }

    private void acceptAsAlex(JoinRequest q) {
        TestBackend.logIn(app, ALEX);
        TestBackend.<Ride>await(cb -> app.joins.accept(q, cb));
    }

    // ---------------------------------------------------------------- BB 3.3: send a join request

    /**
     * BB 3.3: Bailey finds the ride on Search, taps Request to Join, adds a message, and sends.
     * My Rides › My Requests then lists it as Pending with the message.
     */
    @Test
    public void sentRequestAppearsUnderMyRequestsAsPending() {
        post("LAX flight", day.atTime(10, 0), 4);

        openAs(BAILEY, R.id.nav_search);
        openRideFromList("LAX flight");
        onView(withId(R.id.requestJoin)).perform(nestedScrollTo(), click());
        waitFor(withId(R.id.message), isDialog(), matches(isDisplayed()));
        onView(withId(R.id.message)).inRoot(isDialog()).perform(typeText("Room for a suitcase?"), closeSoftKeyboard());
        onView(withText("Send")).inRoot(isDialog()).perform(click());

        // Ride Details now offers to cancel instead of request.
        waitFor(withId(R.id.cancelRequest), matches(withEffectiveVisibility(VISIBLE)));
        onView(withId(R.id.requestJoin)).check(matches(withEffectiveVisibility(GONE)));

        onView(withId(R.id.nav_my_rides)).perform(click());
        myRidesTab("My Requests");
        waitFor(inCard(R.id.status, "LAX flight"), matches(withText(RequestStatus.PENDING.label())));
        onView(inCard(R.id.message, "LAX flight")).check(matches(withText("“Room for a suitcase?”")));
        onView(inCard(R.id.secondary, "LAX flight")).check(matches(withText("Cancel Request")));
    }

    /** BB 3.3b: a request with no message is still Pending, and the message line is hidden. */
    @Test
    public void requestWithoutMessageIsPendingWithNoMessageShown() {
        post("Airport run", day.atTime(8, 0), 3);

        openAs(BAILEY, R.id.nav_search);
        openRideFromList("Airport run");
        onView(withId(R.id.requestJoin)).perform(nestedScrollTo(), click());
        onView(withText("Send")).inRoot(isDialog()).perform(click());
        waitFor(withId(R.id.cancelRequest), matches(withEffectiveVisibility(VISIBLE)));

        onView(withId(R.id.nav_my_rides)).perform(click());
        myRidesTab("My Requests");
        waitFor(inCard(R.id.status, "Airport run"), matches(withText(RequestStatus.PENDING.label())));
        onView(inCard(R.id.message, "Airport run"))
                .check(matches(withEffectiveVisibility(GONE)));
    }

    // ---------------------------------------------------------------- BB 3.4: accept one, reject another

    /**
     * BB 3.4: Alex accepts Bailey and rejects Casey from My Rides › Organizing. Ride Details then
     * lists Bailey as a rider (not Casey), and Casey's My Requests shows Rejected.
     */
    @Test
    public void acceptingOneAndRejectingAnotherUpdatesRidersAndStatus() {
        Ride r = post("Union Station trip", day.atTime(15, 0), 3);
        requestAs(BAILEY, r, "I'm in!");
        requestAs(CASEY, r, "Me too?");

        openAs(ALEX, R.id.nav_my_rides);
        myRidesTab("Organizing");
        waitFor(withText("Requests waiting for you (2)"), matches(isDisplayed()));
        onView(inCard(R.id.primary, "Bailey Chen  ›")).check(matches(withText("Accept"))).perform(click());
        waitFor(withText("Requests waiting for you (1)"), matches(isDisplayed()));
        onView(inCard(R.id.secondary, "Casey Kim  ›")).check(matches(withText("Reject"))).perform(click());
        waitFor(withText("Requests waiting for you (1)"), doesNotExist());

        openRideFromList("Union Station trip");
        onView(withId(R.id.capacityChip)).check(matches(withText("Open – 2 of 3 riders")));
        onView(withId(R.id.ridersHeader)).check(matches(withText("Riders (2 of 3)")));
        onView(withText("Bailey Chen")).perform(nestedScrollTo()).check(matches(isDisplayed()));
        onView(withText("Casey Kim")).check(doesNotExist());
        onView(withId(R.id.pendingEmpty)).perform(nestedScrollTo()).check(matches(withText("No pending requests.")));

        openAs(CASEY, R.id.nav_my_rides);
        myRidesTab("My Requests");
        waitFor(inCard(R.id.status, "Union Station trip"), matches(withText(RequestStatus.REJECTED.label())));
        onView(inCard(R.id.buttons, "Union Station trip"))
                .check(matches(withEffectiveVisibility(GONE)));

        openAs(BAILEY, R.id.nav_my_rides);
        myRidesTab("My Requests");
        waitFor(inCard(R.id.status, "Union Station trip"), matches(withText(RequestStatus.ACCEPTED.label())));
    }

    // ---------------------------------------------------------------- BB 3.5: leaving a ride

    /** BB 3.5: Bailey leaves a Full 2-seat ride; the capacity changes to Open – 1 of 2. */
    @Test
    public void leavingFullRideReopensItAndUpdatesCapacity() {
        Ride r = post("Beach day", day.atTime(11, 0), 2);
        acceptAsAlex(requestAs(BAILEY, r, ""));

        openAs(BAILEY, R.id.nav_my_rides);
        myRidesTab("Joined");
        openRideFromList("Beach day");
        onView(withId(R.id.capacityChip)).check(matches(withText("Full – 2 of 2 riders")));

        onView(withId(R.id.leaveRide)).perform(nestedScrollTo(), click());
        waitFor(withText("Leave this ride?"), isDialog(), matches(isDisplayed()));
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());

        waitFor(withId(R.id.capacityChip), matches(withText("Open – 1 of 2 riders")));
        onView(withId(R.id.riders)).check(matches(withText("1 of 2 riders · 1 seat left")));
        onView(withId(R.id.ridersHeader)).check(matches(withText("Riders (1 of 2)")));
        onView(withId(R.id.requestJoin)).check(matches(withEffectiveVisibility(VISIBLE)));
        Ride saved = TestBackend.await(cb -> app.rides.getRide(r.getRideId(), cb));
        assertEquals(1, saved.riderCount());
    }

    /** BB 3.5b: a Full ride is hidden from another student's search, and shows up again once a seat opens. */
    @Test
    public void reopenedRideReappearsInSearchForOthers() {
        Ride r = post("Concert ride", day.atTime(19, 0), 2);
        acceptAsAlex(requestAs(BAILEY, r, ""));

        openAs(CASEY, R.id.nav_search);
        waitFor(withId(R.id.empty), matches(isDisplayed())); // "Available seats only" hides the Full ride
        onView(withText("Concert ride")).check(doesNotExist());

        scenario.close();
        TestBackend.logIn(app, BAILEY);
        TestBackend.<JoinRequestService.LeaveOutcome>await(cb -> app.joins.leaveRide(r.getRideId(), cb));

        openAs(CASEY, R.id.nav_search);
        waitFor(allOf(withId(R.id.title), withText("Concert ride")), matches(isDisplayed()));
        onView(allOf(withId(R.id.capacityChip), isDescendantOfA(allOf(withId(R.id.card),
                hasDescendant(withText("Concert ride")))))).check(matches(withText("Open – 1 of 2 riders")));
    }

    /** BB 3.5c: within 3 hours of departure Leave Ride is disabled and the reason is shown. */
    @Test
    public void leaveIsDisabledWithinThreeHoursOfDeparture() {
        Ride r = post("Soon ride", LocalDateTime.now().plusHours(2), 4);
        acceptAsAlex(requestAs(BAILEY, r, ""));

        openAs(BAILEY, R.id.nav_my_rides);
        myRidesTab("Joined");
        openRideFromList("Soon ride");

        onView(withId(R.id.leaveRide)).perform(nestedScrollTo()).check(matches(isNotEnabled()));
        onView(withId(R.id.reason)).check(matches(withText(JoinRequestService.LEAVE_TOO_LATE)));
        List<String> riders = TestBackend.<Ride>await(cb -> app.rides.getRide(r.getRideId(), cb)).memberIds();
        assertEquals(2, riders.size());
    }
}
