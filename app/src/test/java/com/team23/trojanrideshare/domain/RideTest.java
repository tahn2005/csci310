package com.team23.trojanrideshare.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.team23.trojanrideshare.testutil.Fixtures;

import org.junit.Test;

import java.time.LocalDateTime;

public class RideTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 11, 20, 15, 0);

    @Test
    public void capacityFollowsRiderCount() {
        Ride r = Fixtures.ride("a", Fixtures.USC, Fixtures.LAX, T, 3, "b");
        assertEquals(CapacityStatus.OPEN, r.getCapacityStatus());
        assertEquals("Open – 2 of 3 riders", r.capacityLabel());
        r.getMembers().add(new RideMember("c", "C", null));
        r.updateCapacity();
        assertEquals(CapacityStatus.FULL, r.getCapacityStatus());
        r.setActivityStatus(ActivityStatus.COMPLETED);
        r.updateCapacity();
        assertEquals(CapacityStatus.NOT_APPLICABLE, r.getCapacityStatus());
    }

    @Test
    public void successorIsMostRecentlyAccepted() {
        Ride r = Fixtures.ride("a", Fixtures.USC, Fixtures.LAX, T, 4, "b", "c");
        assertEquals("c", r.successorAfter("a").getUserId());
        Ride solo = Fixtures.ride("a", Fixtures.USC, Fixtures.LAX, T, 4);
        assertNull(solo.successorAfter("a"));
    }

    @Test
    public void displayNameFallsBackToFullName() {
        User u = new User("Tommy Trojan", "", "t@usc.edu", "1234567890", 2027);
        assertEquals("Tommy Trojan", u.getDisplayName());
        u.setNickname("Tommy");
        assertEquals("Tommy", u.getDisplayName());
    }
}
