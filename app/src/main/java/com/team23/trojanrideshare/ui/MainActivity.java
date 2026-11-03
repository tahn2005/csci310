package com.team23.trojanrideshare.ui;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.team23.trojanrideshare.R;
import com.team23.trojanrideshare.databinding.ActivityMainBinding;
import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.Callback;
import com.team23.trojanrideshare.domain.User;
import com.team23.trojanrideshare.ui.auth.LogInFragment;
import com.team23.trojanrideshare.ui.history.HistoryFragment;
import com.team23.trojanrideshare.ui.myrides.MyRidesFragment;
import com.team23.trojanrideshare.ui.profile.ProfileFragment;
import com.team23.trojanrideshare.ui.search.RideSearchFragment;

/**
 * Navigation Shell: hosts the bottom tabs (Search, My Rides, History, Profile) and sends a
 * signed-out user to Log In.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding b;
    private AppContainer app;
    /** True while the signed-in tabs are shown (not on Log In / Sign Up). */
    private boolean tabsShown;
    private boolean keyboardShown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        app = ((TrojanRideshareApp) getApplication()).container();
        // After Android kills the process, Firebase still remembers the login but the session is
        // gone, so restored screens would load with no user. Start fresh instead and let
        // restoreSession() bring it back. A rotation while signed out keeps the Log In / Sign Up
        // screen and whatever the user typed.
        boolean processRestarted = savedInstanceState != null
                && !app.session.isSignedIn() && app.account.hasSavedLogin();
        if (processRestarted) savedInstanceState = null;
        super.onCreate(savedInstanceState);
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setSupportActionBar(b.toolbar);

        b.bottomNav.setOnItemSelectedListener(item -> {
            showTab(item.getItemId());
            return true;
        });
        b.bottomNav.setOnItemReselectedListener(item -> showTab(item.getItemId()));
        getSupportFragmentManager().addOnBackStackChangedListener(this::updateUpButton);
        b.splashRetry.setOnClickListener(v -> restoreSession());
        // Hide the bottom tabs while typing so they don't sit on top of the keyboard.
        b.getRoot().getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(b.getRoot());
            boolean shown = insets != null && insets.isVisible(WindowInsetsCompat.Type.ime());
            if (shown != keyboardShown) {
                keyboardShown = shown;
                updateBottomNav();
            }
        });

        if (savedInstanceState != null) {
            // Configuration change: the screens were restored as they were.
            b.splash.setVisibility(View.GONE);
            tabsShown = app.session.isSignedIn();
            updateBottomNav();
            updateUpButton();
        } else {
            restoreSession();
        }
    }

    private void restoreSession() {
        b.splash.setVisibility(View.VISIBLE);
        b.splashRetry.setVisibility(View.GONE);
        b.splashText.setText("Connecting…");
        app.account.restoreSession(new Callback<User>() {
            @Override
            public void onSuccess(User u) {
                if (u == null) showLogIn();
                else onSignedIn();
            }

            @Override
            public void onError(AppException e) {
                b.splashText.setText(e.getMessage());
                b.splashRetry.setVisibility(View.VISIBLE);
                if (!app.session.isSignedIn() && e.getCode() == com.team23.trojanrideshare.domain.ErrorCode.NOT_FOUND) {
                    showLogIn();
                }
            }
        });
    }

    private void clearBackStack() {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.getBackStackEntryCount() > 0) {
            fm.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }
    }

    public void showLogIn() {
        b.splash.setVisibility(View.GONE);
        tabsShown = false;
        updateBottomNav();
        clearBackStack();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, new LogInFragment())
                .commit();
    }

    /** Called after sign-up, log-in, or a restored session. */
    public void onSignedIn() {
        b.splash.setVisibility(View.GONE);
        tabsShown = true;
        updateBottomNav();
        if (b.bottomNav.getSelectedItemId() == R.id.nav_search) {
            showTab(R.id.nav_search);
        } else {
            b.bottomNav.setSelectedItemId(R.id.nav_search);
        }
    }

    public void logOut() {
        app.account.logOut();
        showLogIn();
    }

    private void showTab(int itemId) {
        Fragment f;
        if (itemId == R.id.nav_my_rides) f = new MyRidesFragment();
        else if (itemId == R.id.nav_history) f = new HistoryFragment();
        else if (itemId == R.id.nav_profile) f = ProfileFragment.forMe();
        else f = new RideSearchFragment();
        clearBackStack();
        getSupportFragmentManager().beginTransaction().replace(R.id.container, f).commit();
    }

    /** Opens a screen on top of the current one; Back returns to it. */
    public void push(Fragment f) {
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                        android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.container, f)
                .addToBackStack(null)
                .commit();
    }

    /** Replaces the current pushed screen (e.g. Create Ride -> the new ride's details). */
    public void replaceTop(Fragment f) {
        getSupportFragmentManager().popBackStack();
        push(f);
    }

    public void back() {
        getSupportFragmentManager().popBackStack();
    }

    public void switchToTab(int itemId) {
        b.bottomNav.setSelectedItemId(itemId);
    }

    public void setScreenTitle(String title) {
        if (getSupportActionBar() != null) getSupportActionBar().setTitle(title);
    }

    public View bottomNavIfVisible() {
        return b.bottomNav.getVisibility() == View.VISIBLE ? b.bottomNav : null;
    }

    private void updateBottomNav() {
        b.bottomNav.setVisibility(tabsShown && !keyboardShown ? View.VISIBLE : View.GONE);
    }

    private void updateUpButton() {
        boolean canGoBack = getSupportFragmentManager().getBackStackEntryCount() > 0;
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(canGoBack);
    }

    @Override
    public boolean onSupportNavigateUp() {
        getSupportFragmentManager().popBackStack();
        return true;
    }
}
