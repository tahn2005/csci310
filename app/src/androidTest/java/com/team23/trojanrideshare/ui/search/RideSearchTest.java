package com.team23.trojanrideshare.ui.search;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;
import static androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.team23.trojanrideshare.testutil.UiActions.hasInputError;
import static com.team23.trojanrideshare.testutil.UiActions.waitFor;
import static org.hamcrest.Matchers.allOf;

import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.ViewAssertion;
import androidx.test.espresso.action.GeneralClickAction;
import androidx.test.espresso.action.Press;
import androidx.test.espresso.action.Tap;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.material.slider.Slider;
import com.team23.trojanrideshare.R;
import com.team23.trojanrideshare.domain.Place;
import com.team23.trojanrideshare.testutil.TestBackend;
import com.team23.trojanrideshare.ui.AppContainer;
import com.team23.trojanrideshare.ui.MainActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Feature 3 black-box tests for the Ride Search page (BB 3.1 – BB 3.2).
 * Written by Person 3. Runs on an emulator against a fresh test database.
 *
 * <p>Alex posts five rides; Bailey (logged in) searches. Straight-line distances:
 * Expo Park→USC 0.41 mi, Downtown LA→USC 3.14 mi, Pasadena→USC 11.82 mi,
 * Santa Monica→LAX 6.92 mi. So for a USC→LAX search, each ride enters the results
 * at a different radius.
 */
@RunWith(AndroidJUnit4.class)
public class RideSearchTest {

    private static final Place EXPO_PARK = new Place("t-expo", "Exposition Park", "Los Angeles, CA", 34.0168, -118.2873);
    private static final Place DOWNTOWN = new Place("t-dtla", "Downtown LA", "Los Angeles, CA", 34.0522, -118.2437);
    private static final Place PASADENA = new Place("t-pas", "Pasadena", "Pasadena, CA", 34.1478, -118.1445);

    private static final String USC_LAX = "USC to LAX";              // 0 mi / 0 mi
    private static final String EXPO_LAX = "Expo Park to LAX";       // origin 0.41 mi
    private static final String DTLA_LAX = "Downtown to LAX";        // origin 3.14 mi
    private static final String USC_SM = "USC to Santa Monica";      // destination 6.92 mi from LAX
    private static final String PAS_LAX = "Pasadena to LAX";         // origin 11.82 mi

    private static final long SUGGESTION_SETTLE_MS = 700;

    private final LocalDate day = LocalDate.now().plusDays(7);
    private AppContainer app;
    private ActivityScenario<MainActivity> scenario;

    @Before
    public void postRidesAndLogInAsSearcher() {
        app = TestBackend.install();
        TestBackend.signUp(app, "Alex Rivera", "alex.test@usc.edu", "1000000001");
        TestBackend.postRide(app, USC_LAX, TestBackend.USC, TestBackend.LAX, day.atTime(9, 0), 4);
        TestBackend.postRide(app, EXPO_LAX, EXPO_PARK, TestBackend.LAX, day.atTime(10, 0), 4);
        TestBackend.postRide(app, DTLA_LAX, DOWNTOWN, TestBackend.LAX, day.atTime(11, 0), 4);
        TestBackend.postRide(app, USC_SM, TestBackend.USC, TestBackend.SANTA_MONICA, day.atTime(12, 0), 4);
        TestBackend.postRide(app, PAS_LAX, PASADENA, TestBackend.LAX, day.atTime(13, 0), 4);
        TestBackend.signUp(app, "Bailey Chen", "bailey.test@usc.edu", "1000000002"); // now logged in

        scenario = ActivityScenario.launch(MainActivity.class);
        // Wait for the saved login to finish restoring: that is when the app opens the Search tab.
        // (Tapping the Search tab again, or typing before this, would act on a screen that is
        // about to be replaced.)
        waitFor(withId(R.id.splash), matches(withEffectiveVisibility(GONE)));
        waitFor(withId(R.id.bottomNav), matches(isDisplayed()));
        // Before searching, every ride by another student is listed.
        waitFor(results(), showsRides(USC_LAX, EXPO_LAX, DTLA_LAX, USC_SM, PAS_LAX));
    }

