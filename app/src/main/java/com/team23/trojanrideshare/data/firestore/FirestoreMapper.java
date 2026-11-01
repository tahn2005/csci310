package com.team23.trojanrideshare.data.firestore;

import com.google.firebase.firestore.DocumentSnapshot;
import com.team23.trojanrideshare.domain.ActivityStatus;
import com.team23.trojanrideshare.domain.CapacityStatus;
import com.team23.trojanrideshare.domain.CostShare;
import com.team23.trojanrideshare.domain.JoinRequest;
import com.team23.trojanrideshare.domain.NonPaymentFlag;
import com.team23.trojanrideshare.domain.PaymentStatus;
import com.team23.trojanrideshare.domain.Place;
import com.team23.trojanrideshare.domain.RequestStatus;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.domain.RideMember;
import com.team23.trojanrideshare.domain.User;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts between domain objects and Firestore documents. Every document of a type is
 * always written with the same set of fields, so Security Rules can compare old and new
 * versions field by field.
 */
final class FirestoreMapper {

    private FirestoreMapper() {
    }

    // ------------------------------------------------------------------ time

    static Long millis(Instant i) {
        return i == null ? null : i.toEpochMilli();
    }

    static long millis(LocalDateTime t) {
        return t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    static Instant instant(Object o) {
        return o instanceof Number ? Instant.ofEpochMilli(((Number) o).longValue()) : null;
    }

    static LocalDateTime localDateTime(Object o) {
        return o instanceof Number
                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(((Number) o).longValue()), ZoneId.systemDefault())
                : null;
    }

    private static long num(Object o) {
        return o instanceof Number ? ((Number) o).longValue() : 0L;
    }

    private static double dbl(Object o) {
        return o instanceof Number ? ((Number) o).doubleValue() : Double.NaN;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object o) {
        return o instanceof List ? (List<Object>) o : new ArrayList<>();
    }

    // ------------------------------------------------------------------ place

    static Map<String, Object> toMap(Place p) {
        if (p == null) return null;
        Map<String, Object> m = new HashMap<>();
        m.put("placeId", p.getPlaceId());
        m.put("name", p.getName());
        m.put("address", p.getAddress());
        m.put("lat", p.getLat());
        m.put("lng", p.getLng());
        return m;
    }

    static Place toPlace(Object o) {
        if (!(o instanceof Map)) return null;
        Map<String, Object> m = map(o);
        return new Place(str(m.get("placeId")), str(m.get("name")), str(m.get("address")),
                dbl(m.get("lat")), dbl(m.get("lng")));
    }

    // ------------------------------------------------------------------ user

    static Map<String, Object> toMap(User u) {
        Map<String, Object> m = new HashMap<>();
        m.put("uid", u.getUid());
        m.put("fullName", u.getFullName());
        m.put("nickname", u.getNickname() == null ? "" : u.getNickname());
        m.put("email", u.getEmail());
        m.put("uscId", u.getUscId());
        m.put("gradYear", u.getGradYear());
        m.put("publicRideIds", new ArrayList<>(u.getPublicRideIds()));
        return m;
    }

    static User toUser(DocumentSnapshot s) {
        User u = new User();
        u.setUid(s.getId());
        u.setFullName(s.getString("fullName"));
        u.setNickname(s.getString("nickname"));
        u.setEmail(s.getString("email"));
        u.setUscId(s.getString("uscId"));
        u.setGradYear((int) num(s.get("gradYear")));
        List<String> ids = new ArrayList<>();
        for (Object o : list(s.get("publicRideIds"))) ids.add(str(o));
        u.setPublicRideIds(ids);
        return u;
    }

    // ------------------------------------------------------------------ ride

    static Map<String, Object> toMap(Ride r) {
        Map<String, Object> m = new HashMap<>();
        m.put("title", r.getTitle());
        m.put("organizerId", r.getOrganizerId());
        m.put("organizerName", r.getOrganizerName());
        m.put("origin", toMap(r.getOrigin()));
        m.put("destination", toMap(r.getDestination()));
        m.put("departureMillis", millis(r.getDeparture()));
        m.put("maxRiders", r.getMaxRiders());
        m.put("expectedCostCents", r.getExpectedCostCents());
        m.put("actualCostCents", r.getActualCostCents());
        m.put("note", r.getNote());
        m.put("activityStatus", r.getActivityStatus().name());
        m.put("capacityStatus", r.getCapacityStatus().name());

        List<Map<String, Object>> members = new ArrayList<>();
        for (RideMember mem : r.getMembers()) {
            Map<String, Object> mm = new HashMap<>();
            mm.put("userId", mem.getUserId());
            mm.put("displayName", mem.getDisplayName());
            mm.put("acceptedAt", millis(mem.getAcceptedAt()));
            members.add(mm);
        }
        m.put("members", members);
        m.put("memberIds", r.memberIds());
        m.put("pendingRequests", new HashMap<>(r.getPendingRequests()));

        Map<String, Object> shares = new HashMap<>();
        int order = 0;
        for (CostShare s : r.getShares()) {
            Map<String, Object> sm = new HashMap<>();
            sm.put("userId", s.getUserId());
            sm.put("displayName", s.getDisplayName());
            sm.put("amountCents", s.getAmountCents());
            sm.put("status", s.getStatus().name());
            sm.put("paidAt", millis(s.getPaidAt()));
            sm.put("order", order++);
            shares.put(s.getUserId(), sm);
        }
        m.put("shares", shares);
        m.put("splitMethod", r.getSplitMethod());
        m.put("createdAt", millis(r.getCreatedAt()));
        m.put("completedAt", millis(r.getCompletedAt()));
        return m;
    }

