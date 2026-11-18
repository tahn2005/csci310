package com.team23.trojanrideshare.ui.profile;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.not;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import androidx.test.espresso.contrib.RecyclerViewActions;

import com.team23.trojanrideshare.R;
import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.Callback;
import com.team23.trojanrideshare.domain.Place;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.ui.AppContainer;
import com.team23.trojanrideshare.ui.MainActivity;
import com.team23.trojanrideshare.ui.TrojanRideshareApp;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Person 1 - Black-box tests for the profile screen (FR-1.6, FR-1.8/FR-1.9, FR-1.10).
 *
 * Location in project: app/src/androidTest/java/com/team23/trojanrideshare/ui/profile/ProfileAndSharingTest.java
 *
 * Test 5 seeds two rides directly through the app's own AppContainer/RideService instead of
 * driving the "Create Ride" screen's location-autocomplete field - that field is FR-2 / Person
 * 2's territory, and driving it reliably needs a fake location-suggestion backend. Reaching
 * into AppContainer is still a real, supported entry point (TrojanRideshareApp.container()
 * exists specifically so code running inside the app process - which is exactly what an
 * instrumented test is - can get at the real services), and the ride is created the same way
 * RideService.createRide() always creates one; only the screen that normally calls it is
 * skipped.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class ProfileAndSharingTest {

    @Rule
    public ActivityScenarioRule<MainActivity> activityRule = new ActivityScenarioRule<>(MainActivity.class);

    @After
    public void signOutSoTheNextTestStartsClean() {
        ((TrojanRideshareApp) ApplicationProvider.getApplicationContext()).container().account.logOut();
    }

    private static String uniqueSuffix() {
        return String.valueOf(System.nanoTime());
    }

    private static String uniqueUscId() {
        return "9" + String.format("%09d", System.nanoTime() % 1_000_000_000L);
    }

    private void signUpViaUi(String fullName, String email, String uscId) {
        onView(withId(R.id.signUp)).perform(click());
        onView(withId(R.id.fullName)).perform(typeText(fullName), closeSoftKeyboard());
        onView(withId(R.id.email)).perform(typeText(email), closeSoftKeyboard());
        onView(withId(R.id.uscId)).perform(typeText(uscId), closeSoftKeyboard());
        onView(withId(R.id.password)).perform(typeText("password1"), closeSoftKeyboard());
        onView(withId(R.id.gradYear)).perform(typeText("2029"), closeSoftKeyboard());
        onView(withId(R.id.create)).perform(click());
        retryUntilPasses(() -> onView(withId(R.id.bottomNav)).check(matches(isDisplayed())), 15000);
    }

    /**
     * Sign-up, the organizer's public-rides list, etc. all go through Firebase (Firestore +
     * Firebase Auth), so they're real network calls, not instant local writes, and they finish
     * asynchronously and post their result back to the main thread. This retries the given
     * assertion for up to the given timeout instead of checking exactly once, so a test only
     * fails if the expected state never shows up - not just because the network call hadn't
     * finished yet.
     */
    private static void retryUntilPasses(Runnable assertion, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (true) {
            try {
                assertion.run();
                return;
            } catch (Throwable notYet) {
                if (System.currentTimeMillis() >= deadline) {
                    assertion.run(); // let the real failure surface
                    return;
                }
                try {
                    Thread.sleep(150);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    /**
     * Clicks the ride card and waits for the ride-detail screen to open - RETRYING THE CLICK
     * ITSELF, not just waiting, if it doesn't take. R.id.list is backed by a live Firestore
     * snapshot listener on this shared, real Firebase project: a logcat run showed the test
     * stuck on the search screen (toolbar still read "Find a Ride") for the FULL wait after
     * RecyclerViewActions.actionOnItem(...).click() with no error at all - the click wasn't
     * ambiguous or missing, it just never reached the card's own onClickListener. That matches
     * the list's adapter getting rebound by a fresh snapshot (vm.results().observe(...) ->
     * adapter.submit(list)) at the exact moment of the click gesture, which can swap the item
     * view out from under the touch. A wasted click once already on the detail screen is a
     * harmless no-op (the check below returns immediately), but a swallowed click needs a new
     * click, not more waiting - so each loop iteration re-attempts scrollTo + click, not just
     * the isDisplayed() check.
     */
    private void clickRideCardAndWaitForDetail(String title, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (true) {
            try {
                onView(withId(R.id.list)).perform(RecyclerViewActions.scrollTo(hasDescendant(withText(title))));
                onView(withId(R.id.list)).perform(
                        RecyclerViewActions.actionOnItem(hasDescendant(withText(title)), click()));
            } catch (Throwable listNotReadyYet) {
                // The list can be mid-refresh from a new snapshot; just try again below.
            }
            try {
                onView(withText("Ride Details")).check(matches(isDisplayed()));
                return;
            } catch (Throwable notYet) {
                if (System.currentTimeMillis() >= deadline) {
                    onView(withText("Ride Details")).check(matches(isDisplayed())); // let the real failure surface
                    return;
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    // --------------------------------------------------------------------------------
    // Test 4 - FR-1.6 / FR-1.8 / FR-1.9: the profile shows the registered email and USC ID
    // as plain, read-only text (there is no edit control for either - only the nickname has
    // one), so what's shown is exactly what was entered at sign-up and can't be changed here.
    // --------------------------------------------------------------------------------
    @Test
    public void profileShowsEmailAndUscIdAsReadOnly() {
        String suffix = uniqueSuffix();
        String email = "jordan." + suffix + "@usc.edu";
        String uscId = uniqueUscId();
        signUpViaUi("Jordan Lee", email, uscId);

        onView(withId(R.id.nav_profile)).perform(click());
        onView(withId(R.id.email)).check(matches(withText(email)));
        onView(withId(R.id.uscId)).check(matches(withText("USC ID " + uscId)));
    }

    // --------------------------------------------------------------------------------
    // Test 5 - FR-1.10: a ride the organizer switches "on" for their public profile shows up
    // on that profile for another signed-in user; a ride they never switched on does not.
    // --------------------------------------------------------------------------------
    @Test
    public void sharingARidePubliclyMakesItVisibleToAnotherUser() {
        AppContainer app = ((TrojanRideshareApp) ApplicationProvider.getApplicationContext()).container();
        String suffix = uniqueSuffix();
        signUpViaUi("Organizer One", "org1." + suffix + "@usc.edu", uniqueUscId());

        LocalDateTime now = app.clock.now();
        seedRide(app, "Shared airport run " + suffix, now.plusHours(5));
        seedRide(app, "Never shared run " + suffix, now.plusHours(6));

        // Share only the first ride, from the Profile screen's "Upcoming" tab (the default
        // tab), which is where the organizer's own rides and their public-profile switches
        // live (see ProfileFragment.renderRides()).
        onView(withId(R.id.nav_profile)).perform(click());
        // Scope to the SPECIFIC ride's card (R.id.card), not just "some ancestor that has
        // that descendant somewhere" - both cards share the same "rides" container as an
        // ancestor, and that shared container "has a descendant" with either card's title,
        // so isDescendantOfA(hasDescendant(title)) alone matches BOTH cards' switches
        // (AmbiguousViewMatcherException). Requiring the ancestor to also be the card
        // itself picks out only the one card with this title.
        onView(allOf(withId(R.id.publicSwitch),
                isDescendantOfA(allOf(withId(R.id.card), hasDescendant(withText("Shared airport run " + suffix))))))
                .perform(scrollTo(), click());

        // scrollTo(): with two seeded rides above it, logOut can sit below the fold here,
        // same reasoning as the other scrollTo() calls in this file.
        onView(withId(R.id.logOut)).perform(scrollTo(), click());
        // Scoped to the dialog's own button ID, not matched by text - "Log Out" is also the
        // text on the Profile screen's own logOut button sitting right behind this dialog, and
        // SignUpAndLoginTest's identical test hit a real bug from that ambiguity (the text
        // match could silently land on the wrong button if the dialog hadn't fully attached
        // yet). This test has passed so far, but only by luck of timing, not by being safe.
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());

        // Same navigation-timing reasoning as the scrollTo() above: give logOut's return to
        // the Welcome screen a moment to finish before signUpViaUi clicks R.id.signUp there.
        // Bumped to 30s (see the matching comment in SignUpAndLoginTest): a logcat run showed
        // this app's logOut-confirm click to the actual FirebaseAuth sign-out notification
        // taking over 15s by itself on this shared, ever-growing project.
        retryUntilPasses(() -> onView(withId(R.id.signUp)).check(matches(isDisplayed())), 30000);
        signUpViaUi("Viewer Two", "viewer2." + suffix + "@usc.edu", uniqueUscId());
        // Lands on the Search tab (the default tab after signing in), which already loads all
        // open rides - including the two just seeded - with no typing required.
        clickRideCardAndWaitForDetail("Shared airport run " + suffix, 30000);
        onView(withId(R.id.organizer)).perform(scrollTo(), click());

        retryUntilPasses(() -> {
            onView(withId(R.id.rides)).check(matches(hasDescendant(withText("Shared airport run " + suffix))));
            onView(withId(R.id.rides)).check(matches(not(hasDescendant(withText("Never shared run " + suffix)))));
        }, 15000);
    }

    /**
     * Creates a ride the same way RideService.createRide() always does, through the real,
     * already-signed-in AppContainer - see the class comment for why this test bypasses the
     * "Create Ride" screen itself. Blocks until the async callback reports success or failure,
     * since this is a real Firestore write, not an instant local one.
     */
    private void seedRide(AppContainer app, String title, LocalDateTime departure) {
        Place usc = new Place("p-usc-" + title, "USC", "University Park, Los Angeles, CA", 34.0224, -118.2851);
        Place lax = new Place("p-lax-" + title, "LAX", "1 World Way, Los Angeles, CA", 33.9416, -118.4085);
        Ride form = new Ride();
        form.setTitle(title);
        form.setOrigin(usc);
        form.setDestination(lax);
        form.setDeparture(departure);
        form.setMaxRiders(3);
        form.setExpectedCostCents(1500);
        form.setNote("");

        CountDownLatch latch = new CountDownLatch(1);
        AppException[] errorHolder = new AppException[1];
        app.rides.createRide(form, new Callback<Ride>() {
            @Override
            public void onSuccess(Ride r) {
                latch.countDown();
            }

            @Override
            public void onError(AppException e) {
                errorHolder[0] = e;
                latch.countDown();
            }
        });
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new AssertionError("timed out seeding ride \"" + title + "\"");
            }
        } catch (InterruptedException ie) {
            throw new AssertionError(ie);
        }
        if (errorHolder[0] != null) {
            throw new AssertionError("failed to seed ride \"" + title + "\": " + errorHolder[0].getMessage());
        }
    }
}