    /** Logs out so the next test starts at the Log In screen, as other test classes expect. */
    @After
    public void cleanUp() {
        if (scenario != null) scenario.close();
        if (app != null) app.account.logOut();
    }

    // ---------------------------------------------------------------- helpers

    private static org.hamcrest.Matcher<View> results() {
        return allOf(withId(R.id.list), isDisplayed());
    }

    private void pickPlace(int fieldId, String typed, String suggestion) {
        onView(withId(fieldId)).perform(click(), typeText(typed));
        // The field asks for suggestions 350 ms after the last keystroke and redraws the list.
        // Tapping before that can land on the radius slider under the field, so let it settle.
        SystemClock.sleep(SUGGESTION_SETTLE_MS);
        waitFor(withText(suggestion), isPlatformPopup(), matches(isDisplayed()));
        onView(withText(suggestion)).inRoot(isPlatformPopup()).perform(click());
        onView(withId(fieldId)).perform(closeSoftKeyboard());
    }

    private void search(String originTyped, Place origin, String destinationTyped, Place destination) {
        pickPlace(R.id.origin, originTyped, origin.getName());
        pickPlace(R.id.destination, destinationTyped, destination.getName());
        onView(withId(R.id.radiusLabel)).check(matches(withText("Radius: 5 mi"))); // default, untouched
        onView(withId(R.id.searchButton)).perform(click());
    }

    /**
     * Checks the rides in the results list, in order, by binding each row the way the list
     * would show it (rows scrolled off-screen are checked too).
     */
    private static ViewAssertion showsRides(String... expected) {
        return (view, noView) -> {
            if (noView != null) throw noView;
            RecyclerView list = (RecyclerView) view;
            @SuppressWarnings("unchecked")
            RecyclerView.Adapter<RecyclerView.ViewHolder> a =
                    (RecyclerView.Adapter<RecyclerView.ViewHolder>) list.getAdapter();
            List<String> actual = new ArrayList<>();
            for (int i = 0; a != null && i < a.getItemCount(); i++) {
                RecyclerView.ViewHolder h = a.createViewHolder(list, a.getItemViewType(i));
                a.onBindViewHolder(h, i);
                actual.add(String.valueOf(((TextView) h.itemView.findViewById(R.id.title)).getText()));
            }
            if (!actual.equals(Arrays.asList(expected))) {
                View root = view.getRootView();
                throw new AssertionError("Expected rides " + Arrays.asList(expected) + " but the list shows " + actual
                        + " (screen: origin=\"" + textOf(root, R.id.origin) + "\", destination=\""
                        + textOf(root, R.id.destination) + "\", " + textOf(root, R.id.radiusLabel) + ")");
            }
        };
    }

    private static String textOf(View root, int id) {
        View v = root.findViewById(id);
        return v instanceof TextView ? String.valueOf(((TextView) v).getText()) : "?";
    }

    /**
     * Taps the slider track where {@code miles} is, like a finger would. A tap fires the
     * slider's touch listeners, which is what makes the page search again on its own.
     */
    private static ViewAction tapSliderAt(float miles) {
        return new GeneralClickAction(Tap.SINGLE, view -> {
            Slider s = (Slider) view;
            int[] xy = new int[2];
            s.getLocationOnScreen(xy);
            float fraction = (miles - s.getValueFrom()) / (s.getValueTo() - s.getValueFrom());
            float x = xy[0] + s.getTrackSidePadding() + fraction * s.getTrackWidth();
            float y = xy[1] + s.getHeight() / 2f;
            return new float[]{x, y};
        }, Press.FINGER, 0, 0);
    }

