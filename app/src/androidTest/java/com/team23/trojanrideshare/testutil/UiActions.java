package com.team23.trojanrideshare.testutil;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static org.hamcrest.Matchers.allOf;

import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;

import androidx.core.widget.NestedScrollView;
import androidx.test.espresso.Root;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.ViewAssertion;
import androidx.test.espresso.matcher.BoundedMatcher;

import com.google.android.material.textfield.TextInputLayout;

import org.hamcrest.Description;
import org.hamcrest.Matcher;

/** Small Espresso helpers shared by the UI tests. */
public final class UiActions {

    private UiActions() {
    }

    /**
     * Retries {@code assertion} for up to 10 s. The test database answers on a background thread
     * that Espresso doesn't track, so screens that load data need a short wait.
     */
    public static void waitFor(Matcher<View> view, ViewAssertion assertion) {
        waitFor(view, null, assertion);
    }

    public static void waitFor(Matcher<View> view, Matcher<Root> root, ViewAssertion assertion) {
        long deadline = SystemClock.uptimeMillis() + 10_000;
        while (true) {
            try {
                if (root == null) onView(view).check(assertion);
                else onView(view).inRoot(root).check(assertion);
                return;
            } catch (Throwable t) {
                if (SystemClock.uptimeMillis() > deadline) throw t;
                SystemClock.sleep(150);
            }
        }
    }

    /** Espresso's scrollTo() only supports ScrollView; Ride Details uses a NestedScrollView. */
    public static ViewAction nestedScrollTo() {
        return new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return allOf(isDescendantOfA(isAssignableFrom(NestedScrollView.class)));
            }

            @Override
            public String getDescription() {
                return "scroll to view inside a NestedScrollView";
            }

            @Override
            public void perform(UiController ui, View view) {
                view.requestRectangleOnScreen(new Rect(0, 0, view.getWidth(), view.getHeight()), true);
                ui.loopMainThreadUntilIdle();
            }
        };
    }

    /** Matches a TextInputLayout showing exactly this error message. */
    public static Matcher<View> hasInputError(String expected) {
        return new BoundedMatcher<View, TextInputLayout>(TextInputLayout.class) {
            private CharSequence seen;

            @Override
            public void describeTo(Description d) {
                d.appendText("TextInputLayout with error: ").appendValue(expected)
                        .appendText(" (it showed ").appendValue(seen).appendText(")");
            }

            @Override
            protected boolean matchesSafely(TextInputLayout layout) {
                seen = layout.getError();
                return seen != null && expected.contentEquals(seen);
            }
        };
    }

    /** Matches a TextInputLayout with no error message. */
    public static Matcher<View> hasNoInputError() {
        return new BoundedMatcher<View, TextInputLayout>(TextInputLayout.class) {
            @Override
            public void describeTo(Description d) {
                d.appendText("TextInputLayout without an error");
            }

            @Override
            protected boolean matchesSafely(TextInputLayout layout) {
                return layout.getError() == null || layout.getError().length() == 0;
            }
        };
    }

    public static Matcher<View> shown(Matcher<View> m) {
        return allOf(m, isDisplayed());
    }
}
