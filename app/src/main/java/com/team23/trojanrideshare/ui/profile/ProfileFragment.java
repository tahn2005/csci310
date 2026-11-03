package com.team23.trojanrideshare.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.team23.trojanrideshare.BuildConfig;
import com.team23.trojanrideshare.R;
import com.team23.trojanrideshare.databinding.DialogNicknameBinding;
import com.team23.trojanrideshare.databinding.FragmentProfileBinding;
import com.team23.trojanrideshare.databinding.ItemRideBinding;
import com.team23.trojanrideshare.domain.CostShare;
import com.team23.trojanrideshare.domain.HistoryEntry;
import com.team23.trojanrideshare.domain.NonPaymentFlag;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.domain.User;
import com.team23.trojanrideshare.domain.util.Money;
import com.team23.trojanrideshare.ui.common.BaseFragment;
import com.team23.trojanrideshare.ui.common.RideViews;
import com.team23.trojanrideshare.ui.common.UiFormat;
import com.team23.trojanrideshare.ui.ride.RideDetailFragment;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * FR-1.6 – FR-1.10. The signed-in user's own profile shows private upcoming and completed
 * rides with a public toggle per ride; anyone else's profile shows only their public rides.
 */
public class ProfileFragment extends BaseFragment {

    private static final String ARG_UID = "uid";
    private static final int TAB_UPCOMING = 0;
    private static final int TAB_COMPLETED = 1;

    private FragmentProfileBinding b;
    private ProfileViewModel vm;

    public static ProfileFragment forMe() {
        return new ProfileFragment();
    }

    public static ProfileFragment forUser(String uid) {
        ProfileFragment f = new ProfileFragment();
        Bundle args = new Bundle();
        args.putString(ARG_UID, uid);
        f.setArguments(args);
        return f;
    }

    private String profileUid() {
        return getArguments() == null ? null : getArguments().getString(ARG_UID);
    }

    @Override
    protected String title() {
        return vm != null && !vm.isMe() ? "Student Profile" : "My Profile";
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentProfileBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        vm = new ViewModelProvider(this).get(ProfileViewModel.class);
        observeCommon(vm, b.progress);
        vm.busy().observe(getViewLifecycleOwner(), busy -> {
            if (!Boolean.TRUE.equals(busy)) b.refresh.setRefreshing(false);
        });
        vm.user().observe(getViewLifecycleOwner(), this::showUser);
        vm.upcoming().observe(getViewLifecycleOwner(), x -> renderRides());
        vm.completed().observe(getViewLifecycleOwner(), x -> renderRides());
        vm.publicRides().observe(getViewLifecycleOwner(), x -> renderRides());
        vm.flags().observe(getViewLifecycleOwner(), this::showFlags);
        b.tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                renderRides();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
        b.refresh.setOnRefreshListener(vm::reload);
        b.editNickname.setOnClickListener(v -> editNickname());
        b.logOut.setOnClickListener(v -> confirm("Log out?", "You'll need your USC email and password to log back in.",
                "Log Out", () -> main().logOut()));
        b.plusHour.setOnClickListener(v -> shift(Duration.ofHours(1)));
        b.plusDay.setOnClickListener(v -> shift(Duration.ofDays(1)));
        b.plusMonth.setOnClickListener(v -> shift(Duration.ofDays(30)));
        b.resetClock.setOnClickListener(v -> shift(null));
        b.backend.setText(vm.backendLabel());
    }

    @Override
    public void onResume() {
        vm.load(profileUid());
        super.onResume();
        boolean me = vm.isMe();
        b.privateInfo.setVisibility(me ? View.VISIBLE : View.GONE);
        b.tabs.setVisibility(me ? View.VISIBLE : View.GONE);
        b.ridesHint.setVisibility(me ? View.VISIBLE : View.GONE);
        b.logOut.setVisibility(me ? View.VISIBLE : View.GONE);
        b.demoCard.setVisibility(me && BuildConfig.DEBUG ? View.VISIBLE : View.GONE);
        b.ridesHeader.setText(me ? "My rides" : "Public rides");
        showClock();
    }

