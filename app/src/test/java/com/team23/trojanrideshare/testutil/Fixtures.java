package com.team23.trojanrideshare.testutil;

import com.team23.trojanrideshare.domain.ActivityStatus;
import com.team23.trojanrideshare.domain.Place;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.domain.RideMember;
import com.team23.trojanrideshare.domain.User;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Places around USC and a ride builder for tests. */
public final class Fixtures {

    public static final Place USC = new Place("usc", "USC Village", "Los Angeles, CA", 34.0256, -118.2853);
    public static final Place USC_NEARBY = new Place("uscn", "Leavey Library", "Los Angeles, CA", 34.0219, -118.2828);
    public static final Place LAX = new Place("lax", "LAX", "Los Angeles, CA", 33.9416, -118.4085);
    public static final Place UNION = new Place("union", "Union Station", "Los Angeles, CA", 34.0562, -118.2365);
    public static final Place SANTA_MONICA = new Place("sm", "Santa Monica Pier", "Santa Monica, CA", 34.0094, -118.4973);

    private Fixtures() {
    }

    public static User user(String uid, String name) {
        User u = new User(name, "", uid + "@usc.edu", "", 2027);
        u.setUid(uid);
        return u;
    }

    public static Ride ride(String organizerId, Place from, Place to, LocalDateTime departure, int max, String... riders) {
        Ride r = new Ride();
        r.setTitle(from.getName() + " to " + to.getName());
        r.setOrganizerId(organizerId);
        r.setOrganizerName(organizerId.toUpperCase());
        r.setOrigin(from);
        r.setDestination(to);
        r.setDeparture(departure);
        r.setMaxRiders(max);
        r.setExpectedCostCents(4000);
        r.setActivityStatus(ActivityStatus.ON_TIME);
        List<RideMember> members = new ArrayList<>();
        members.add(new RideMember(organizerId, organizerId.toUpperCase(), Instant.ofEpochSecond(1000)));
        long t = 2000;
        for (String id : riders) members.add(new RideMember(id, id.toUpperCase(), Instant.ofEpochSecond(t += 1000)));
        r.setMembers(members);
        r.updateCapacity();
        return r;
    }
}
