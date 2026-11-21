# Pokémon Binder Tracker

An Android app (Kotlin + Jetpack Compose) for tracking Pokémon cards kept in binders.
Each person signs in with Google and gets their own shelf of binders.

## What works so far

- **Sign in with Google** (Firebase Auth), or "Use on this phone only" without an account
- **Shelf** of binders, each drawn as a spine with its name and size
- **New binder**: name, spine colour, and size (2 × 2, 3 × 3 or 3 × 4)
- **Binder** screen, with delete (asks first)
- Binders are saved on the phone in a Room (SQLite) database
- **Permissions screen** before first use: Camera (for scanning) and Install apps (for updates)
- **Self-updating**: each time the app opens it checks for a newer version and offers "Update now"

Coming next, in order: card index and search (TCGdex), then camera page scanning.
See the plan and checklist artifacts linked in the handoff notes.

## Project layout

```
app/src/main/java/com/hybrid87sg/bindertracker/
  MainActivity.kt      app start-up and the list of screens (navigation)
  AppViewModel.kt      holds the data; screens ask it to do things
  auth/AuthManager.kt  Google + Firebase sign-in
  data/                Binder table, database and queries (Room)
  ui/screens/          one file per screen
  ui/components/       shared pieces: spine, shelf plank, buttons
  ui/theme/            colours and text styles
app/src/test/          screenshot tests (draw each screen to a PNG)
keystore/              the fixed debug signing key (see below)
```

## Building

Needs JDK 17+ and the Android SDK.

```
./gradlew assembleDebug        # makes app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # saves screen pictures to app/build/outputs/roborazzi/
```

GitHub Actions builds the app on every push and publishes it as a pre-release called
**Latest debug build vX.Y.Z** (tag `debug-latest`). On the repo's GitHub page, open
**Releases** and download `binder-tracker-v0.1.N.apk` onto your phone. Each push replaces it.

## Versions and updates

- The version is `appVersionBase` from `gradle.properties` (now `0.1`) plus the GitHub
  Actions run number, e.g. `0.1.7`. Change `appVersionBase` to `0.2` for a new milestone.
- Builds on your own computer are called `0.1-dev` and always see GitHub's version as newer.
- Next to the APK, the release has `latest.json`, for example
  `{"versionCode":7,"versionName":"0.1.7","apkUrl":"https://.../binder-tracker-v0.1.7.apk"}`.
  The app reads it each time it opens; if `versionCode` is higher than its own, it offers the update.
- Updates install over the old app, keeping your binders, only because every build is
  signed with the same key in `keystore/`. Don't change that key.
- The repo must be **public** for the app to read the release without a GitHub login.

## Fixed debug key

Debug builds are signed with `keystore/binder-debug.keystore` (password `android`,
alias `androiddebugkey`). It is committed on purpose: Google sign-in only works for
apps signed with a key registered in Firebase, so every build must use the same one.
It is only for debug builds; never use it for a Play Store release.

SHA-1 to register in Firebase:

```
06:C0:FD:D1:E9:3B:92:C1:59:04:13:67:9E:C3:9A:AB:28:7A:85:91
```

## Connecting Firebase (one-time, in the Firebase console)

Until this is done the app builds and runs, but only "Use on this phone only" works.

1. Open your Firebase project (the one Doodle Frame uses) → **Add app** → **Android**.
   Package name: `com.hybrid87sg.bindertracker`
2. On that app's settings, **Add fingerprint** and paste the SHA-1 above.
3. **Authentication → Sign-in method** → enable **Google**.
4. **Firestore Database** → **Create database** (used for syncing in a later step).
5. Download **google-services.json** and put it in the `app/` folder, then commit it.
