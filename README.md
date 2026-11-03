# TrojanRideshare — Team 23 (CSCI 310, Project 2.3)

Android app (Java) that lets USC students post shared rides, search for rides near where
they start and where they are going, request to join, manage riders, split the final cost,
and review their ride history and monthly summary.

It follows the Project 2.2 design: a closed layered client (Presentation → Application Logic
→ Data Access, plus a shared Domain Model & Utilities component) on top of Firebase
Authentication and Cloud Firestore, with Firestore Security Rules enforcing who may write what.
The implementation document (`../Team 23 - Project 2.3.pdf`) lists every place the code
departs from the 2.2 design and why.

## Project layout

| Package | Architectural component (2.2) |
|---|---|
| `ui` (`MainActivity`, `AppContainer`) | Navigation Shell |
| `ui.auth`, `ui.profile` | Account & Profile UI |
| `ui.ride` | Ride Post UI |
| `ui.search`, `ui.myrides` | Search & Requests UI |
| `ui.cost` | Cost Split UI |
| `ui.history` | History & Summary UI |
| `ui.common` | Shared presentation helpers (new in 2.3) |
| `service` | Account Service, Ride Service, Ride Search Engine, Join Request Service, Cost Split Service, Ride History Service |
| `data`, `data.firestore` | Repositories |
| `gateway` | Auth Gateway, Location Gateway |
| `domain`, `domain.util` | Domain Model & Utilities |
| `firestore.rules` | Cloud Firestore Security Rules |

## Running the app

Open the `TrojanRideshare` folder in Android Studio and run the `app` configuration on the
Pixel 4 (API 33, Google APIs) emulator. The app needs a Firebase backend; pick one:

### Option A — Firebase Local Emulator Suite (default, no cloud setup)

When `app/google-services.json` is **not** present, the app connects to the emulators at
`10.0.2.2` (your computer, as seen from the Android emulator) using the demo project
`demo-trojanrideshare`.

1. Install Node.js and Java 21, then `npm install -g firebase-tools`.
2. From this folder run `./run-emulators.sh` and wait for "All emulators ready".
   Emulator UI: http://127.0.0.1:4000 (browse users and Firestore documents).
3. Run the app. The Log In screen shows "Data: Firebase Emulator (10.0.2.2)".

Data stays available while the emulators run (closing and reopening the app keeps everything)
and is saved to `emulator-data/` when you stop them with Ctrl+C.

To use a physical phone instead, add `firebase.emulatorHost=<your computer's LAN IP>` to
`local.properties` and start the emulators with `host` set to `0.0.0.0` in `firebase.json`.

### Option B — a real Firebase project

1. In the Firebase console create a project, enable **Authentication → Email/Password**,
   and create a **Cloud Firestore** database.
2. Add an Android app with package name `com.team23.trojanrideshare` and put the downloaded
   `google-services.json` in `app/`.
3. Deploy the Security Rules: `firebase deploy --only firestore:rules --project <project-id>`.
4. Rebuild. The build detects `google-services.json` and the app uses the real project
   (Profile → bottom of the page shows which backend is in use).

`local.properties` overrides: `firebase.useEmulator=true|false`, `firebase.emulatorHost=…`.

### Locations

Origins and destinations come from the free OpenStreetMap geocoder Photon
(https://photon.komoot.io), biased toward USC; no key is needed. To use Google Places instead,
add `places.apiKey=<key>` to `local.properties` (the key needs the Places API enabled).

### Demo clock (debug builds only)

Profile → **Demo clock** moves the app clock forward (+1 h, +1 day, +30 days, Reset). Rides
must be posted for the future, so this lets a demo show a departed ride being marked
Completed, the 3-hour leave rule, and a later month's summary without waiting. The offset is
stored on the device and applies to every account used on it.

## Tests

```bash
./gradlew testDebugUnitTest
```

74 JUnit 4 tests cover the Application Logic layer (validation, ride editing, search filters
and sorts, join eligibility, leaving and organizer handoff, equal/custom splits, payments and
flags, nickname changes, history and monthly summary) against an in-memory repository, plus
`Ride`, `Money`, `GeoUtils`, `SafeLogger`.

### Security Rules tests

```bash
cd rules-tests && npm install && npm test
```

63 tests run `firestore.rules` in its own Firestore emulator (port 8181) and check that every
write the app makes is allowed and that forbidden writes (editing someone else's ride or name,
changing a share amount, editing an ended ride, …) are rejected. Each test writes the exact
document shape `FirestoreMapper` produces. Needs Node.js and `firebase-tools`, like the app's
emulator setup.

## Suggested demo video script

Use two or more accounts (log out and in, or two emulators).

1. **Sign up** with invalid data to show every error (non-`@usc.edu` email, 9-digit USC ID,
   past graduation year), then a valid account. Show a duplicate email / USC ID is rejected.
2. **Profile**: name, nickname (edit it), locked email and USC ID, class year.
3. **Create Ride**: location suggestions for origin and destination, date, time, max riders
   (2–6), expected cost, note. The post shows On-Time and "Open – 1 of N riders".
4. As a second user, **Search**: origin/destination keywords + radius, date, time window,
   "Available seats only", sort by time or proximity. Open the ride → **Request to Join** with
   a message. Show the disabled button reasons (own ride, full, already requested).
5. As the organizer, **My Rides**: accept/reject requests; fill the ride to show "Full" and the
   "This ride is full" message; **Edit Ride** (title, time, seats, cost, note) → Delayed;
   **Leave** as organizer to show
   the handoff to the most recently accepted rider; **Cancel Ride** to show pending requests
   becoming Rejected.
6. Use the **Demo clock** to pass departure, then **Mark as Completed**: actual cost, Equal
   (choose who pays leftover cents) or Custom amounts, preview, save.
7. Riders **mark their share Paid**; the organizer **flags** an unpaid rider, who sees the
   notice on their profile until they pay.
8. **History**: search by location + radius, filter by status and by rider, newest/oldest.
   **Monthly Summary**: pick a month with rides and one without ("No activity!…").
9. Make a ride public on your profile and open your profile from another account.
