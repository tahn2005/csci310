// Security Rules tests. Every write below mirrors a document the app writes through
// FirestoreMapper.toMap (rides are always rewritten whole with tx.set), so a passing
// "allowed" test means the real app write is permitted and a passing "denied" test means
// the rules block it. Run: npm test (starts its own emulator on port 8181).
import { after, before, beforeEach, describe, test } from 'node:test';
import { readFileSync } from 'node:fs';
import {
  assertFails, assertSucceeds, initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, updateDoc, deleteDoc } from 'firebase/firestore';

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-rules-test',
    firestore: {
      rules: readFileSync(process.env.RULES_FILE ?? '../firestore.rules', 'utf8'),
      host: '127.0.0.1', port: 8181,
    },
  });
});
after(async () => { await env.cleanup(); });
beforeEach(async () => { await env.clearFirestore(); });

const db = (uid) => env.authenticatedContext(uid, { email: `${uid}@usc.edu` }).firestore();
const seed = (path, data) => env.withSecurityRulesDisabled((c) => setDoc(doc(c.firestore(), path), data));
const clone = (o) => structuredClone(o);

const place = (id, name) => ({ placeId: id, name, address: 'Los Angeles, CA', lat: 34.02, lng: -118.28 });
const member = (uid, name, t) => ({ userId: uid, displayName: name, acceptedAt: t });
const share = (uid, name, cents, status, order) =>
  ({ userId: uid, displayName: name, amountCents: cents, status, paidAt: status === 'PAID' ? 5000 : null, order });

/** A ride document exactly as FirestoreMapper.toMap(Ride) writes it. */
function ride(over = {}) {
  const members = over.members ?? [member('alice', 'Alice', 1000), member('bob', 'Bob', 2000)];
  return {
    title: 'LAX trip', organizerId: 'alice', organizerName: 'Alice',
    origin: place('usc', 'USC'), destination: place('lax', 'LAX'),
    departureMillis: 9_999_999_999_999, maxRiders: 4, expectedCostCents: 4000, actualCostCents: 0,
    note: '', activityStatus: 'ON_TIME', capacityStatus: 'OPEN',
    members, memberIds: members.map((m) => m.userId), pendingRequests: {},
    shares: {}, splitMethod: null, createdAt: 1000, completedAt: null,
    ...over,
  };
}

function completedRide() {
  return ride({
    members: [member('alice', 'Alice', 1000), member('bob', 'Bob', 2000), member('carol', 'Carol', 3000)],
    activityStatus: 'COMPLETED', capacityStatus: 'NOT_APPLICABLE', actualCostCents: 900,
    splitMethod: 'EQUAL', completedAt: 6000,
    shares: {
      alice: share('alice', 'Alice', 300, 'PAID', 0),
      bob: share('bob', 'Bob', 300, 'UNPAID', 1),
      carol: share('carol', 'Carol', 300, 'UNPAID', 2),
    },
  });
}

/** Seeds `before`, then has `uid` overwrite the ride with `mutate(copy)`, like tx.set does. */
async function rewrite(uid, before, mutate) {
  await seed('rides/r1', before);
  const next = clone(before);
  mutate(next);
  return setDoc(doc(db(uid), 'rides/r1'), next);
}

// ------------------------------------------------------------------ ride creation
describe('create ride', () => {
  const fresh = () => ride({ members: [member('alice', 'Alice', 1000)] });
  test('organizer creates a valid ride', () => assertSucceeds(setDoc(doc(db('alice'), 'rides/r1'), fresh())));
  test('cannot create a ride for someone else', () =>
    assertFails(setDoc(doc(db('bob'), 'rides/r1'), fresh())));
  test('cannot create with 7 seats', () =>
    assertFails(setDoc(doc(db('alice'), 'rides/r1'), { ...fresh(), maxRiders: 7 })));
});

