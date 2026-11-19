
package com.team23.trojanrideshare.ui.history;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.team23.trojanrideshare.testutil.UiActions.waitFor;
import static org.junit.Assert.assertEquals;

import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.material.slider.Slider;
import com.team23.trojanrideshare.R;
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
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;

@RunWith(AndroidJUnit4.class)
public class RideHistoryUiTest {

    private AppContainer app;
    private ActivityScenario<MainActivity> scenario;

    @Before
    public void setUp() {
        app = TestBackend.install();

        TestBackend.signUp(
                app,
                "Alex Rivera",
                "alex.history@usc.edu",
                "1000000099"
        );

        scenario = ActivityScenario.launch(MainActivity.class);
    }

    @After
    public void tearDown() {
        if (scenario != null) {
            scenario.close();
        }
    }

    // --------------------------------------------------
    // Shared helper: Verify history list contents
    // --------------------------------------------------

    private void assertHistoryTitles(String... expectedTitles) {

        waitFor(withId(R.id.list), (view, error) -> {
            if (error != null) {
                throw error;
            }

            RecyclerView list = (RecyclerView) view;
            RecyclerView.Adapter adapter = list.getAdapter();

            assertEquals(
                    "Number of rides in history",
                    expectedTitles.length,
                    adapter.getItemCount()
            );

            for (int i = 0; i < expectedTitles.length; i++) {

                RecyclerView.ViewHolder holder =
                        adapter.createViewHolder(
                                list,
                                adapter.getItemViewType(i)
                        );

                adapter.bindViewHolder(holder, i);

                TextView title =
                        holder.itemView.findViewById(R.id.title);

                assertEquals(
                        "Ride title at position " + i,
                        expectedTitles[i],
                        title.getText().toString()
                );
            }
        });
    }

    // --------------------------------------------------
    // Shared helper: Prepare historical rides
    // --------------------------------------------------

    /**
     * A: Completed, USC to LAX
     * B: Cancelled, USC to Santa Monica
     * C: Completed, LAX to USC
     * D: Active, USC to LAX
     *
     * Historical newest-first order: C, B, A.
     */
    private void prepareHistoryRides() {

        LocalDateTime departure =
                app.clock.now().plusMinutes(30);

        Ride rideA = TestBackend.postRide(
                app,
                "History Test - LAX",
                TestBackend.USC,
                TestBackend.LAX,
                departure,
                4
        );

        Ride rideB = TestBackend.postRide(
                app,
                "History Test - Santa Monica",
                TestBackend.USC,
                TestBackend.SANTA_MONICA,
                departure.plusMinutes(5),
                4
        );

        Ride rideC = TestBackend.postRide(
                app,
                "History Test - Return",
                TestBackend.LAX,
                TestBackend.USC,
                departure.plusMinutes(10),
                4
        );

        // Active ride, scheduled two days later.
        TestBackend.postRide(
                app,
                "History Test - Upcoming",
                TestBackend.USC,
                TestBackend.LAX,
                departure.plusDays(2),
                4
        );

        // Cancel ride B.
        TestBackend.<Ride>await(cb ->
                app.rides.cancelRide(
                        rideB.getRideId(), cb
                )
        );

        // Advance the test clock.
        app.setClockOffset(Duration.ofHours(2));

        // Complete ride A.
        TestBackend.<Ride>await(cb ->
                app.costs.completeRide(
                        rideA.getRideId(),
                        4000,
                        new EqualSplit(null),
                        cb
                )
        );

        // Complete ride C.
        TestBackend.<Ride>await(cb ->
                app.costs.completeRide(
                        rideC.getRideId(),
                        4000,
                        new EqualSplit(null),
                        cb
                )
        );
    }

    // --------------------------------------------------
    // Shared helper: Open Ride History
    // --------------------------------------------------

    private void openHistory() {
        waitFor(
                withId(R.id.nav_history),
                matches(isDisplayed())
        );

        onView(withId(R.id.nav_history))
                .perform(click());
    }

    // --------------------------------------------------
    // Shared helper: Select an autocomplete location
    // --------------------------------------------------

    private void pickPlace(
            int fieldId,
            String typed,
            String suggestion
    ) {
        onView(withId(fieldId))
                .perform(click(), typeText(typed));

        waitFor(
                withText(suggestion),
                isPlatformPopup(),
                matches(isDisplayed())
        );

        onView(withText(suggestion))
                .inRoot(isPlatformPopup())
                .perform(click());

        onView(withId(fieldId))
                .perform(closeSoftKeyboard());
    }

    // --------------------------------------------------
    // Shared helper: Set radius slider value
    // --------------------------------------------------

    private void setRadius(float miles) {
        onView(withId(R.id.radius))
                .perform(new ViewAction() {

                    @Override
                    public Matcher<View> getConstraints() {
                        return isAssignableFrom(Slider.class);
                    }

                    @Override
                    public String getDescription() {
                        return "Set radius to " + miles + " miles";
                    }

                    @Override
                    public void perform(
                            UiController uiController,
                            View view
                    ) {
                        Slider slider = (Slider) view;
                        slider.setValue(miles);
                        uiController.loopMainThreadUntilIdle();
                    }
                });
    }

    // ==================================================
    // BB 5.1: Completed/Cancelled rides, newest first
    // ==================================================

