package com.team23.trojanrideshare.ui.myrides;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;
import com.team23.trojanrideshare.databinding.FragmentMyRidesBinding;
import com.team23.trojanrideshare.databinding.ItemHeaderBinding;
import com.team23.trojanrideshare.databinding.ItemRequestBinding;
import com.team23.trojanrideshare.databinding.ItemRideBinding;
import com.team23.trojanrideshare.domain.JoinRequest;
import com.team23.trojanrideshare.domain.RequestStatus;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.ui.common.BaseFragment;
import com.team23.trojanrideshare.ui.common.RideViews;
import com.team23.trojanrideshare.ui.common.StatusStyle;
import com.team23.trojanrideshare.ui.common.UiFormat;
import com.team23.trojanrideshare.ui.profile.ProfileFragment;
import com.team23.trojanrideshare.ui.ride.CreateRideFragment;
import com.team23.trojanrideshare.ui.ride.RideDetailFragment;

import java.util.ArrayList;
import java.util.List;

/** FR-3.7: a central place for the rides a user organizes, has joined, and has requested. */
public class MyRidesFragment extends BaseFragment {

    private static final int TAB_ORGANIZING = 0;
    private static final int TAB_JOINED = 1;
    private static final int TAB_REQUESTS = 2;
    private static int lastTab = TAB_ORGANIZING;

    private FragmentMyRidesBinding b;
    private MyRidesViewModel vm;
    private final Adapter adapter = new Adapter();