// ------------------------------------------------------------------ Edit Ride (new)
describe('edit ride (organizerUpdate)', () => {
  test('organizer edits title, time, seats, cost, note and marks Delayed', () =>
    assertSucceeds(rewrite('alice', ride(), (r) => Object.assign(r, {
      title: 'LAX - earlier', departureMillis: 9_999_999_000_000, maxRiders: 2, capacityStatus: 'FULL',
      expectedCostCents: 5250, note: 'Meet at fountain', activityStatus: 'DELAYED',
    }))));
  test('seats cannot drop below current riders', () =>
    assertFails(rewrite('alice', ride({
      members: [member('alice', 'Alice', 1), member('bob', 'Bob', 2), member('carol', 'Carol', 3)],
    }), (r) => { r.maxRiders = 2; })));
  test('seats cannot exceed 6', () => assertFails(rewrite('alice', ride(), (r) => { r.maxRiders = 7; })));
  test('seats cannot be below 2', () =>
    assertFails(rewrite('alice', ride({ members: [member('alice', 'Alice', 1)] }), (r) => { r.maxRiders = 1; })));
  test('cost must stay above $0', () => assertFails(rewrite('alice', ride(), (r) => { r.expectedCostCents = 0; })));
  test('title cannot be blank', () => assertFails(rewrite('alice', ride(), (r) => { r.title = ''; })));
  test('origin cannot change', () =>
    assertFails(rewrite('alice', ride(), (r) => { r.origin = place('ucla', 'UCLA'); })));
  test('destination cannot change', () =>
    assertFails(rewrite('alice', ride(), (r) => { r.destination = place('union', 'Union Station'); })));
  test('createdAt cannot change', () => assertFails(rewrite('alice', ride(), (r) => { r.createdAt = 1; })));
  test('title longer than 60 characters is rejected', () =>
    assertFails(rewrite('alice', ride(), (r) => { r.title = 'x'.repeat(61); })));
  test('note longer than 300 characters is rejected', () =>
    assertFails(rewrite('alice', ride(), (r) => { r.note = 'x'.repeat(301); })));
  test('details cannot change once the ride is Completed', () =>
    assertFails(rewrite('alice', completedRide(), (r) => { r.title = 'renamed later'; })));
  test('details cannot change once the ride is Cancelled', () =>
    assertFails(rewrite('alice', ride({ activityStatus: 'CANCELLED', capacityStatus: 'NOT_APPLICABLE' }),
      (r) => { r.expectedCostCents = 1; })));
  test('a rider cannot edit the ride', () => assertFails(rewrite('bob', ride(), (r) => { r.title = 'hijacked'; })));
  test('a stranger cannot edit the ride', () => assertFails(rewrite('dave', ride(), (r) => { r.note = 'x'; })));
});

// ------------------------------------------------------------------ existing flows (regression)
describe('existing ride flows still allowed', () => {
  test('requester adds only their own pending request', () =>
    assertSucceeds(rewrite('dave', ride(), (r) => { r.pendingRequests = { dave: 'q1' }; })));
  test('requester cannot add someone else\'s request', () =>
    assertFails(rewrite('dave', ride(), (r) => { r.pendingRequests = { erin: 'q1' }; })));
  test('organizer accepts a request (ride becomes Full)', () =>
    assertSucceeds(rewrite('alice', ride({ maxRiders: 3, pendingRequests: { carol: 'q1' } }), (r) => {
      r.members.push(member('carol', 'Carol', 3000));
      r.memberIds.push('carol');
      r.pendingRequests = {};
      r.capacityStatus = 'FULL';
    })));
  test('rider leaves (Full -> Open)', () =>
    assertSucceeds(rewrite('bob', ride({ maxRiders: 2, capacityStatus: 'FULL' }), (r) => {
      r.members = r.members.filter((m) => m.userId !== 'bob');
      r.memberIds = ['alice'];
      r.capacityStatus = 'OPEN';
    })));
  test('rider cannot remove another rider', () =>
    assertFails(rewrite('bob', ride({
      members: [member('alice', 'Alice', 1), member('bob', 'Bob', 2), member('carol', 'Carol', 3)],
    }), (r) => {
      r.members = r.members.filter((m) => m.userId !== 'carol');
      r.memberIds = ['alice', 'bob'];
    })));
  test('organizer leaves and hands the ride to the newest rider', () =>
    assertSucceeds(rewrite('alice', ride(), (r) => {
      r.members = [member('bob', 'Bob', 2000)];
      r.memberIds = ['bob'];
      r.organizerId = 'bob';
      r.organizerName = 'Bob';
    })));
  test('only rider leaves: ride Cancelled with capacity N/A', () =>
    assertSucceeds(rewrite('alice', ride({ members: [member('alice', 'Alice', 1)] }), (r) => {
      r.activityStatus = 'CANCELLED';
      r.capacityStatus = 'NOT_APPLICABLE';
    })));
  test('organizer cancels: pending cleared, capacity N/A', () =>
    assertSucceeds(rewrite('alice', ride({ pendingRequests: { dave: 'q1' } }), (r) => {
      r.activityStatus = 'CANCELLED';
      r.capacityStatus = 'NOT_APPLICABLE';
      r.pendingRequests = {};
    })));
  test('organizer completes the ride with shares', () =>
    assertSucceeds(rewrite('alice', ride(), (r) => {
      Object.assign(r, completedRide(), { members: r.members, memberIds: r.memberIds });
      r.shares = { alice: share('alice', 'Alice', 200, 'PAID', 0), bob: share('bob', 'Bob', 200, 'UNPAID', 1) };
    })));
  test('rider marks own share Paid', () =>
    assertSucceeds(rewrite('bob', completedRide(), (r) => {
      r.shares.bob = { ...r.shares.bob, status: 'PAID', paidAt: 7000 };
    })));
  test('rider cannot mark someone else\'s share Paid', () =>
    assertFails(rewrite('bob', completedRide(), (r) => {
      r.shares.carol = { ...r.shares.carol, status: 'PAID', paidAt: 7000 };
    })));
  test('rider cannot lower their own share while paying', () =>
    assertFails(rewrite('bob', completedRide(), (r) => {
      r.shares.bob = { ...r.shares.bob, status: 'PAID', amountCents: 1 };
    })));
  test('organizer cannot change shares after completion', () =>
    assertFails(rewrite('alice', completedRide(), (r) => {
      r.shares.bob = { ...r.shares.bob, amountCents: 1 };
    })));
});

