# Pokémon Binder Tracker

An Android app (Kotlin + Jetpack Compose) for tracking Pokémon cards kept in binders.
Each person signs in with Google and gets their own shelf of binders.

## What works so far

- **Sign in with Google** (Firebase Auth, project `pokemon-binder-tracker`), or "Use on this phone only"
- **Shelf** of binders, each drawn as a spine with its name and size
- **New binder**: name, spine colour, and size (2 × 2, 3 × 3 or 3 × 4)
- **Binder** screen, with delete (asks first)
- Binders are saved on the phone in a Room (SQLite) database
- **Permissions screen** before first use: Camera (for scanning) and Install apps (for updates)
- **Self-updating**: each time the app opens it checks for a newer version and offers "Update now"
- **Card index** (#7): all ~24,000 English cards downloaded once from TCGdex, searchable offline
  by name or number ("pikachu", "025/165", "charizard 4"); refreshed weekly
- **Card screen** (#8, #9, #13): picture, set, rarity and market prices from TCGplayer (USD)
  and Cardmarket (EUR), each with its source and last-updated date
- **Add pages** (+ on the binder screen): adds one or more empty pages; you fill each pocket by
  scanning its card, choosing it from the index, or marking it empty
- **Automatic card reading**: when you scan a card, the app reads its name and number
  (ML Kit, on the phone); each pocket shows the official card picture from the card index
- **Recognise by picture**: a small AI picture model (MobileNet via MediaPipe, on the phone) compares
  each card with every official card picture. The picture descriptions are prepared on GitHub by
  `scripts/build_card_embeddings.py` (workflow "Build card pictures list", release `card-pictures`)
  and downloaded once (~20 MB). Unsure pockets offer their 5 best guesses to tap
- **Scan this card** (pocket menu): photograph one card on its own to fill that pocket. The card is
  straightened, then matched by artwork details against the official pictures
- **Online saving**: when signed in with Google, binders are also kept in Cloud Firestore, so they
  come back on any phone you sign in on (page photos stay on the phone that took them)
- **Shared binders**: the owner taps Share on a binder and shows a QR code (or sends the code).
  A friend taps the QR button on their shelf to scan it. The owner picks **Editor** (can add, move
  and remove cards) or **Viewer** (look only) and can change or remove people later

Coming next: prices in SGD (#10–#12),
and page value totals (#38).
See the plan and checklist artifacts linked in the handoff notes.

## Project layout

```
app/src/main/java/com/hybrid87sg/bindertracker/
  MainActivity.kt      app start-up and the list of screens (navigation)
  AppViewModel.kt      holds the data; screens ask it to do things
  auth/AuthManager.kt  Google + Firebase sign-in
  sync/                online saving and sharing (Cloud Firestore)
  data/                database tables and queries (Room): binders, cards, pages, pockets
  tcgdex/              downloads cards and prices from TCGdex
  update/              checks GitHub for new versions and installs them
  ui/screens/          one file per screen
  ui/components/       shared pieces: spine, shelf plank, buttons
  ui/theme/            colours and text styles
app/src/test/          screenshot tests (draw each screen to a PNG)
app/schemas/           saved database descriptions, used to upgrade safely (don't delete)
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

## Firebase (connected)

Google sign-in uses the Firebase project **pokemon-binder-tracker**. Its settings file,
`app/google-services.json`, is committed on purpose: it identifies the project and is not a
password. When the file is present the build switches Firebase on automatically.

If sign-in fails, check in the Firebase console under **Project settings → Your apps →
Binder Tracker**:

- Package name is `com.hybrid87sg.bindertracker`
- **SHA certificate fingerprints** include the SHA-1 above (and ideally the SHA-256:
  `B7:34:2D:CA:44:41:8F:D8:27:C3:C5:9B:52:DF:8E:32:86:A0:66:7E:2D:FE:85:9B:6F:FB:3F:B4:24:29:8F:2B`)
- **Authentication → Sign-in method → Google** is enabled

If you change any of these, download `google-services.json` again and replace the one in `app/`.
## Shared binders (Cloud Firestore)

Online saving and sharing use **Cloud Firestore** in the same Firebase project. It needs
switching on once in the [Firebase console](https://console.firebase.google.com/):

1. Open the project, then **Build → Firestore Database → Create database**.
2. Pick a location close to you (for Singapore: `asia-southeast1`), and **Start in production mode**.
3. Open the **Rules** tab, replace everything there with the contents of `firestore.rules`
   from this repo, and press **Publish**.

The free (Spark) plan is enough. Until Firestore is switched on, the app works as before and the
Account screen says online saving isn't on yet.

What is stored online:

- `binders/{id}`: name, colour, size, the owner, and who else can use it (`memberIds`, `roles`)
- `binders/{id}/pages/{pageId}`: which card is in each pocket
- `invites/{code}`: share codes (which binder, Editor or Viewer, expires after 7 days)

Page photos are not uploaded (that needs Firebase Storage, which is on the paid plan).