    static Ride toRide(DocumentSnapshot s) {
        Ride r = new Ride();
        r.setRideId(s.getId());
        r.setTitle(s.getString("title"));
        r.setOrganizerId(s.getString("organizerId"));
        r.setOrganizerName(s.getString("organizerName"));
        r.setOrigin(toPlace(s.get("origin")));
        r.setDestination(toPlace(s.get("destination")));
        r.setDeparture(localDateTime(s.get("departureMillis")));
        r.setMaxRiders((int) num(s.get("maxRiders")));
        r.setExpectedCostCents(num(s.get("expectedCostCents")));
        r.setActualCostCents(num(s.get("actualCostCents")));
        r.setNote(s.getString("note"));
        r.setActivityStatus(ActivityStatus.fromName(s.getString("activityStatus")));
        r.setCapacityStatus(CapacityStatus.fromName(s.getString("capacityStatus")));

        List<RideMember> members = new ArrayList<>();
        for (Object o : list(s.get("members"))) {
            Map<String, Object> mm = map(o);
            members.add(new RideMember(str(mm.get("userId")), str(mm.get("displayName")),
                    instant(mm.get("acceptedAt"))));
        }
        r.setMembers(members);

        Map<String, String> pending = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : map(s.get("pendingRequests")).entrySet()) {
            pending.put(e.getKey(), str(e.getValue()));
        }
        r.setPendingRequests(pending);

        List<Map<String, Object>> rawShares = new ArrayList<>();
        for (Object o : map(s.get("shares")).values()) rawShares.add(map(o));
        rawShares.sort(Comparator.comparingLong(sm -> num(sm.get("order"))));
        List<CostShare> shares = new ArrayList<>();
        for (Map<String, Object> sm : rawShares) {
            CostShare c = new CostShare(r.getRideId(), str(sm.get("userId")), str(sm.get("displayName")),
                    num(sm.get("amountCents")));
            c.setStatus(PaymentStatus.fromName(str(sm.get("status"))));
            c.setPaidAt(instant(sm.get("paidAt")));
            shares.add(c);
        }
        r.setShares(shares);
        r.setSplitMethod(s.getString("splitMethod"));
        r.setCreatedAt(instant(s.get("createdAt")));
        r.setCompletedAt(instant(s.get("completedAt")));
        return r;
    }

    // ------------------------------------------------------------------ join request

    static Map<String, Object> toMap(JoinRequest q) {
        Map<String, Object> m = new HashMap<>();
        m.put("rideId", q.getRideId());
        m.put("requesterId", q.getRequesterId());
        m.put("requesterName", q.getRequesterName());
        m.put("organizerId", q.getOrganizerId());
        m.put("message", q.getMessage());
        m.put("createdAt", millis(q.getCreatedAt()));
        m.put("respondedAt", millis(q.getRespondedAt()));
        m.put("status", q.getStatus().name());
        return m;
    }

    static JoinRequest toRequest(DocumentSnapshot s) {
        JoinRequest q = new JoinRequest();
        q.setRequestId(s.getId());
        q.setRideId(s.getString("rideId"));
        q.setRequesterId(s.getString("requesterId"));
        q.setRequesterName(s.getString("requesterName"));
        q.setOrganizerId(s.getString("organizerId"));
        q.setMessage(s.getString("message"));
        q.setCreatedAt(instant(s.get("createdAt")));
        q.setRespondedAt(instant(s.get("respondedAt")));
        q.setStatus(RequestStatus.fromName(s.getString("status")));
        return q;
    }

    // ------------------------------------------------------------------ non-payment flag

    static Map<String, Object> toMap(NonPaymentFlag f) {
        Map<String, Object> m = new HashMap<>();
        m.put("rideId", f.getRideId());
        m.put("userId", f.getUserId());
        m.put("organizerId", f.getOrganizerId());
        m.put("rideTitle", f.getRideTitle());
        m.put("userName", f.getUserName());
        m.put("organizerName", f.getOrganizerName());
        m.put("amountCents", f.getAmountCents());
        m.put("createdAt", millis(f.getCreatedAt()));
        return m;
    }

    static NonPaymentFlag toFlag(DocumentSnapshot s) {
        NonPaymentFlag f = new NonPaymentFlag(s.getString("rideId"), s.getString("userId"),
                s.getString("organizerId"));
        f.setRideTitle(s.getString("rideTitle"));
        f.setUserName(s.getString("userName"));
        f.setOrganizerName(s.getString("organizerName"));
        f.setAmountCents(num(s.get("amountCents")));
        f.setCreatedAt(instant(s.get("createdAt")));
        return f;
    }
}