    private void showUser(User u) {
        if (u == null) return;
        b.avatar.setText(UiFormat.initials(u.getDisplayName()));
        b.displayName.setText(u.getDisplayName());
        b.fullName.setText(u.getFullName());
        b.fullName.setVisibility(u.getDisplayName().equals(u.getFullName()) ? View.GONE : View.VISIBLE);
        b.gradYear.setText("USC Class of " + u.getGradYear());
        b.email.setText(u.getEmail());
        b.uscId.setText("USC ID " + u.getUscId());
        renderRides();
    }

    /** FR-4.7: a flag shows on the flagged rider's own profile until they mark the share paid. */
    private void showFlags(List<NonPaymentFlag> flags) {
        b.notices.removeAllViews();
        if (!vm.isMe()) return;
        for (NonPaymentFlag f : flags) {
            TextView t = (TextView) getLayoutInflater().inflate(R.layout.item_notice, b.notices, false);
            t.setText("⚑ " + f.getOrganizerName() + " flagged your unpaid share of "
                    + Money.format(f.getAmountCents()) + " for “" + f.getRideTitle()
                    + "”. Tap to open the ride and mark it paid once you've reimbursed them.");
            t.setOnClickListener(v -> push(RideDetailFragment.newInstance(f.getRideId())));
            b.notices.addView(t);
        }
    }

    private void renderRides() {
        if (b == null) return;
        b.rides.removeAllViews();
        User u = vm.user().getValue();
        String empty;
        int count = 0;
        int tab = vm.isMe() ? b.tabs.getSelectedTabPosition() : -1;
        if (tab == TAB_UPCOMING) {
            for (Ride r : nonNull(vm.upcoming().getValue())) {
                addRide(r, null, u, true);
                count++;
            }
            empty = "No upcoming rides.";
        } else if (tab == TAB_COMPLETED) {
            for (HistoryEntry e : nonNull(vm.completed().getValue())) {
                addRide(e.getRide(), e.getMyShare(), u, true);
                count++;
            }
            empty = "No completed rides yet.";
        } else {
            for (Ride r : nonNull(vm.publicRides().getValue())) {
                addRide(r, null, u, false);
                count++;
            }
            empty = vm.isMe() ? "You haven't made any rides public." : "This student hasn't shared any rides.";
        }
        b.ridesEmpty.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        b.ridesEmpty.setText(empty);
    }

    private void addRide(Ride r, CostShare share, User u, boolean withToggle) {
        ItemRideBinding ib = ItemRideBinding.inflate(getLayoutInflater(), b.rides, false);
        RideViews.bind(ib, r);
        if (share != null) {
            RideViews.extra(ib, "Your share: " + Money.format(share.getAmountCents()) + " · " + share.getStatus().label());
        }
        if (withToggle && u != null) {
            ib.publicSwitch.setVisibility(View.VISIBLE);
            ib.publicSwitch.setChecked(u.isRidePublic(r.getRideId()));
            ib.publicSwitch.setOnCheckedChangeListener((s, on) -> vm.setRidePublic(r.getRideId(), on));
        }
        ib.card.setOnClickListener(v -> push(RideDetailFragment.newInstance(r.getRideId())));
        b.rides.addView(ib.getRoot());
    }

    private static <T> List<T> nonNull(List<T> l) {
        return l == null ? Collections.emptyList() : l;
    }

    private void editNickname() {
        User u = vm.user().getValue();
        DialogNicknameBinding d = DialogNicknameBinding.inflate(getLayoutInflater());
        if (u != null && u.getNickname() != null) d.nickname.setText(u.getNickname());
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Edit nickname")
                .setView(d.getRoot())
                .setNegativeButton("Back", null)
                .setPositiveButton("Save", (dlg, w) -> vm.saveNickname(String.valueOf(d.nickname.getText())))
                .show();
    }

    private void shift(Duration d) {
        vm.shiftClock(d);
        showClock();
        toast("App clock: " + UiFormat.dateTime(vm.now()));
    }

    private void showClock() {
        Duration off = vm.clockOffset();
        String offset = off.isZero() ? "real time" : "+" + off.toDays() + "d " + (off.toHours() % 24) + "h ahead";
        b.clock.setText("Now: " + UiFormat.dateTime(vm.now()) + " (" + offset + ")\n"
                + "Moves the app clock forward so you can show departed rides, the 3-hour leave rule, "
                + "and monthly summaries without waiting.");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