    private static void setRadiusByTouch(float miles, String expectedLabel) {
        onView(allOf(withId(R.id.radius), isAssignableFrom(Slider.class))).perform(tapSliderAt(miles));
        onView(withId(R.id.radiusLabel)).check(matches(withText(expectedLabel)));
    }

    // ---------------------------------------------------------------- BB 3.1: search by origin/destination

    /** BB 3.1a: USC → LAX within the default 5 mi returns the three rides with both ends nearby. */
    @Test
    public void searchUscToLaxReturnsRidesNearBothEnds() {
        onView(withId(R.id.radiusLabel)).check(matches(withText("Radius: 5 mi")));

        search("USC", TestBackend.USC, "LAX", TestBackend.LAX);

        // Listed earliest first (the default sort).
        waitFor(results(), showsRides(USC_LAX, EXPO_LAX, DTLA_LAX));
    }

    /** BB 3.1b: a different destination returns a different set (only the Santa Monica ride). */
    @Test
    public void searchUscToSantaMonicaReturnsOnlyThatRoute() {
        search("USC", TestBackend.USC, "santa monica", TestBackend.SANTA_MONICA);

        waitFor(results(), showsRides(USC_SM));
    }

    /** BB 3.1c: the reverse direction (LAX → USC) matches nothing and says so. */
    @Test
    public void searchInReverseDirectionShowsNoMatchMessage() {
        search("LAX", TestBackend.LAX, "USC", TestBackend.USC);

        waitFor(results(), showsRides());
        waitFor(withId(R.id.empty), matches(withText(RideSearchViewModel.NO_MATCH)));
    }

    /** BB 3.1d: searching with only an origin is refused and the list is left as it was. */
    @Test
    public void searchWithoutDestinationShowsErrorAndKeepsList() {
        pickPlace(R.id.origin, "USC", TestBackend.USC.getName());
        onView(withId(R.id.searchButton)).perform(click());

        onView(withId(R.id.destinationLayout)).check(matches(hasInputError("Enter a destination to search.")));
        waitFor(results(), showsRides(USC_LAX, EXPO_LAX, DTLA_LAX, USC_SM, PAS_LAX));
    }

    // ---------------------------------------------------------------- BB 3.2: radius slider updates live

    /**
     * BB 3.2: after one USC → LAX search, moving only the slider (no second tap on Search)
     * removes and adds rides: 5 mi → 3 rides, 1 mi → 2, 8 mi → 4, 15 mi → 5.
     */
    @Test
    public void movingRadiusSliderUpdatesResultsLive() {
        search("USC", TestBackend.USC, "LAX", TestBackend.LAX);
        waitFor(results(), showsRides(USC_LAX, EXPO_LAX, DTLA_LAX));

        setRadiusByTouch(1f, "Radius: 1 mi");
        waitFor(results(), showsRides(USC_LAX, EXPO_LAX));            // Downtown (3.14 mi) removed

        setRadiusByTouch(8f, "Radius: 8 mi");
        waitFor(results(), showsRides(USC_LAX, EXPO_LAX, DTLA_LAX, USC_SM)); // Downtown back, Santa Monica (6.92) added

        setRadiusByTouch(15f, "Radius: 15 mi");
        waitFor(results(), showsRides(USC_LAX, EXPO_LAX, DTLA_LAX, USC_SM, PAS_LAX)); // Pasadena (11.82) added
    }

    /** BB 3.2b: the smallest radius (0.5 mi) still keeps a ride whose origin is 0.41 mi away. */
    @Test
    public void smallestRadiusKeepsOnlyRidesWithinHalfAMile() {
        search("USC", TestBackend.USC, "LAX", TestBackend.LAX);
        waitFor(results(), showsRides(USC_LAX, EXPO_LAX, DTLA_LAX));

        setRadiusByTouch(0.5f, "Radius: 0.5 mi");

        waitFor(results(), showsRides(USC_LAX, EXPO_LAX));
    }
}