    @Override
    protected String title() {
        return "My Rides & Requests";
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentMyRidesBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        vm = new ViewModelProvider(this).get(MyRidesViewModel.class);
        observeCommon(vm, b.progress);
        b.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.list.setAdapter(adapter);
        vm.organizing().observe(getViewLifecycleOwner(), x -> render());
        vm.joined().observe(getViewLifecycleOwner(), x -> render());
        vm.incoming().observe(getViewLifecycleOwner(), x -> render());
        vm.requests().observe(getViewLifecycleOwner(), x -> render());
        vm.busy().observe(getViewLifecycleOwner(), busy -> {
            if (!Boolean.TRUE.equals(busy)) b.refresh.setRefreshing(false);
        });
        TabLayout.Tab tab = b.tabs.getTabAt(lastTab);
        if (tab != null) tab.select();
        b.tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab t) {
                lastTab = t.getPosition();
                render();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab t) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab t) {
            }
        });
        b.refresh.setOnRefreshListener(vm::load);
        b.fab.setOnClickListener(v -> push(new CreateRideFragment()));
    }

    @Override
    public void onResume() {
        super.onResume();
        vm.load();
    }

    private void render() {
        if (b == null) return;
        List<Object> rows = new ArrayList<>();
        String empty;
        int tab = b.tabs.getSelectedTabPosition();
        if (tab == TAB_JOINED) {
            rows.addAll(nonNull(vm.joined().getValue()));
            empty = "You haven't joined any upcoming rides. Find one on the Search tab.";
        } else if (tab == TAB_REQUESTS) {
            rows.addAll(nonNull(vm.requests().getValue()));
            empty = "You haven't sent any join requests yet.";
        } else {
            List<JoinRequest> incoming = nonNull(vm.incoming().getValue());
            List<Ride> organizing = nonNull(vm.organizing().getValue());
            if (!incoming.isEmpty()) {
                rows.add("Requests waiting for you (" + incoming.size() + ")");
                rows.addAll(incoming);
            }
            if (!organizing.isEmpty()) {
                rows.add("Rides I organize (" + organizing.size() + ")");
                rows.addAll(organizing);
            }
            empty = "You aren't organizing any upcoming rides. Tap Create Ride to post one.";
        }
        adapter.submit(rows, tab);
        b.empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        b.empty.setText(empty);
    }

    private static <T> List<T> nonNull(List<T> l) {
        return l == null ? new ArrayList<>() : l;
    }

    // ------------------------------------------------------------------ adapter

    private class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int HEADER = 0;
        private static final int RIDE = 1;
        private static final int REQUEST = 2;

        private final List<Object> rows = new ArrayList<>();
        private int tab;

        void submit(List<Object> list, int tab) {
            rows.clear();
            rows.addAll(list);
            this.tab = tab;
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            Object o = rows.get(position);
            return o instanceof String ? HEADER : o instanceof Ride ? RIDE : REQUEST;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            if (type == HEADER) return new Holder(ItemHeaderBinding.inflate(inf, parent, false).getRoot());
            if (type == RIDE) {
                ItemRideBinding rb = ItemRideBinding.inflate(inf, parent, false);
                Holder h = new Holder(rb.getRoot());
                h.ride = rb;
                return h;
            }
            ItemRequestBinding qb = ItemRequestBinding.inflate(inf, parent, false);
            Holder h = new Holder(qb.getRoot());
            h.request = qb;
            return h;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Holder h = (Holder) holder;
            Object o = rows.get(position);
            if (o instanceof String) {
                ((TextView) h.itemView).setText((String) o);
            } else if (o instanceof Ride) {
                Ride r = (Ride) o;
                RideViews.bind(h.ride, r);
                if (r.pendingCount() > 0 && r.isOrganizer(vm.uid())) {
                    RideViews.tag(h.ride, r.pendingCount() + " pending", true);
                }
                if (r.hasDeparted(app().clock.now())) {
                    RideViews.extra(h.ride, r.isOrganizer(vm.uid())
                            ? "This ride has departed. Open it to mark it completed and split the cost."
                            : "This ride has departed and is waiting for the organizer to complete it.");
                }
                h.ride.card.setOnClickListener(v -> push(RideDetailFragment.newInstance(r.getRideId())));
            } else {
                JoinRequest q = (JoinRequest) o;
                if (tab == TAB_REQUESTS) bindMine(h.request, q);
                else bindIncoming(h.request, q);
            }
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }

    private static class Holder extends RecyclerView.ViewHolder {
        ItemRideBinding ride;
        ItemRequestBinding request;

        Holder(View v) {
            super(v);
        }
    }

    /** FR-3.8: incoming request with Accept / Reject. */
    private void bindIncoming(ItemRequestBinding row, JoinRequest q) {
        Ride r = q.getRide();
        row.heading.setText(q.getRequesterName() + "  ›");
        row.heading.setOnClickListener(v -> push(ProfileFragment.forUser(q.getRequesterId())));
        StatusStyle.request(row.status, q.getStatus());
        row.line1.setVisibility(View.VISIBLE);
        row.line1.setText("wants to join “" + (r == null ? "your ride" : r.getTitle()) + "”");
        if (r != null) {
            row.line2.setVisibility(View.VISIBLE);
            row.line2.setText(r.capacityLabel() + " · " + UiFormat.dateTime(r.getDeparture()));
        } else {
            row.line2.setVisibility(View.GONE);
        }
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
        row.card.setOnClickListener(r == null ? null : v -> push(RideDetailFragment.newInstance(r.getRideId())));
    }

    /** FR-3.7.2: a request I sent, with Cancel while it is pending (FR-3.9). */
    private void bindMine(ItemRequestBinding row, JoinRequest q) {
        Ride r = q.getRide();
        row.heading.setText(r.getTitle());
        row.heading.setOnClickListener(null);
        StatusStyle.request(row.status, q.getStatus());
        row.line1.setVisibility(View.VISIBLE);
        row.line1.setText(UiFormat.route(r.getOrigin(), r.getDestination()));
        row.line2.setVisibility(View.VISIBLE);
        String extra = "";
        if (q.getStatus() == RequestStatus.ACCEPTED && !r.hasMember(vm.uid())) extra = " · you left this ride";
        else if (!r.isActive()) extra = " · ride " + r.getActivityStatus().label().toLowerCase();
        row.line2.setText(UiFormat.dateTime(r.getDeparture()) + extra);
        row.message.setVisibility(q.getMessage().isEmpty() ? View.GONE : View.VISIBLE);
        row.message.setText("“" + q.getMessage() + "”");
        row.time.setText("Sent " + UiFormat.stamp(q.getCreatedAt()));
        boolean pending = q.getStatus() == RequestStatus.PENDING;
        row.buttons.setVisibility(pending ? View.VISIBLE : View.GONE);
        row.primary.setVisibility(View.GONE);
        row.secondary.setVisibility(pending ? View.VISIBLE : View.GONE);
        row.secondary.setText("Cancel Request");
        row.secondary.setOnClickListener(v -> confirm("Cancel your request?",
                "The organizer will no longer see it.", "Cancel Request", () -> vm.cancel(q)));
        row.card.setOnClickListener(v -> push(RideDetailFragment.newInstance(r.getRideId())));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
