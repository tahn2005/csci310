package com.team23.trojanrideshare.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.MemoryCacheSettings;
import com.team23.trojanrideshare.BuildConfig;
import com.team23.trojanrideshare.data.firestore.FirestoreJoinRequestRepository;
import com.team23.trojanrideshare.data.firestore.FirestoreRideRepository;
import com.team23.trojanrideshare.data.firestore.FirestoreUserRepository;
import com.team23.trojanrideshare.data.firestore.NetworkMonitor;
import com.team23.trojanrideshare.domain.util.DeviceClock;
import com.team23.trojanrideshare.domain.util.SafeLogger;
import com.team23.trojanrideshare.gateway.AuthGateway;
import com.team23.trojanrideshare.gateway.FirebaseAuthGateway;
import com.team23.trojanrideshare.gateway.GooglePlacesGateway;
import com.team23.trojanrideshare.gateway.LocationGateway;
import com.team23.trojanrideshare.gateway.OpenStreetMapGateway;
import com.team23.trojanrideshare.service.AccountService;
import com.team23.trojanrideshare.service.CostSplitService;
import com.team23.trojanrideshare.service.JoinRequestService;
import com.team23.trojanrideshare.service.RideHistoryService;
import com.team23.trojanrideshare.service.RideSearchEngine;
import com.team23.trojanrideshare.service.RideService;
import com.team23.trojanrideshare.service.Session;

import java.time.Duration;

/**
 * Creates the repositories, gateways, and services once and hands the services to the
 * ViewModels. This is the only class that knows which backend implementations are used.
 */
public class AppContainer {

    private static final String DEMO_PROJECT_ID = "demo-trojanrideshare";
    private static final String PREFS = "trojanrideshare";
    private static final String KEY_CLOCK_OFFSET = "clockOffsetMinutes";

    public final AccountService account;
    public final RideService rides;
    public final RideSearchEngine search;
    public final JoinRequestService joins;
    public final CostSplitService costs;
    public final RideHistoryService history;

    public final DeviceClock clock = new DeviceClock();
    public final Session session = new Session();
    /** Human-readable description of the backend, shown on the Profile page. */
    public final String backendLabel;
    public final String locationLabel;

    private final SharedPreferences prefs;

    public AppContainer(Context context) {
        SafeLogger.setSink(msg -> Log.d("TrojanRideshare", msg));
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        clock.setOffset(Duration.ofMinutes(prefs.getLong(KEY_CLOCK_OFFSET, 0)));

        FirebaseApp app = firebaseApp(context);
        FirebaseFirestore db = FirebaseFirestore.getInstance(app);
        FirebaseAuth firebaseAuth = FirebaseAuth.getInstance(app);
        if (BuildConfig.USE_FIREBASE_EMULATOR) {
            db.useEmulator(BuildConfig.FIREBASE_EMULATOR_HOST, 8080);
            firebaseAuth.useEmulator(BuildConfig.FIREBASE_EMULATOR_HOST, 9099);
            backendLabel = "Firebase Emulator (" + BuildConfig.FIREBASE_EMULATOR_HOST + ")";
        } else {
            backendLabel = "Firebase project " + app.getOptions().getProjectId();
        }
        // Screens always read fresh data from the server (refresh-based reads).
        db.setFirestoreSettings(new FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build());

        NetworkMonitor network = new NetworkMonitor(context);
        FirestoreUserRepository userRepo = new FirestoreUserRepository(db, network);
        FirestoreRideRepository rideRepo = new FirestoreRideRepository(db, network, clock);
        FirestoreJoinRequestRepository requestRepo = new FirestoreJoinRequestRepository(db);
        AuthGateway auth = new FirebaseAuthGateway(firebaseAuth);

        LocationGateway places;
        if (!BuildConfig.PLACES_API_KEY.isEmpty()) {
            places = new GooglePlacesGateway(context, BuildConfig.PLACES_API_KEY);
            locationLabel = "Google Places";
        } else {
            places = new OpenStreetMapGateway();
            locationLabel = "OpenStreetMap (Photon)";
        }

        account = new AccountService(auth, userRepo, clock, session);
        rides = new RideService(rideRepo, places, clock, session);
        search = new RideSearchEngine(rideRepo, clock, session);
        joins = new JoinRequestService(rideRepo, requestRepo, clock, session);
        costs = new CostSplitService(rideRepo, clock, session);
        history = new RideHistoryService(rideRepo, clock, session);
    }

    /** Uses google-services.json when present, otherwise a demo project for the local emulator. */
    private static FirebaseApp firebaseApp(Context context) {
        if (!FirebaseApp.getApps(context).isEmpty()) return FirebaseApp.getInstance();
        FirebaseOptions options = new FirebaseOptions.Builder()
                .setProjectId(DEMO_PROJECT_ID)
                .setApplicationId("1:000000000000:android:0000000000000000")
                .setApiKey("demo-api-key-for-local-emulator")
                .build();
        return FirebaseApp.initializeApp(context, options);
    }

    /** Demo tool: move the app clock forward so time-based rules can be shown quickly. */
    public void setClockOffset(Duration offset) {
        clock.setOffset(offset);
        prefs.edit().putLong(KEY_CLOCK_OFFSET, offset.toMinutes()).apply();
    }
}
