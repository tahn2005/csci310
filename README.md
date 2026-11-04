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
