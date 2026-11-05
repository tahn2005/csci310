# TrojanRideshare — Team 23 (CSCI 310, Project 2.3)

Android app (Java) that lets USC students post shared rides, search for rides near where
they start and where they are going, request to join, manage riders, split the final cost,
and review their ride history and monthly summary.

It follows the Project 2.2 design: a closed layered client (Presentation → Application Logic
→ Data Access, plus a shared Domain Model & Utilities component). Accounts and data are stored
in the team's shared Firebase project (Firebase Authentication and Cloud Firestore), with
Firestore Security Rules enforcing who may write what.
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
| `data`, `data.firestore`, `data.local` | Repositories (Firestore, or the on-device database) |
| `gateway` | Auth Gateway, Location Gateway |
| `domain`, `domain.util` | Domain Model & Utilities |
| `firestore.rules` | Cloud Firestore Security Rules |

## Running the app

Open the `csci310-main` folder in Android Studio and run the `app` configuration on the
Pixel 4 (API 33, Google APIs) emulator. No other setup is needed: `app/google-services.json`
connects the app to the team's Firebase project `trojanrideshare23`, and the Log In screen
shows "Data: Firebase project trojanrideshare23". The emulator or device needs an internet
connection. Every device running the app shares the same accounts, rides, requests, and cost
splits, so students on different devices can find and join each other's rides.

### Firebase project setup

The project `trojanrideshare23` is already set up. To recreate it (for example, in a new
Firebase account):

1. In the Firebase console create a project, enable **Authentication → Email/Password**,
   and create a **Cloud Firestore** database (Standard edition, default database ID).
2. Add an Android app with package name `com.team23.trojanrideshare` and replace
   `app/google-services.json` with the downloaded file.
3. In **Firestore → Rules**, paste the contents of `firestore.rules` and publish.
4. Rebuild. The Log In screen then shows "Data: Firebase project <id>".

If `app/google-services.json` is missing, the app falls back to a database on the device
("Data: This device (built-in database)"), which is not shared between devices.
Developers can also point the app at the Firebase Local Emulator Suite by adding
`firebase.useEmulator=true` to `local.properties` and starting the emulators with the Firebase
CLI (see the comment at the top of `app/build.gradle`).

### Locations

Origins and destinations come from the free OpenStreetMap geocoder Photon
(https://photon.komoot.io), biased toward USC; no key is needed. To use Google Places instead,
add `places.apiKey=<key>` to `local.properties` (the key needs the Places API enabled).

### Demo clock (debug builds only)

Profile → **Demo clock** moves the app clock forward (+1 h, +1 day, +30 days, Reset). Rides
must be posted for the future, so this lets a demo show a departed ride being marked
Completed, the 3-hour leave rule, and a later month's summary without waiting. The offset is
stored on the device and applies to every account used on it.
