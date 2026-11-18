package com.team23.trojanrideshare.ui.auth;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.util.Log;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.google.android.material.textfield.TextInputLayout;
import com.team23.trojanrideshare.R;
import com.team23.trojanrideshare.ui.MainActivity;
import com.team23.trojanrideshare.ui.TrojanRideshareApp;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Person 1 - Black-box tests for sign-up and login (FR-1.1 - FR-1.5, FR-1.7).
 *
 * Location in project: app/src/androidTest/java/com/team23/trojanrideshare/ui/auth/SignUpAndLoginTest.java
 *
 * Each test signs up its own, uniquely-named account (see uniqueSuffix/uniqueUscId below) so
 * repeated runs never collide with data an earlier run left in the on-device database, and
 * each test logs itself out in @After so the next test (in this file or ProfileAndSharingTest)
 * always starts from a signed-out app, the same way a fresh install would.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class SignUpAndLoginTest {

    @Rule
    public ActivityScenarioRule<MainActivity> activityRule = new ActivityScenarioRule<>(MainActivity.class);

    @After
    public void signOutSoTheNextTestStartsClean() {
        ((TrojanRideshareApp) ApplicationProvider.getApplicationContext()).container().account.logOut();
    }

    static String uniqueSuffix() {
        return String.valueOf(System.nanoTime());
    }

    /** A fresh, valid-looking 10-digit USC ID that won't collide with another test run. */
    static String uniqueUscId() {
        return "9" + String.format("%09d", System.nanoTime() % 1_000_000_000L);
    }

    private void fillAndSubmitSignUp(String fullName, String email, String uscId, String password, String gradYear) {
        onView(withId(R.id.fullName)).perform(typeText(fullName), closeSoftKeyboard());
        onView(withId(R.id.email)).perform(typeText(email), closeSoftKeyboard());
        onView(withId(R.id.uscId)).perform(typeText(uscId), closeSoftKeyboard());
        onView(withId(R.id.password)).perform(typeText(password), closeSoftKeyboard());
        onView(withId(R.id.gradYear)).perform(typeText(gradYear), closeSoftKeyboard());
        onView(withId(R.id.create)).perform(click());
    }

    /**
     * Sign-up/login both finish asynchronously and post their result back to the main thread,
     * so the UI doesn't update the instant the click() call returns. This polls for up to the
     * given timeout instead of checking exactly once, so the test only fails if the view never
     * shows up - not just because the background work hadn't finished yet. The timeout is
     * generous (15s) because this project talks to Firebase, not just a local file, so a
     * sign-up/login round trip is a real network call, not an instant local write.
     */
    /**
     * Waits for a Snackbar with the given text to actually disappear. BaseFragment.say()
     * shows nickname-save confirmation as Snackbar.make(..., LENGTH_LONG).setAnchorView(the
     * bottom nav) - anchored right above the bottom nav bar, which is exactly where logOut
     * sits once scrolled into view. LENGTH_LONG holds for ~2.75s, long enough to still be
     * covering (and intercepting taps on) the logOut button when the test clicks it right
     * after the nickname save - found by watching the emulator directly, not from logcat.
     */
    private static void waitUntilSnackbarGone(String text, long timeoutMs) {
        long start = System.currentTimeMillis();
        long deadline = start + timeoutMs;
        while (true) {
            try {
                onView(withText(text)).check(doesNotExist());
                Log.d("WaitDebug", "waitUntilSnackbarGone(\"" + text + "\") confirmed gone after "
                        + (System.currentTimeMillis() - start) + "ms");
                return;
            } catch (Throwable stillThere) {
                if (System.currentTimeMillis() >= deadline) {
                    Log.d("WaitDebug", "waitUntilSnackbarGone(\"" + text + "\") gave up after "
                            + (System.currentTimeMillis() - start) + "ms - proceeding anyway");
                    return;
                }
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    /**
     * Waits for a dialog's own positive button (android.R.id.button1) to be displayed,
     * scoped to the currently-showing dialog window via inRoot(isDialog()). We stopped
     * matching the confirm dialog's button by its text ("Log Out") because that exact text
     * also exists on the Profile screen's own logOut button sitting right behind it - if the
     * confirm dialog hasn't fully attached yet when that match is evaluated, withText("Log
     * Out") can resolve to the WRONG one (the Profile button again, re-opening a second,
     * never-confirmed dialog) instead of throwing an ambiguous-match error, which is exactly
     * what manual testing on the emulator exposed: the real app works fine by hand, so the
     * bug was in which button the test was actually hitting, not in the app.
     */
    private static void waitUntilDialogButtonDisplayed(long timeoutMs) {
        long start = System.currentTimeMillis();
        long deadline = start + timeoutMs;
        while (true) {
            try {
                onView(withId(android.R.id.button1)).inRoot(isDialog()).check(matches(isDisplayed()));
                Log.d("WaitDebug", "waitUntilDialogButtonDisplayed succeeded after "
                        + (System.currentTimeMillis() - start) + "ms");
                return;
            } catch (Throwable notYet) {
                Log.d("WaitDebug", "waitUntilDialogButtonDisplayed not yet after "
                        + (System.currentTimeMillis() - start) + "ms: "
                        + notYet.getClass().getSimpleName() + " - " + notYet.getMessage());
                if (System.currentTimeMillis() >= deadline) {
                    return; // let the click below throw its own, more specific failure
                }
                try {
                    Thread.sleep(150);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    private static void waitUntilDisplayed(int viewId, long timeoutMs) {
        long start = System.currentTimeMillis();
        long deadline = start + timeoutMs;
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                onView(withId(viewId)).check(matches(isDisplayed()));
                Log.d("WaitDebug", "waitUntilDisplayed(" + viewId + ") succeeded after "
                        + (System.currentTimeMillis() - start) + "ms");
                return;
            } catch (Throwable notYet) {
                // Diagnostic only - logs the REAL exception type on every failed attempt instead
                // of silently swallowing it, so logcat shows whether the view is genuinely
                // missing (NoMatchingViewException) the whole time or something else (e.g.
                // AmbiguousViewMatcherException, meaning a stale view from the old screen is
                // still around) for part of the wait. Every ~10th attempt also probes a few
                // known IDs from OTHER screens, so logcat shows which screen is actually on
                // display during the stuck window (still on Profile? stuck on the splash
                // "Connecting..." screen? something else?) instead of just "signUp isn't there".
                String extra = (attempt % 10 == 1) ? " | screen probe: " + currentScreenProbe() : "";
                Log.d("WaitDebug", "waitUntilDisplayed(" + viewId + ") not yet after "
                        + (System.currentTimeMillis() - start) + "ms: "
                        + notYet.getClass().getSimpleName() + " - " + notYet.getMessage() + extra);
                if (System.currentTimeMillis() >= deadline) {
                    onView(withId(viewId)).check(matches(isDisplayed())); // let the real failure surface
                    return;
                }
                try {
                    Thread.sleep(150);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    /** Checks a handful of known IDs from other screens to see which screen is really showing. */
    private static String currentScreenProbe() {
        StringBuilder sb = new StringBuilder();
        probeOne(sb, "splash", R.id.splash);
        probeOne(sb, "logOut", R.id.logOut);
        probeOne(sb, "bottomNav", R.id.bottomNav);
        probeOne(sb, "displayName", R.id.displayName);
        probeOne(sb, "email", R.id.email);
        return sb.toString();
    }

    private static void probeOne(StringBuilder sb, String label, int id) {
        try {
            onView(withId(id)).check(matches(isDisplayed()));
            sb.append(label).append("=visible ");
        } catch (Throwable t) {
            sb.append(label).append("=no ");
        }
    }

    /**
     * Same reasoning as waitUntilDisplayed, but for a view whose TEXT needs to catch up, not
     * just its presence - saving the nickname writes to Firestore and only updates the
     * displayName TextView once that write's callback comes back, so checking withText() once,
     * immediately after clicking Save, can still see the pre-save name for a moment.
     */
    private static void waitUntilTextEquals(int viewId, String expectedText, long timeoutMs) {
        long start = System.currentTimeMillis();
        long deadline = start + timeoutMs;
        while (true) {
            try {
                onView(withId(viewId)).check(matches(withText(expectedText)));
                Log.d("WaitDebug", "waitUntilTextEquals(" + viewId + ", \"" + expectedText
                        + "\") succeeded after " + (System.currentTimeMillis() - start) + "ms");
                return;
            } catch (Throwable notYet) {
                Log.d("WaitDebug", "waitUntilTextEquals(" + viewId + ", \"" + expectedText
                        + "\") not yet after " + (System.currentTimeMillis() - start) + "ms: "
                        + notYet.getClass().getSimpleName() + " - " + notYet.getMessage());
                if (System.currentTimeMillis() >= deadline) {
                    onView(withId(viewId)).check(matches(withText(expectedText))); // let the real failure surface
                    return;
                }
                try {
                    Thread.sleep(150);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    /** See the class comment on hasErrorText vs. TextInputLayout.setError() for why this exists. */
    private static Matcher<View> hasTextInputLayoutErrorText(String expectedError) {
        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("TextInputLayout with error text: \"" + expectedError + "\"");
            }

            @Override
            protected boolean matchesSafely(View view) {
                if (!(view instanceof TextInputLayout)) return false;
                CharSequence error = ((TextInputLayout) view).getError();
                return error != null && expectedError.equals(error.toString());
            }
        };
    }

    // --------------------------------------------------------------------------------
    // Test 1 - FR-1.1 / FR-1.3: a correctly filled sign-up form creates the account and signs
    // the user straight in, landing on the app's main tabs.
    // --------------------------------------------------------------------------------
    @Test
    public void signUpWithValidDataSignsIn() {
        onView(withId(R.id.signUp)).perform(click());
        String suffix = uniqueSuffix();
        fillAndSubmitSignUp("Jane Trojan", "jane." + suffix + "@usc.edu", uniqueUscId(), "password1", "2029");

        waitUntilDisplayed(R.id.bottomNav, 15000);
    }

    // --------------------------------------------------------------------------------
    // Test 2 - FR-1.2: a sign-up with an invalid field (here, an email that isn't @usc.edu)
    // is blocked - the error shows on that field, the form stays up, and the user never
    // reaches the main tabs.
    // --------------------------------------------------------------------------------
    @Test
    public void signUpWithNonUscEmailIsBlocked() {
        onView(withId(R.id.signUp)).perform(click());
        String suffix = uniqueSuffix();
        fillAndSubmitSignUp("Jane Trojan", "jane." + suffix + "@gmail.com", uniqueUscId(), "password1", "2029");

        onView(withId(R.id.emailLayout))
                .check(matches(hasTextInputLayoutErrorText("A valid USC email address ending in @usc.edu is required.")));
        onView(withId(R.id.create)).check(matches(isDisplayed()));
    }

    // --------------------------------------------------------------------------------
    // Test 3 - FR-1.4 / FR-1.5 / FR-1.7: logging in with the account's saved credentials
    // works, a nickname change is saved, and logging out and back in again still shows the
    // new nickname (it was actually persisted, not just held in memory).
    // --------------------------------------------------------------------------------
    @Test
    public void loginNicknameChangeLogoutAndReloginPersistNickname() {
        String suffix = uniqueSuffix();
        String email = "nick." + suffix + "@usc.edu";
        onView(withId(R.id.signUp)).perform(click());
        fillAndSubmitSignUp("Nick Trojan", email, uniqueUscId(), "password1", "2029");
        waitUntilDisplayed(R.id.bottomNav, 15000);

        onView(withId(R.id.nav_profile)).perform(click());
        onView(withId(R.id.editNickname)).perform(click());
        onView(withId(R.id.nickname)).perform(typeText("Nicky"), closeSoftKeyboard());
        onView(withText("Save")).perform(click());
        waitUntilTextEquals(R.id.displayName, "Nicky", 15000);

        // The "Nickname updated." Snackbar is anchored right above the bottom nav (see
        // waitUntilSnackbarGone's comment) and physically overlaps logOut once it's scrolled
        // into view - clicking through it was the real bug, not anything about the dialog or
        // Firebase. Wait for it to actually clear before touching logOut at all.
        waitUntilSnackbarGone("Nickname updated.", 6000);

        // scrollTo() first: right after the nickname-save dialog closes, the layout can
        // still be mid-settle, and Espresso's click() requires the target to be >= 90%
        // visible - scrolling it fully into view resolves that without needing animations
        // disabled on every machine this test runs on.
        onView(withId(R.id.logOut)).perform(scrollTo(), click());

        // Target the confirm dialog's positive button by its dialog-scoped button ID, not by
        // matching the text "Log Out" - that text also exists on the Profile screen's own
        // logOut button sitting right behind the dialog, and matching by text risked silently
        // clicking the wrong one (re-opening a second, unconfirmed dialog instead of confirming
        // the one already open) if the dialog hadn't fully attached yet when the match ran.
        waitUntilDialogButtonDisplayed(10000);
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());

        // R.id.email is reused by the Welcome screen's login EditText AND by the Profile
        // screen's read-only email label - logOut() navigating back to Welcome isn't
        // instant, so querying R.id.email immediately can still match the outgoing Profile
        // screen's label instead of the EditText we actually want (its visible text looked
        // right because it genuinely was this account's own email, just on the wrong view -
        // a plain TextView, not editable, so typeText() failed on it). Wait for a view that
        // only exists on the logged-out Welcome screen before typing.
        // Now that the confirm dialog's button is targeted unambiguously (see
        // waitUntilDialogButtonDisplayed above), this should resolve in well under a second,
        // same as ProfileAndSharingTest's logout. Left at 45s for one more run so a wrong guess
        // still fails loudly (with WaitDebug + screen-probe logs) instead of silently retrying
        // forever; once this is confirmed fast and reliable, shrink it back down to ~15s.
        waitUntilDisplayed(R.id.signUp, 45000);
        onView(withId(R.id.email)).perform(typeText(email), closeSoftKeyboard());
        onView(withId(R.id.password)).perform(typeText("password1"), closeSoftKeyboard());
        onView(withId(R.id.logIn)).perform(click());
        waitUntilDisplayed(R.id.bottomNav, 15000);

        onView(withId(R.id.nav_profile)).perform(click());
        waitUntilTextEquals(R.id.displayName, "Nicky", 15000);
    }
}
