package com.team23.trojanrideshare.ui.ride;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE;
import static androidx.test.espresso.matcher.ViewMatchers.Visibility.VISIBLE;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withClassName;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.team23.trojanrideshare.testutil.UiActions.nestedScrollTo;
import static com.team23.trojanrideshare.testutil.UiActions.waitFor;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;

import android.widget.TimePicker;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.contrib.PickerActions;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.team23.trojanrideshare.R;
import com.team23.trojanrideshare.domain.ActivityStatus;
import com.team23.trojanrideshare.domain.JoinRequest;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.testutil.TestBackend;
import com.team23.trojanrideshare.ui.AppContainer;
import com.team23.trojanrideshare.ui.MainActivity;
import com.team23.trojanrideshare.ui.common.UiFormat;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Feature 2 black-box tests for managing a posted ride on Ride Details (BB 2.3 – BB 2.5).
 * Written by Luke Hanna (Person 2). Runs on an emulator against a fresh test database.
 */
@RunWith(AndroidJUnit4.class)
public class RideManagementTest {

    private static final String ALEX = "alex.test@usc.edu";
    private static final String BAILEY = "bailey.test@usc.edu";

    private final LocalDate day = LocalDate.now().plusDays(7);
    private AppContainer app;
    private ActivityScenario<MainActivity> scenario;

    @Before
    public void freshDatabase() {
        app = TestBackend.install();
        TestBackend.signUp(app, "Alex Rivera", ALEX, "1000000001");
    }

    @After
    public void close() {
        if (scenario != null) scenario.close();
    }

    /** Opens the app (as whoever is logged in) and taps a ride on the given tab. */
    private void openRide(int tab, String title) {
        scenario = ActivityScenario.launch(MainActivity.class);
        waitFor(withId(R.id.bottomNav), matches(isDisplayed()));
        onView(withId(tab)).perform(click());
        waitFor(withText(title), matches(isDisplayed()));
        onView(withText(title)).perform(click());
        waitFor(withId(R.id.capacityChip), matches(isDisplayed()));
    }

    /** BB 2.3: the organizer marks the ride Delayed with a later time; the screen shows both. */
    @Test
    public void markingRideDelayedShowsNewTime() {
        LocalDateTime original = day.atTime(12, 0);
        Ride ride = TestBackend.postRide(app, "Santa Monica beach", TestBackend.USC, TestBackend.SANTA_MONICA, original, 3);
        openRide(R.id.nav_my_rides, "Santa Monica beach");
        onView(withId(R.id.when)).check(matches(withText(UiFormat.dateTime(original))));

        onView(withId(R.id.editDeparture)).perform(nestedScrollTo(), click());
        waitFor(withId(R.id.delayed), matches(isDisplayed()));
        onView(withId(R.id.delayed)).perform(click());
        onView(withId(R.id.time)).perform(click());
        onView(withClassName(equalTo(TimePicker.class.getName()))).perform(PickerActions.setTime(14, 0));
        onView(withId(android.R.id.button1)).perform(click()); // OK on the time picker
        onView(withText("Save")).perform(click());

        LocalDateTime delayed = day.atTime(14, 0);
        waitFor(withId(R.id.activityChip), matches(withText(ActivityStatus.DELAYED.label())));
        onView(withId(R.id.when)).check(matches(withText(UiFormat.dateTime(delayed))));
        // The change was saved, not just shown.
        Ride saved = TestBackend.await(cb -> app.rides.getRide(ride.getRideId(), cb));
        assertEquals(delayed, saved.getDeparture());
        assertEquals(ActivityStatus.DELAYED, saved.getActivityStatus());
    }

    /** BB 2.4: another student opening the ride sees no Edit Ride, Cancel Ride, or Complete option. */
    @Test
    public void nonOrganizerSeesNoEditOrCancelOptions() {
        TestBackend.postRide(app, "LAX flight", TestBackend.USC, TestBackend.LAX, day.atTime(10, 0), 4);
        TestBackend.signUp(app, "Bailey Chen", BAILEY, "1000000002"); // now logged in as Bailey
        openRide(R.id.nav_search, "LAX flight");

        onView(withId(R.id.organizer)).check(matches(withText("Organized by Alex Rivera  ›")));
        onView(withId(R.id.editDeparture)).check(matches(withEffectiveVisibility(GONE)));
        onView(withId(R.id.cancelRide)).check(matches(withEffectiveVisibility(GONE)));
        onView(withId(R.id.markCompleted)).check(matches(withEffectiveVisibility(GONE)));
        onView(withId(R.id.pendingSection)).check(matches(withEffectiveVisibility(GONE)));
        // What a non-organizer can do instead: ask to join.
        onView(withId(R.id.requestJoin)).check(matches(withEffectiveVisibility(VISIBLE)));
        onView(withId(R.id.requestJoin)).check(matches(isEnabled()));
    }

    /** BB 2.5: accepting the request that takes the last seat flips the ride to Full. */
    @Test
    public void acceptingLastSeatShowsRideAsFull() {
        Ride ride = TestBackend.postRide(app, "Pasadena run", TestBackend.USC, TestBackend.LAX, day.atTime(9, 0), 2);
        TestBackend.signUp(app, "Bailey Chen", BAILEY, "1000000002");
        TestBackend.<JoinRequest>await(cb -> app.joins.sendRequest(ride.getRideId(), "Room for one more?", cb));
        TestBackend.logIn(app, ALEX);
        openRide(R.id.nav_my_rides, "Pasadena run");
        onView(withId(R.id.capacityChip)).check(matches(withText("Open – 1 of 2 riders")));

        waitFor(withText("Accept"), matches(withEffectiveVisibility(VISIBLE)));
        onView(withText("Accept")).perform(nestedScrollTo(), click());

        waitFor(withId(R.id.capacityChip), matches(withText("Full – 2 of 2 riders")));
        onView(withId(R.id.ridersHeader)).check(matches(withText("Riders (2 of 2)")));
        onView(withId(R.id.riders)).check(matches(withText("2 of 2 riders · 0 seats left")));
        onView(withText("Bailey Chen")).check(matches(withEffectiveVisibility(VISIBLE)));
    }
}
