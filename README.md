# TrojanRideshare — Team 23 (CSCI 310, Project 2.3)

Android app (Java) that lets USC students post shared rides, search for rides near where
they start and where they are going, request to join, manage riders, split the final cost,
and review their ride history and monthly summary.

It follows the Project 2.2 design: a closed layered client (Presentation → Application Logic
→ Data Access, plus a shared Domain Model & Utilities component). Data is stored in a database
on the device by default, or in Firebase Authentication and Cloud Firestore when a Firebase
project is connected, with Firestore Security Rules enforcing who may write what.
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
Pixel 4 (API 33, Google APIs) emulator. No other setup is needed: by default the app keeps its
data in a database on the device, and the Log In screen shows
"Data: This device (built-in database)". Accounts, rides, requests, and cost splits stay saved
when the app is closed and reopened. To act as several students, log out and sign up or log in
as another account on the same emulator.

### Optional: a shared Firebase project

To share data between devices, connect a Firebase project:

1. In the Firebase console create a project, enable **Authentication → Email/Password**,
   and create a **Cloud Firestore** database.
2. Add an Android app with package name `com.team23.trojanrideshare` and put the downloaded
   `google-services.json` in `app/`.
3. In **Firestore → Rules**, paste the contents of `firestore.rules` and publish.
4. Rebuild. The Log In screen then shows "Data: Firebase project <id>".

Developers can instead point the app at the Firebase Local Emulator Suite by adding
`firebase.useEmulator=true` to `local.properties` (see the comment at the top of
`app/build.gradle`).

### Locations

Origins and destinations come from the free OpenStreetMap geocoder Photon
(https://photon.komoot.io), biased toward USC; no key is needed. To use Google Places instead,
add `places.apiKey=<key>` to `local.properties` (the key needs the Places API enabled).

### Demo clock (debug builds only)

Profile → **Demo clock** moves the app clock forward (+1 h, +1 day, +30 days, Reset). Rides
must be posted for the future, so this lets a demo show a departed ride being marked
Completed, the 3-hour leave rule, and a later month's summary without waiting. The offset is
stored on the device and applies to every account used on it.
