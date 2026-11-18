package com.team23.trojanrideshare.testutil;

import com.team23.trojanrideshare.domain.CostShare;
import com.team23.trojanrideshare.domain.JoinRequest;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.domain.RideMember;
import com.team23.trojanrideshare.domain.User;

import java.util.ArrayList;
import java.util.List;

/** Deep copies so the in-memory "database" behaves like a real store. */
public final class Copies {

    private Copies() {
    }

    public static Ride ride(Ride r) {
        Ride c = new Ride();
        c.setRideId(r.getRideId());
        c.setTitle(r.getTitle());
        c.setOrganizerId(r.getOrganizerId());
        c.setOrganizerName(r.getOrganizerName());
        c.setOrigin(r.getOrigin());
        c.setDestination(r.getDestination());
        c.setDeparture(r.getDeparture());
        c.setMaxRiders(r.getMaxRiders());
        c.setExpectedCostCents(r.getExpectedCostCents());
        c.setActualCostCents(r.getActualCostCents());
        c.setNote(r.getNote());
        c.setActivityStatus(r.getActivityStatus());
        c.setCapacityStatus(r.getCapacityStatus());
        List<RideMember> members = new ArrayList<>();
        for (RideMember m : r.getMembers()) {
            members.add(new RideMember(m.getUserId(), m.getDisplayName(), m.getAcceptedAt()));
        }
        c.setMembers(members);
        c.setPendingRequests(r.getPendingRequests());
        List<CostShare> shares = new ArrayList<>();
        for (CostShare s : r.getShares()) shares.add(s.copy());
        c.setShares(shares);
        c.setSplitMethod(r.getSplitMethod());
        c.setCreatedAt(r.getCreatedAt());
        c.setCompletedAt(r.getCompletedAt());
        return c;
    }

    public static JoinRequest request(JoinRequest q) {
        JoinRequest c = new JoinRequest();
        c.setRequestId(q.getRequestId());
        c.setRideId(q.getRideId());
        c.setRequesterId(q.getRequesterId());
        c.setRequesterName(q.getRequesterName());
        c.setOrganizerId(q.getOrganizerId());
        c.setMessage(q.getMessage());
        c.setCreatedAt(q.getCreatedAt());
        c.setRespondedAt(q.getRespondedAt());
        c.setStatus(q.getStatus());
        return c;
    }

    public static User user(User u) {
        User c = new User(u.getFullName(), u.getNickname(), u.getEmail(), u.getUscId(), u.getGradYear());
        c.setUid(u.getUid());
        c.setPublicRideIds(u.getPublicRideIds());
        return c;
    }
}
