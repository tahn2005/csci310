package com.team23.trojanrideshare.ui.ride;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.team23.trojanrideshare.R;
import com.team23.trojanrideshare.databinding.DialogEditRideBinding;
import com.team23.trojanrideshare.databinding.DialogRequestJoinBinding;
import com.team23.trojanrideshare.databinding.FragmentRideDetailBinding;
import com.team23.trojanrideshare.databinding.ItemMemberBinding;
import com.team23.trojanrideshare.databinding.ItemRequestBinding;
import com.team23.trojanrideshare.domain.ActivityStatus;
import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.CostShare;
import com.team23.trojanrideshare.domain.JoinRequest;
import com.team23.trojanrideshare.domain.NonPaymentFlag;
import com.team23.trojanrideshare.domain.Place;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.domain.RideMember;
import com.team23.trojanrideshare.domain.util.Money;
import com.team23.trojanrideshare.service.CustomSplit;
import com.team23.trojanrideshare.service.JoinRequestService;
import com.team23.trojanrideshare.service.RideService;
import com.team23.trojanrideshare.ui.common.BaseFragment;
import com.team23.trojanrideshare.ui.common.StatusStyle;
import com.team23.trojanrideshare.ui.common.UiFormat;
import com.team23.trojanrideshare.ui.cost.CostSplitFragment;
import com.team23.trojanrideshare.ui.profile.ProfileFragment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/** FR-2.4, FR-2.5, FR-3.5 – FR-3.10, FR-4.5 – FR-4.7: everything about one ride. */
public class RideDetailFragment extends BaseFragment {

    private static final String ARG_RIDE = "rideId";

    private FragmentRideDetailBinding b;
    private RideDetailViewModel vm;

