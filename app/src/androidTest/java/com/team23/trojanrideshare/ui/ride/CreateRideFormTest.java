package com.team23.trojanrideshare.ui.ride;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withClassName;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.team23.trojanrideshare.testutil.UiActions.hasInputError;
import static com.team23.trojanrideshare.testutil.UiActions.hasNoInputError;
import static com.team23.trojanrideshare.testutil.UiActions.waitFor;
import static org.hamcrest.Matchers.equalTo;

import android.widget.DatePicker;
import android.widget.TimePicker;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.contrib.PickerActions;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.team23.trojanrideshare.R;
import com.team23.trojanrideshare.testutil.TestBackend;
import com.team23.trojanrideshare.ui.AppContainer;
import com.team23.trojanrideshare.ui.MainActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;

/**
 * Feature 2 black-box tests for the Create Ride form (BB 2.1, BB 2.2).
 * Written by Luke Hanna (Person 2). Runs on an emulator against a fresh test database.
 */
@RunWith(AndroidJUnit4.class)
public class CreateRideFormTest {

    private static final String TITLE = "USC to LAX Test Ride";

    private final LocalDate day = LocalDate.now().plusDays(7);
    private AppContainer app;
    private ActivityScenario<MainActivity> scenario;

    @Before
    public void signedInAsOrganizer() {
        app = TestBackend.install();
        TestBackend.signUp(app, "Alex Rivera", "alex.test@usc.edu", "1000000001");
        scenario = ActivityScenario.launch(MainActivity.class);
        waitFor(withId(R.id.fab), matches(isDisplayed()));
        onView(withId(R.id.fab)).perform(click()); // Search tab → Create Ride
        waitFor(withId(R.id.title), matches(isDisplayed()));
    }

    @After
    public void close() {
        if (scenario != null) scenario.close();
    }

    /** Types a keyword and taps the matching location suggestion. */
    private void pickPlace(int fieldId, String typed, String suggestion) {
        onView(withId(fieldId)).perform(scrollTo(), click(), typeText(typed));
        waitFor(withText(suggestion), isPlatformPopup(), matches(isDisplayed()));
        onView(withText(suggestion)).inRoot(isPlatformPopup()).perform(click());
        onView(withId(fieldId)).perform(closeSoftKeyboard());
    }

    /** Fills every field with valid values except the cost, which is the input under test. */
    private void fillForm(String cost) {
        onView(withId(R.id.title)).perform(scrollTo(), typeText(TITLE), closeSoftKeyboard());
        pickPlace(R.id.origin, "USC", TestBackend.USC.getName());
        pickPlace(R.id.destination, "LAX", TestBackend.LAX.getName());
        onView(withId(R.id.date)).perform(scrollTo(), click());
        onView(withClassName(equalTo(DatePicker.class.getName())))
                .perform(PickerActions.setDate(day.getYear(), day.getMonthValue(), day.getDayOfMonth()));
        onView(withId(android.R.id.button1)).perform(click());
        onView(withId(R.id.time)).perform(scrollTo(), click());
        onView(withClassName(equalTo(TimePicker.class.getName()))).perform(PickerActions.setTime(17, 0));
        onView(withId(android.R.id.button1)).perform(click());
        onView(withId(R.id.riders4)).perform(scrollTo(), click());
        onView(withId(R.id.cost)).perform(scrollTo(), replaceText(cost), closeSoftKeyboard());
    }

    private void post() {
        onView(withId(R.id.post)).perform(scrollTo(), click());
    }

    /** BB 2.1: a valid form posts the ride, and it shows up in the organizer's upcoming rides. */
    @Test
    public void postingValidRideShowsItInOrganizersUpcomingList() {
        fillForm("40");
        post();

        // The new ride opens: On-Time, Open, and the organizer is the first of 4 riders.
        waitFor(withId(R.id.capacityChip), matches(withText("Open – 1 of 4 riders")));
        onView(withId(R.id.title)).check(matches(withText(TITLE)));
        onView(withId(R.id.activityChip)).check(matches(withText("On-Time")));
        onView(withId(R.id.organizer)).check(matches(withText("Organized by Alex Rivera (you)  ›")));

        // My Rides lists it under the rides Alex organizes.
        onView(withId(R.id.nav_my_rides)).perform(click());
        waitFor(withText("Rides I organize (1)"), matches(isDisplayed()));
        onView(withText(TITLE)).check(matches(isDisplayed()));
    }

    /** BB 2.2: invalid costs show an inline error on the cost field and nothing is posted. */
    @Test
    public void invalidCostShowsInlineErrorAndDoesNotPost() {
        fillForm("0");
        post();
        waitFor(withId(R.id.costLayout), matches(hasInputError("Expected cost must be greater than $0.")));
        onView(withId(R.id.titleLayout)).check(matches(hasNoInputError()));
        onView(withId(R.id.post)).check(matches(isDisplayed())); // still on the form

        // Three decimal places is the other invalid cost format.
        onView(withId(R.id.cost)).perform(scrollTo(), replaceText("12.345"), closeSoftKeyboard());
        onView(withId(R.id.costLayout)).check(matches(hasNoInputError())); // editing clears the old error
        post(); // right away, while "Please fix the highlighted fields." is still showing (bug fix)
        waitFor(withId(R.id.costLayout), matches(hasInputError("Use at most two decimal places.")));

        // Leave without fixing it: Alex still organizes no rides.
        onView(withId(R.id.cost)).perform(scrollTo(), clearText(), closeSoftKeyboard());
        pressBack();
        onView(withId(R.id.nav_my_rides)).perform(click());
        waitFor(withText("You aren't organizing any upcoming rides. Tap Create Ride to post one."),
                matches(isDisplayed()));
    }
}