// ------------------------------------------------------------------ nickname sync (new)
describe('self rename on rides (selfRename)', () => {
  const rename = (r, uid, name) => r.members.forEach((m) => { if (m.userId === uid) m.displayName = name; });

  test('rider renames themself on an active ride', () =>
    assertSucceeds(rewrite('bob', ride(), (r) => rename(r, 'bob', 'Bobby'))));
  test('rider at the 6th position renames themself', () => {
    const six = ['alice', 'b', 'c', 'd', 'e', 'f'].map((u, i) => member(u, u, i));
    return assertSucceeds(rewrite('f', ride({ members: six, maxRiders: 6, capacityStatus: 'FULL' }),
      (r) => rename(r, 'f', 'Frank')));
  });
  test('rider cannot rename the rider in the 6th position', () => {
    const six = ['alice', 'b', 'c', 'd', 'e', 'f'].map((u, i) => member(u, u, i));
    return assertFails(rewrite('b', ride({ members: six, maxRiders: 6, capacityStatus: 'FULL' }),
      (r) => rename(r, 'f', 'Mallory')));
  });
  test('rider cannot rename another rider', () =>
    assertFails(rewrite('bob', ride(), (r) => rename(r, 'alice', 'Mallory'))));
  test('name cannot be blank', () => assertFails(rewrite('bob', ride(), (r) => rename(r, 'bob', ''))));
  test('name cannot be huge', () =>
    assertFails(rewrite('bob', ride(), (r) => rename(r, 'bob', 'x'.repeat(201)))));
  test('organizer name cannot be blank', () =>
    assertFails(rewrite('alice', completedRide(), (r) => { r.organizerName = ''; })));
  test('rider cannot change their join time while renaming', () =>
    assertFails(rewrite('bob', ride(), (r) => { r.members[1] = { ...r.members[1], displayName: 'B', acceptedAt: 1 }; })));
  test('rider cannot rename the organizer field', () =>
    assertFails(rewrite('bob', ride(), (r) => { r.organizerName = 'Bob'; })));
  test('rider cannot reorder riders', () =>
    assertFails(rewrite('bob', ride(), (r) => { r.members.reverse(); })));
  test('rider cannot add a rider while renaming', () =>
    assertFails(rewrite('bob', ride(), (r) => {
      rename(r, 'bob', 'Bobby');
      r.members.push(member('mallory', 'M', 9));
    })));
  test('non-member cannot rename anyone', () =>
    assertFails(rewrite('dave', ride(), (r) => rename(r, 'bob', 'X'))));
  test('rider renames themself and their share on a completed ride', () =>
    assertSucceeds(rewrite('bob', completedRide(), (r) => {
      rename(r, 'bob', 'Bobby');
      r.shares.bob = { ...r.shares.bob, displayName: 'Bobby' };
    })));
  test('rider cannot rename someone else\'s share', () =>
    assertFails(rewrite('bob', completedRide(), (r) => {
      r.shares.carol = { ...r.shares.carol, displayName: 'X' };
    })));
  test('rider cannot change their share amount while renaming', () =>
    assertFails(rewrite('bob', completedRide(), (r) => {
      rename(r, 'bob', 'Bobby');
      r.shares.bob = { ...r.shares.bob, displayName: 'Bobby', amountCents: 1 };
    })));
  // Paying is only allowed as a shares-only write; a rename may not smuggle a status change.
  test('rider cannot mark their share Paid inside a rename', () =>
    assertFails(rewrite('bob', completedRide(), (r) => {
      rename(r, 'bob', 'Bobby');
      r.shares.bob = { ...r.shares.bob, displayName: 'Bobby', status: 'PAID', paidAt: 7000 };
    })));
  test('organizer renames themself everywhere on a completed ride', () =>
    assertSucceeds(rewrite('alice', completedRide(), (r) => {
      rename(r, 'alice', 'Ali');
      r.organizerName = 'Ali';
      r.shares.alice = { ...r.shares.alice, displayName: 'Ali' };
    })));
  test('organizer renames themself on a cancelled ride', () =>
    assertSucceeds(rewrite('alice', ride({
      members: [member('alice', 'Alice', 1)], activityStatus: 'CANCELLED', capacityStatus: 'NOT_APPLICABLE',
    }), (r) => {
      rename(r, 'alice', 'Ali');
      r.organizerName = 'Ali';
    })));
});