    public static RideDetailFragment newInstance(String rideId) {
        RideDetailFragment f = new RideDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_RIDE, rideId);
        f.setArguments(args);
        return f;
    }

    @Override
    protected String title() {
        return "Ride Details";
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentRideDetailBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        vm = new ViewModelProvider(this).get(RideDetailViewModel.class);
        observeCommon(vm, b.progress);
        vm.busy().observe(getViewLifecycleOwner(), busy -> {
            if (!Boolean.TRUE.equals(busy)) b.refresh.setRefreshing(false);
        });
        vm.ride().observe(getViewLifecycleOwner(), this::showRide);
        vm.pending().observe(getViewLifecycleOwner(), this::showPending);
        vm.flags().observe(getViewLifecycleOwner(), f -> {
            Ride r = vm.ride().getValue();
            if (r != null) showShares(r);
        });
        b.refresh.setOnRefreshListener(vm::reload);
        b.content.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onResume() {
        super.onResume();
        vm.load(requireArguments().getString(ARG_RIDE));
    }

    // ------------------------------------------------------------------ ride post

    private void showRide(Ride r) {
        if (r == null) return;
        b.content.setVisibility(View.VISIBLE);
        String me = vm.uid();
        b.title.setText(r.getTitle());
        StatusStyle.activity(b.activityChip, r.getActivityStatus());
        StatusStyle.capacity(b.capacityChip, r.displayCapacity(), r.capacityLabel());
        b.organizer.setText("Organized by " + r.getOrganizerName() + (r.isOrganizer(me) ? " (you)" : "") + "  ›");
        b.organizer.setOnClickListener(v -> push(ProfileFragment.forUser(r.getOrganizerId())));
        b.origin.setText(placeText("From", r.getOrigin()));
        b.destination.setText(placeText("To", r.getDestination()));
        b.when.setText(UiFormat.dateTime(r.getDeparture()));
        b.riders.setText(r.riderCount() + " of " + r.getMaxRiders() + " riders · " + r.seatsLeft()
                + (r.seatsLeft() == 1 ? " seat" : " seats") + " left");
        String cost = "Expected cost " + Money.format(r.getExpectedCostCents());
        if (r.getActivityStatus() == ActivityStatus.COMPLETED) {
            cost += "\nActual cost " + Money.format(r.getActualCostCents());
        }
        b.cost.setText(cost);
        b.note.setVisibility(r.getNote().isEmpty() ? View.GONE : View.VISIBLE);
        b.note.setText("Note: " + r.getNote());

        showRiders(r);
        showActions(r);
        showShares(r);
    }

    private static String placeText(String label, Place p) {
        if (p == null) return label + ": ?";
        return label + ": " + p.getName() + (p.getAddress().isEmpty() ? "" : "\n" + p.getAddress());
    }

    private void showRiders(Ride r) {
        b.ridersHeader.setText("Riders (" + r.riderCount() + " of " + r.getMaxRiders() + ")");
        b.ridersList.removeAllViews();
        for (RideMember m : r.getMembers()) {
            ItemMemberBinding row = ItemMemberBinding.inflate(getLayoutInflater(), b.ridersList, false);
            row.avatar.setText(UiFormat.initials(m.getDisplayName()));
            row.name.setText(m.getDisplayName() + (m.getUserId().equals(vm.uid()) ? " (you)" : ""));
            row.subtitle.setVisibility(View.VISIBLE);
            row.subtitle.setText(r.isOrganizer(m.getUserId()) ? "Posted or took over this ride"
                    : "Joined " + UiFormat.stamp(m.getAcceptedAt()));
            if (r.isOrganizer(m.getUserId())) {
                row.chip.setVisibility(View.VISIBLE);
                StatusStyle.apply(row.chip, "Organizer", R.color.ink, R.color.gold);
            }
            row.row.setOnClickListener(v -> push(ProfileFragment.forUser(m.getUserId())));
            b.ridersList.addView(row.getRoot());
        }
    }

    // ------------------------------------------------------------------ actions

    private void showActions(Ride r) {
        String me = vm.uid();
        boolean organizer = r.isOrganizer(me);
        boolean member = r.hasMember(me);
        boolean pendingMine = r.hasPendingRequestFrom(me);
        String reason = null;

        hide(b.requestJoin, b.cancelRequest, b.markCompleted, b.editDeparture, b.leaveRide, b.cancelRide);

        if (!member && !pendingMine) {
            // FR-3.5 / FR-3.E3: show the button, disabled with the reason when not eligible.
            String block = vm.joinBlock(r);
            b.requestJoin.setVisibility(View.VISIBLE);
            b.requestJoin.setEnabled(block == null);
            b.requestJoin.setOnClickListener(v -> askToJoin());
            reason = block;
        }
        if (pendingMine) {
            b.cancelRequest.setVisibility(View.VISIBLE);
            reason = "Your request to join is pending. The organizer will accept or reject it.";
            b.cancelRequest.setOnClickListener(v -> confirm("Cancel your request?",
                    "The organizer will no longer see it.", "Cancel Request", vm::cancelRequest));
        }
        if (organizer && r.isActive()) {
            String completeBlock = vm.completeBlock(r);
            b.markCompleted.setVisibility(View.VISIBLE);
            b.markCompleted.setEnabled(completeBlock == null);
            b.markCompleted.setOnClickListener(v -> push(CostSplitFragment.newInstance(r.getRideId())));
            if (completeBlock != null && !r.hasDeparted(vm.now())) {
                b.markCompleted.setText("Mark as Completed (after departure)");
            } else {
                b.markCompleted.setText("Mark as Completed");
            }
            b.editDeparture.setVisibility(View.VISIBLE);
            b.editDeparture.setOnClickListener(v -> editRide(r));
            b.cancelRide.setVisibility(View.VISIBLE);
            b.cancelRide.setOnClickListener(v -> confirm("Cancel this ride?",
                    "Riders will see it as Cancelled and pending requests will be rejected. This can't be undone.",
                    "Cancel Ride", vm::cancelRide));
        }
        if (member && r.isActive()) {
            // FR-3.10 / FR-3.E7: Leave is disabled within 3 hours of departure.
            String leaveBlock = vm.leaveBlock(r);
            b.leaveRide.setVisibility(View.VISIBLE);
            b.leaveRide.setEnabled(leaveBlock == null);
            b.leaveRide.setOnClickListener(v -> confirmLeave(r));
            if (leaveBlock != null && reason == null) reason = leaveBlock;
        }
        if (!r.isActive()) {
            if (r.getActivityStatus() == ActivityStatus.CANCELLED) reason = "This ride was cancelled.";
            else if (member) reason = "This ride is completed. See the cost split below.";
            else reason = JoinRequestService.UNAVAILABLE;
        }
        b.reason.setVisibility(reason == null ? View.GONE : View.VISIBLE);
        b.reason.setText(reason);
    }

    private static void hide(View... views) {
        for (View v : views) v.setVisibility(View.GONE);
    }

    private void askToJoin() {
        DialogRequestJoinBinding d = DialogRequestJoinBinding.inflate(getLayoutInflater());
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Request to join")
                .setView(d.getRoot())
                .setNegativeButton("Back", null)
                .setPositiveButton("Send", (dlg, w) -> vm.requestToJoin(String.valueOf(d.message.getText())))
                .show();
    }

    private void confirmLeave(Ride r) {
        String msg;
        if (r.isOrganizer(vm.uid())) {
            RideMember next = vm.successorIfLeaving(r);
            msg = next == null
                    ? "You're the only rider, so leaving will cancel this ride."
                    : next.getDisplayName() + " (the most recently accepted rider) will become the organizer.";
        } else {
            msg = "Your seat will open up for other students.";
        }
        confirm("Leave this ride?", msg, "Leave Ride", vm::leave);
    }

    /** FR-2.5: the organizer edits everything except the route. */
    private void editRide(Ride r) {
        DialogEditRideBinding d = DialogEditRideBinding.inflate(getLayoutInflater());
        d.title.setText(r.getTitle());
        d.cost.setText(Money.plain(r.getExpectedCostCents()));
        d.note.setText(r.getNote());
        int[] seatIds = {R.id.riders2, R.id.riders3, R.id.riders4, R.id.riders5, R.id.riders6};
        for (int i = 0; i < seatIds.length; i++) {
            // The maximum can't drop below the riders already in the ride.
            d.maxRiders.findViewById(seatIds[i]).setEnabled(Ride.MIN_RIDERS + i >= r.riderCount());
        }
        d.maxRiders.check(seatIds[r.getMaxRiders() - Ride.MIN_RIDERS]);
        LocalDateTime[] chosen = {r.getDeparture()};
        Runnable refresh = () -> {
            d.date.setText(UiFormat.date(chosen[0].toLocalDate()));
            d.time.setText(UiFormat.time(chosen[0].toLocalTime()));
        };
        refresh.run();
        d.status.check(r.getActivityStatus() == ActivityStatus.DELAYED ? R.id.delayed : R.id.onTime);
        d.date.setOnClickListener(v -> {
            LocalDate cur = chosen[0].toLocalDate();
            new DatePickerDialog(requireContext(), (p, y, m, day) -> {
                chosen[0] = LocalDateTime.of(LocalDate.of(y, m + 1, day), chosen[0].toLocalTime());
                refresh.run();
            }, cur.getYear(), cur.getMonthValue() - 1, cur.getDayOfMonth()).show();
        });
        d.time.setOnClickListener(v -> {
            LocalTime cur = chosen[0].toLocalTime();
            new TimePickerDialog(requireContext(), (p, h, m) -> {
                chosen[0] = LocalDateTime.of(chosen[0].toLocalDate(), LocalTime.of(h, m));
                refresh.run();
            }, cur.getHour(), cur.getMinute(), false).show();
        });
        clearErrorOnEdit(d.titleLayout, d.costLayout, d.noteLayout);
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Edit ride")
                .setView(d.getRoot())
                .setNegativeButton("Back", null)
                .setPositiveButton("Save", null)
                .show();
        // Validate before closing so a mistake doesn't throw away the organizer's other edits.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            Ride changes = new Ride();
            changes.setTitle(String.valueOf(d.title.getText()));
            changes.setDeparture(chosen[0]);
            changes.setNote(String.valueOf(d.note.getText()));
            for (int i = 0; i < seatIds.length; i++) {
                if (d.maxRiders.getCheckedButtonId() == seatIds[i]) changes.setMaxRiders(Ride.MIN_RIDERS + i);
            }
            Map<String, String> errors;
            try {
                changes.setExpectedCostCents(Money.parse(String.valueOf(d.cost.getText())));
                errors = vm.editErrors(changes);
            } catch (AppException e) {
                errors = vm.editErrors(changes);
                errors.put(RideService.F_COST, e.getMessage());
            }
            d.titleLayout.setError(errors.get(RideService.F_TITLE));
            d.costLayout.setError(errors.get(RideService.F_COST));
            d.noteLayout.setError(errors.get(RideService.F_NOTE));
            String other = errors.containsKey(RideService.F_DEPARTURE) ? errors.get(RideService.F_DEPARTURE)
                    : errors.get(RideService.F_MAX_RIDERS);
            if (other != null) toast(other);
            if (!errors.isEmpty()) return;
            vm.editRide(changes,
                    d.status.getCheckedButtonId() == R.id.delayed ? ActivityStatus.DELAYED : ActivityStatus.ON_TIME);
            dialog.dismiss();
        });
    }

    // ------------------------------------------------------------------ pending requests

    private void showPending(List<JoinRequest> list) {
        Ride r = vm.ride().getValue();
        boolean show = r != null && r.isOrganizer(vm.uid()) && r.isActive();
        b.pendingSection.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;
        b.pendingHeader.setText("Pending requests (" + list.size() + ")");
        b.pendingEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        b.pendingList.removeAllViews();
        for (JoinRequest q : list) {
            ItemRequestBinding row = ItemRequestBinding.inflate(getLayoutInflater(), b.pendingList, false);
            bindIncoming(row, q);
            b.pendingList.addView(row.getRoot());
        }
    }

    /** A pending request with Accept / Reject (FR-3.8). */
    private void bindIncoming(ItemRequestBinding row, JoinRequest q) {
        row.heading.setText(q.getRequesterName() + "  ›");
        row.heading.setOnClickListener(v -> push(ProfileFragment.forUser(q.getRequesterId())));
        StatusStyle.request(row.status, q.getStatus());
        row.message.setVisibility(q.getMessage().isEmpty() ? View.GONE : View.VISIBLE);
        row.message.setText("“" + q.getMessage() + "”");
        row.time.setText("Requested " + UiFormat.stamp(q.getCreatedAt()));
        row.buttons.setVisibility(View.VISIBLE);
        row.secondary.setVisibility(View.VISIBLE);
        row.secondary.setText("Reject");
        row.secondary.setOnClickListener(v -> vm.reject(q));
        row.primary.setVisibility(View.VISIBLE);
        row.primary.setText("Accept");
        row.primary.setOnClickListener(v -> vm.accept(q));
    }

    // ------------------------------------------------------------------ cost shares

    private void showShares(Ride r) {
        boolean completed = r.getActivityStatus() == ActivityStatus.COMPLETED;
        b.costCard.setVisibility(completed ? View.VISIBLE : View.GONE);
        if (!completed) return;
        String me = vm.uid();
        boolean organizer = r.isOrganizer(me);
        Map<String, NonPaymentFlag> flags = vm.flags().getValue();
        int paid = 0;
        for (CostShare s : r.getShares()) if (s.isPaid()) paid++;
        b.costSummary.setText("Actual cost " + Money.format(r.getActualCostCents()) + " · "
                + (CustomSplit.METHOD.equals(r.getSplitMethod()) ? "Custom split" : "Equal split")
                + " · " + paid + " of " + r.getShares().size() + " paid");
        b.sharesList.removeAllViews();
        for (CostShare s : r.getShares()) {
            ItemMemberBinding row = ItemMemberBinding.inflate(getLayoutInflater(), b.sharesList, false);
            row.avatar.setText(UiFormat.initials(s.getDisplayName()));
            row.name.setText(s.getDisplayName() + (s.getUserId().equals(me) ? " (you)" : ""));
            row.amount.setVisibility(View.VISIBLE);
            row.amount.setText(Money.format(s.getAmountCents()));
            row.chip.setVisibility(View.VISIBLE);
            StatusStyle.payment(row.chip, s.getStatus());
            boolean flagged = flags != null && flags.containsKey(s.getUserId());
            if (organizer && flagged) {
                row.subtitle.setVisibility(View.VISIBLE);
                row.subtitle.setText("⚑ Flagged for non-payment");
                row.subtitle.setTextColor(requireContext().getColor(R.color.danger));
            } else if (s.isPaid() && s.getPaidAt() != null) {
                row.subtitle.setVisibility(View.VISIBLE);
                row.subtitle.setText("Paid " + UiFormat.stamp(s.getPaidAt()));
            }
            // FR-4.7: only the organizer flags, never their own share (FR-4.E6).
            if (organizer && !s.isPaid() && !s.getUserId().equals(me)) {
                row.action.setVisibility(View.VISIBLE);
                row.action.setText(flagged ? "Unflag" : "Flag");
                row.action.setOnClickListener(v -> {
                    if (flagged) vm.unflag(s.getUserId());
                    else confirm("Flag " + s.getDisplayName() + "?",
                            "Flag this rider for not paying their " + Money.format(s.getAmountCents())
                                    + " share. They'll see a reminder on their profile until they mark it paid.",
                            "Flag", () -> vm.flag(s.getUserId()));
                });
            }
            b.sharesList.addView(row.getRoot());
        }
        // FR-4.6: only the rider whose share it is can mark it Paid.
        CostShare mine = r.shareOf(me);
        boolean canPay = mine != null && !mine.isPaid();
        b.markPaid.setVisibility(canPay ? View.VISIBLE : View.GONE);
        if (canPay) {
            b.markPaid.setText("I've Paid My Share (" + Money.format(mine.getAmountCents()) + ")");
            b.markPaid.setOnClickListener(v -> confirm("Mark your share as paid?",
                    "Confirm you reimbursed " + r.getOrganizerName() + " " + Money.format(mine.getAmountCents())
                            + " outside the app (e.g. Venmo).", "Mark Paid", vm::markPaid));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
