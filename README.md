# TrojanRideshare — Team 23 (CSCI 310, Project 2.4)

Android app (Java) that lets USC students post shared rides, search for rides near where
they start and where they are going, request to join, manage riders, split the final cost,
and review their ride history and monthly summary.

It follows the Project 2.2 design: a closed layered client (Presentation → Application Logic
→ Data Access, plus a shared Domain Model & Utilities component). Accounts and data are stored
in the team's shared Firebase project (Firebase Authentication and Cloud Firestore), with
Firestore Security Rules enforcing who may write what.
The Project 2.3 implementation document lists every place the code departs from the 2.2
design and why. The Project 2.4 testing document describes every test case; see
[Running the tests](#running-the-tests) below for how to run them.

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

Open the project folder (the one containing this README) in Android Studio and run the `app`
configuration on the Pixel 4 (API 33, Google APIs) emulator. No other setup is needed: `app/google-services.json`
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

## Running the tests

The project has two kinds of tests:

| Kind | Tool | Folder | Needs |
|---|---|---|---|
| White-box (unit) | JUnit | `app/src/test/java/com/team23/trojanrideshare/service/` | Nothing extra; runs on the computer |
| Black-box (UI) | Espresso | `app/src/androidTest/java/com/team23/trojanrideshare/ui/` | A running emulator or device (we used Pixel 4, API 33) |

The unit tests run the real services against an in-memory database with a fixed clock. Most UI
tests use a fresh on-device test database for every test. The account tests
(`ui/auth/SignUpAndLoginTest`, `ui/profile/ProfileAndSharingTest`) instead sign up through the
real app, so they use the shared Firebase project and need an internet connection; each run
creates new test accounts with unique emails.

Requirements: JDK 17 or newer and the Android SDK. Opening the project once in Android Studio
sets this up and creates `local.properties`; alternatively, set `ANDROID_HOME`.

### From the command line

From the project folder, with `make` (macOS/Linux):

| Command | What it does |
|---|---|
| `make test-unit` | All white-box tests |
| `make test-ui` | All black-box tests (start the emulator first) |
| `make test-all` | Both |
| `make coverage` | White-box tests plus a JaCoCo line/branch coverage report |
| `make build` | Build the debug app |

To run one test class, add `CLASS=`, for example
`make test-ui CLASS=com.team23.trojanrideshare.ui.search.RideSearchTest`.

Without `make` (or on Windows, using `gradlew.bat`), run the Gradle wrapper directly:

```
./gradlew :app:testDebugUnitTest                    # white-box tests
./gradlew :app:connectedDebugAndroidTest            # black-box tests (emulator running)
./gradlew :app:createDebugUnitTestCoverageReport    # coverage report
```

Reports are written to:

- Unit tests: `app/build.nosync/reports/tests/testDebugUnitTest/index.html`
- UI tests: `app/build.nosync/reports/androidTests/connected/debug/index.html`
- Coverage: `app/build.nosync/reports/coverage/test/debug/index.html`

### From Android Studio

Right-click `app/src/test/java` (white-box) or `app/src/androidTest/java` (black-box) and
choose **Run 'Tests in …'**. You can also right-click a single test file or test method to run
only that one. Start the emulator before running the black-box tests.

### Test files by feature

| Feature | White-box (`app/src/test/...`) | Black-box (`app/src/androidTest/...`) |
|---|---|---|
| Accounts & profiles | `service/AccountServiceTest` | `ui/auth/SignUpAndLoginTest`, `ui/profile/ProfileAndSharingTest` |
| Ride creation & management | `service/RideServiceTest` | `ui/ride/CreateRideFormTest`, `ui/ride/RideManagementTest` |
| Ride search & requests to join | `service/RideSearchEngineTest`, `service/JoinRequestServiceTest` | `ui/search/RideSearchTest`, `ui/myrides/JoinRequestFlowTest` |
| Cost splitting | `service/CostSplitServiceTest` | `ui/cost/CostSharingTest` |
| Ride history & monthly summary | `service/RideHistoryServiceTest` | `ui/history/RideHistoryUiTest` |