    @Test
    public void historyShowsCompletedAndCancelledNewestFirst() {

        prepareHistoryRides();
        openHistory();

        waitFor(
                withId(R.id.statusAll),
                matches(isDisplayed())
        );

        onView(withId(R.id.statusAll))
                .perform(click());

        assertHistoryTitles(
                "History Test - Return",
                "History Test - Santa Monica",
                "History Test - LAX"
        );
    }

    // ==================================================
    // BB 5.2: Monthly summary totals
    // ==================================================

    @Test
    public void monthlySummaryShowsCorrectCountsAndCosts() {

        YearMonth rideMonth = YearMonth.from(
                app.clock.now().plusMinutes(30)
        );

        prepareHistoryRides();
        openHistory();

        waitFor(
                withId(R.id.summaryButton),
                matches(isDisplayed())
        );

        onView(withId(R.id.summaryButton))
                .perform(click());

        String monthName = rideMonth.getMonth()
                .getDisplayName(TextStyle.FULL, Locale.US);

        waitFor(
                withId(R.id.month),
                matches(isDisplayed())
        );

        onView(withId(R.id.month))
                .perform(click());

        onView(withText(monthName))
                .inRoot(isPlatformPopup())
                .perform(click());

        onView(withId(R.id.year))
                .perform(click());

        onView(withText(String.valueOf(rideMonth.getYear())))
                .inRoot(isPlatformPopup())
                .perform(click());

        onView(withId(R.id.month))
                .check(matches(withText(monthName)));

        onView(withId(R.id.year))
                .check(matches(withText(
                        String.valueOf(rideMonth.getYear())
                )));

        waitFor(
                withId(R.id.stats),
                matches(isDisplayed())
        );

        waitFor(
                withId(R.id.completed),
                matches(withText("2"))
        );

        waitFor(
                withId(R.id.total),
                matches(withText("$80.00"))
        );

        onView(withId(R.id.organized))
                .check(matches(withText("2")));

        onView(withId(R.id.joined))
                .check(matches(withText("0")));
    }

    // ==================================================
    // BB 5.3: Month with no activity
    // ==================================================

    @Test
    public void monthWithNoActivityShowsMessage() {

        openHistory();

        waitFor(
                withId(R.id.summaryButton),
                matches(isDisplayed())
        );

        onView(withId(R.id.summaryButton))
                .perform(click());

        waitFor(
                withId(R.id.noActivity),
                matches(withText(
                        "No activity! Please select another month."
                ))
        );
    }

    // ==================================================
    // BB 5.4: Status filtering
    // ==================================================

    @Test
    public void statusFilterUpdatesHistoryList() {

        prepareHistoryRides();
        openHistory();

        // Completed filter.
        waitFor(
                withId(R.id.statusCompleted),
                matches(isDisplayed())
        );

        onView(withId(R.id.statusCompleted))
                .perform(click());

        assertHistoryTitles(
                "History Test - Return",
                "History Test - LAX"
        );

        // Cancelled filter.
        onView(withId(R.id.statusCancelled))
                .perform(click());

        assertHistoryTitles(
                "History Test - Santa Monica"
        );

        // All filter.
        onView(withId(R.id.statusAll))
                .perform(click());

        assertHistoryTitles(
                "History Test - Return",
                "History Test - Santa Monica",
                "History Test - LAX"
        );
    }

    // ==================================================
    // BB 5.5: Search by origin, destination and radius
    // ==================================================

    @Test
    public void historySearchFiltersByOriginDestinationAndRadius() {

        // Prepare three historical rides and one active ride.
        prepareHistoryRides();

        // Navigate to Ride History.
        openHistory();

        // Initially, all three historical rides appear.
        assertHistoryTitles(
                "History Test - Return",
                "History Test - Santa Monica",
                "History Test - LAX"
        );

        // Select USC as the search origin.
        pickPlace(
                R.id.origin,
                "USC",
                TestBackend.USC.getName()
        );

        // Select LAX as the search destination.
        pickPlace(
                R.id.destination,
                "LAX",
                TestBackend.LAX.getName()
        );

        // ----------------------------------------------
        // Test 1: Small radius of 2 miles
        // ----------------------------------------------

        setRadius(2.0f);

        onView(withId(R.id.radiusLabel))
                .check(matches(withText("Radius: 2 mi")));

        // Apply the search criteria.
        onView(withId(R.id.searchButton))
                .perform(click());

        // Only ride A matches BOTH origin and destination.
        assertHistoryTitles(
                "History Test - LAX"
        );

        // Ride B is excluded because its destination
        // is Santa Monica rather than LAX.
        //
        // Ride C is excluded because its origin is LAX
        // rather than USC.

        // ----------------------------------------------
        // Test 2: Wider radius of 15 miles
        // ----------------------------------------------

        setRadius(15.0f);

        onView(withId(R.id.radiusLabel))
                .check(matches(withText("Radius: 15 mi")));

        // Reapply with the wider radius.
        onView(withId(R.id.searchButton))
                .perform(click());

        // All three historical rides fall within
        // the wider radius around both endpoints.
        assertHistoryTitles(
                "History Test - Return",
                "History Test - Santa Monica",
                "History Test - LAX"
        );

        // The active ride remains excluded regardless
        // of the radius.
    }
}
