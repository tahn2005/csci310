package com.team23.trojanrideshare.testutil;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.Callback;
import com.team23.trojanrideshare.domain.ErrorCode;
import com.team23.trojanrideshare.domain.Place;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.domain.User;
import com.team23.trojanrideshare.gateway.LocationGateway;
import com.team23.trojanrideshare.ui.AppContainer;
import com.team23.trojanrideshare.ui.TrojanRideshareApp;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Shared setup for the Espresso tests: a fresh on-device database for every test (never the
 * shared Firebase project), fixed location suggestions, and helpers that arrange data through
 * the app's own services before the screen under test is opened.
 */
public final class TestBackend {

    public static final String PASSWORD = "testpass123";

    public static final Place USC = new Place("t-usc", "University of Southern California",
            "Los Angeles, CA", 34.0224, -118.2851);
    public static final Place LAX = new Place("t-lax", "Los Angeles International Airport",
            "Los Angeles, CA", 33.9416, -118.4085);
    public static final Place SANTA_MONICA = new Place("t-sm", "Santa Monica Pier",
            "Santa Monica, CA", 34.0094, -118.4973);

    private static final List<Place> PLACES = Arrays.asList(USC, LAX, SANTA_MONICA);

    private TestBackend() {
    }

    /** Replaces the app's backend with an empty test database. Call before launching MainActivity. */
    public static AppContainer install() {
        Context ctx = ApplicationProvider.getApplicationContext();
        ctx.getSharedPreferences("trojanrideshare-test", Context.MODE_PRIVATE).edit().clear().commit();
        File db = new File(ctx.getFilesDir(), "espresso-test-db.json");
        //noinspection ResultOfMethodCallIgnored
        db.delete();
        AppContainer c = new AppContainer(ctx, db, new FixedPlaces());
        ((TrojanRideshareApp) ctx).replaceContainer(c);
        return c;
    }

    /** Runs an async service call and waits for its result; fails the test on an error. */
    public static <T> T await(Consumer<Callback<T>> call) {
        CountDownLatch done = new CountDownLatch(1);
        Object[] out = new Object[1];
        AppException[] err = new AppException[1];
        call.accept(new Callback<T>() {
            @Override
            public void onSuccess(T result) {
                out[0] = result;
                done.countDown();
            }

            @Override
            public void onError(AppException e) {
                err[0] = e;
                done.countDown();
            }
        });
        try {
            if (!done.await(10, TimeUnit.SECONDS)) throw new AssertionError("Service call timed out");
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
        if (err[0] != null) throw new AssertionError("Setup failed: " + err[0].getMessage(), err[0]);
        @SuppressWarnings("unchecked") T result = (T) out[0];
        return result;
    }

    /** Signs up a student (which also logs them in). */
    public static User signUp(AppContainer c, String fullName, String email, String uscId) {
        User form = new User(fullName, "", email, uscId, 2027);
        return await(cb -> c.account.signUp(form, PASSWORD, cb));
    }

    public static User logIn(AppContainer c, String email) {
        c.account.logOut();
        return await(cb -> c.account.logIn(email, PASSWORD, cb));
    }

    /** Posts a ride as the signed-in user. */
    public static Ride postRide(AppContainer c, String title, Place from, Place to,
                                LocalDateTime departure, int maxRiders) {
        Ride form = new Ride();
        form.setTitle(title);
        form.setOrigin(from);
        form.setDestination(to);
        form.setDeparture(departure);
        form.setMaxRiders(maxRiders);
        form.setExpectedCostCents(4000);
        return await(cb -> c.rides.createRide(form, cb));
    }

    /** Suggestions whose name or short name contains the typed text, answered right away. */
    static final class FixedPlaces implements LocationGateway {
        private static final String[] SHORT_NAMES = {"usc", "lax", "santa monica"};

        @Override
        public void suggest(String query, Callback<List<Place>> cb) {
            String q = query.trim().toLowerCase(Locale.US);
            List<Place> out = new ArrayList<>();
            for (int i = 0; i < PLACES.size(); i++) {
                Place p = PLACES.get(i);
                if (p.getName().toLowerCase(Locale.US).contains(q) || SHORT_NAMES[i].contains(q)) out.add(p);
            }
            cb.onSuccess(out);
        }

        @Override
        public void getPlace(String placeId, Callback<Place> cb) {
            for (Place p : PLACES) {
                if (p.getPlaceId().equals(placeId)) {
                    cb.onSuccess(p);
                    return;
                }
            }
            cb.onError(new AppException(ErrorCode.NOT_FOUND, "Location not found."));
        }
    }
}
