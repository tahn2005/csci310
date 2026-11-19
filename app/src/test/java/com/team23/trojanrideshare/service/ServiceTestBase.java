package com.team23.trojanrideshare.service;

import com.team23.trojanrideshare.domain.User;
import com.team23.trojanrideshare.domain.util.FixedClock;
import com.team23.trojanrideshare.testutil.FakeAuthGateway;
import com.team23.trojanrideshare.testutil.Fixtures;
import com.team23.trojanrideshare.testutil.InMemoryDatabase;

import org.junit.Before;

import java.time.LocalDateTime;

/** Wires every service to the in-memory database, like AppContainer does with Firestore. */
public abstract class ServiceTestBase {

    protected static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 30, 12, 0);

    protected InMemoryDatabase db;
    protected FakeAuthGateway auth;
    protected FixedClock clock;
    protected Session session;
    protected AccountService accounts;
    protected RideService rideService;
    protected RideSearchEngine search;
    protected JoinRequestService joins;
    protected CostSplitService costs;
    protected RideHistoryService history;

    @Before
    public void wire() {
        db = new InMemoryDatabase();
        auth = new FakeAuthGateway();
        clock = new FixedClock(NOW);
        session = new Session();
        accounts = new AccountService(auth, db, clock, session);
        rideService = new RideService(db, null, clock, session);
        search = new RideSearchEngine(db, clock, session);
        joins = new JoinRequestService(db, db, clock, session);
        costs = new CostSplitService(db, clock, session);
        history = new RideHistoryService(db, clock, session);
        for (String id : new String[]{"alice", "bob", "carol", "dave"}) {
            User u = Fixtures.user(id, id.substring(0, 1).toUpperCase() + id.substring(1));
            db.users.put(id, u);
        }
    }

    protected void as(String uid) {
        session.set(db.users.get(uid));
    }
}
