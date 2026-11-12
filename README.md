# FastShopper

An Android companion app for [Mealie](https://mealie.io) that makes adding and ticking off shopping list entries fast, even with bad signal in the shop.

**Status:** early development. Working: connect to a server, pick lists, default list, list grouped by label, tap to check, swipe to delete with undo, offline queue with background sync. Next: adding items.

## Install

Every push builds a debug APK on GitHub Actions. Open the latest run under **Actions → Build**, download `fastshopper-debug-apk`, unzip it and install the APK on your phone (allow installing from unknown sources). Tagged versions (`v0.1.0`, …) also attach the APK to a GitHub release.

## Connect

1. In Mealie, open **Profile → API Tokens** and create a token.
2. Open the app, enter your server URL (e.g. `https://mealie.example.com`) and paste the token.
3. Pick a list. Tap the star to open that list automatically on launch.

The token is stored encrypted with the Android Keystore.

## Build locally

Requires JDK 17 and the Android SDK (API 35).

```
./gradlew assembleDebug
```

## Tech

Kotlin, Jetpack Compose (Material 3), Retrofit + kotlinx.serialization, DataStore. Talks to Mealie's REST API (`/api/households/shopping/...`), tested against Mealie v3.