// ------------------------------------------------------------------ join requests
describe('join requests', () => {
  const request = (over = {}) => ({
    rideId: 'r1', requesterId: 'dave', requesterName: 'Dave', organizerId: 'alice',
    message: 'hi', createdAt: 1000, respondedAt: null, status: 'PENDING', ...over,
  });
  async function rewriteRequest(uid, mutate, over = {}) {
    await seed('rides/r1', ride());
    await seed('joinRequests/q1', request(over));
    const next = request(over);
    mutate(next);
    return setDoc(doc(db(uid), 'joinRequests/q1'), next);
  }
  test('requester renames their pending request', () =>
    assertSucceeds(rewriteRequest('dave', (q) => { q.requesterName = 'Davey'; })));
  test('requester cannot rename an already answered request', () =>
    assertFails(rewriteRequest('dave', (q) => { q.requesterName = 'Davey'; }, { status: 'REJECTED', respondedAt: 2000 })));
  test('requester cannot blank their name', () =>
    assertFails(rewriteRequest('dave', (q) => { q.requesterName = ''; })));
  test('requester cannot accept their own request', () =>
    assertFails(rewriteRequest('dave', (q) => { q.status = 'ACCEPTED'; })));
  test('requester cannot rename and edit the message together', () =>
    assertFails(rewriteRequest('dave', (q) => { q.requesterName = 'Davey'; q.message = 'changed'; })));
  test('another student cannot rename the request', () =>
    assertFails(rewriteRequest('erin', (q) => { q.requesterName = 'X'; })));
  test('organizer accepts the request', () =>
    assertSucceeds(rewriteRequest('alice', (q) => { q.status = 'ACCEPTED'; q.respondedAt = 2000; })));
  test('organizer rejects the request', () =>
    assertSucceeds(rewriteRequest('alice', (q) => { q.status = 'REJECTED'; q.respondedAt = 2000; })));
  test('organizer cannot move the request to another requester', () =>
    assertFails(rewriteRequest('alice', (q) => { q.requesterId = 'erin'; })));
  test('requester deletes (cancels) a pending request', async () => {
    await seed('joinRequests/q1', request());
    await assertSucceeds(deleteDoc(doc(db('dave'), 'joinRequests/q1')));
  });
});

// ------------------------------------------------------------------ users
describe('users', () => {
  const user = { uid: 'bob', fullName: 'Bob', nickname: '', email: 'bob@usc.edu', uscId: '1000000002', gradYear: 2027, publicRideIds: [] };
  test('user updates their nickname', async () => {
    await seed('users/bob', user);
    await assertSucceeds(updateDoc(doc(db('bob'), 'users/bob'), { nickname: 'Bobby' }));
  });
  test('user cannot change their USC email', async () => {
    await seed('users/bob', user);
    await assertFails(updateDoc(doc(db('bob'), 'users/bob'), { email: 'evil@usc.edu' }));
  });
  test('user cannot edit someone else\'s profile', async () => {
    await seed('users/bob', user);
    await assertFails(updateDoc(doc(db('alice'), 'users/bob'), { nickname: 'X' }));
  });
  test('signed-in users can read profiles', async () => {
    await seed('users/bob', user);
    await assertSucceeds(getDoc(doc(db('alice'), 'users/bob')));
  });
});
