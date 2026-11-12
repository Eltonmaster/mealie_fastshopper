# FastShopper

An Android companion app for [Mealie](https://mealie.io) that makes adding and ticking off shopping list entries fast, even with bad signal in the shop.

**Status:** early development. Working: connect to a server, pick lists, default list, list grouped by label, tap to check, long press to edit (name, amount, container, note, label), swipe to delete with undo, offline queue with background sync, add sheet with food autocomplete and remembered containers. checked items are cleared automatically 12 hours after checking.

## Install

Download the latest `fastshopper-x.y.z.apk` from [Releases](../../releases/latest) and open it on your phone (allow installing from unknown sources). Each release also has a `.sha256` file if you want to check the download.

Want the newest unreleased build? Every push to `main` and every pull request builds a debug APK: open the run under **Actions → CI** and download `fastshopper-debug-apk`. Debug and release builds are signed with different keys, so switching between them means uninstalling first.

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

## CI/CD

- **CI** (`.github/workflows/ci.yml`) runs on every pull request and push to `main`: Android lint, unit tests, a debug APK, and a release build signed with a throwaway key so signing problems show up before a release.
- **Release** (`.github/workflows/release.yml`) runs when you push a tag like `v0.2.0`. It runs lint and tests, builds a release APK signed with your key, and publishes it to GitHub Releases with generated notes. Tags with a suffix (`v0.2.0-beta.1`) become pre-releases.
- **Dependabot** opens weekly grouped PRs for Gradle dependencies and GitHub Actions.

### Releasing

The version comes from the tag: `v1.2.3` becomes versionName `1.2.3` and versionCode `10203` (minor and patch must stay below 100).

```
git tag v0.2.0
git push origin v0.2.0
```

### One-time signing setup

1. Create a keystore (keep it and the passwords somewhere safe: without it you can't ship updates that install over an existing release):

   ```
   keytool -genkeypair -v -keystore fastshopper-release.jks -alias fastshopper \
     -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Base64-encode it: `base64 -w0 fastshopper-release.jks` on Linux, `base64 -i fastshopper-release.jks` on macOS, or `[Convert]::ToBase64String([IO.File]::ReadAllBytes("fastshopper-release.jks"))` in PowerShell.
3. In the repository on GitHub, go to **Settings → Secrets and variables → Actions** and add these repository secrets:

   | Secret | Value |
   | --- | --- |
   | `KEYSTORE_BASE64` | the base64 output from step 2 |
   | `KEYSTORE_PASSWORD` | the keystore password |
   | `KEY_ALIAS` | `fastshopper` (or the alias you chose) |
   | `KEY_PASSWORD` | the key password (same as the keystore password unless you set a different one) |

To build a signed release locally, export `SIGNING_STORE_FILE`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD` and run `./gradlew assembleRelease`. Without them the release APK is built unsigned.

## Tech

Kotlin, Jetpack Compose (Material 3), Retrofit + kotlinx.serialization, DataStore. Talks to Mealie's REST API (`/api/households/shopping/...`), tested against Mealie v3.
