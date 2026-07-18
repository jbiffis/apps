# Caddie

An Android golf companion for Garmin watches — a self-hosted alternative to the
Garmin Golf app. Reads the golf FIT files your watch records, and aims to sync
them straight off the watch over Bluetooth (Gadgetbridge-style, no Garmin
Connect account required).

## Features

- **Rounds list** — every imported round with score, putts, distance walked and
  average heart rate.
- **Full scorecard** — classic 18-hole card with yards, handicap/stroke index,
  par, score (colour-coded eagle/birdie/bogey/double+) and putts per hole,
  front/back/total rows.
- **Hole-by-hole view** — satellite map of each hole showing your individual
  shot locations: numbered markers at each shot's start point, shot lines,
  your walked GPS track, and the pin position. Shot list with club and
  distance in yards.
- **Shot-by-shot view** — Garmin-style *drawn* hole map (flat vector
  rendering, no satellite imagery) built from OpenStreetMap course polygons:
  fairways, greens, bunkers, tees, water and trees. Step through each shot
  with distance bubbles on every segment, see its lie (tee/fairway/rough/
  bunker/green — detected by point-in-polygon against the course map) and its
  result (fairway hit, missed left/right, short), **reassign the club**, or
  **delete phantom shots** the watch invented (AutoShot loves fake putts).
  Course geometry is fetched from the Overpass API automatically on import
  and cached in the database; a retry button appears if the download failed.
- **Club stats** — per-club average / median / longest distance, left–right
  miss percentages measured against the shot→pin target line, short/long
  distribution for approach shots, and a dispersion scatter plot. When the
  course is mapped on OSM, tee shots on par 4/5s also get true **driving
  accuracy**: % fairways hit and a breakdown of misses (left/right/short/
  bunker/water) measured against the actual fairway polygons.
- **Bag** — the watch only records opaque club IDs; name them once here
  (Driver, 7 Iron, …) and every screen uses your names.
- **Watch sync (experimental)** — direct BLE link to the watch speaking the
  GFDI protocol (COBS framing + CRC-16), with a live protocol log. See status
  below.

## Getting data in

Three ways:

1. **Garmin Connect cloud sync (recommended).** Keep the watch paired with the
   Garmin Connect app as usual. In Caddie's *Watch* tab, sign in with your
   Garmin account: Caddie talks to Garmin's cloud (same SSO → OAuth1 → OAuth2
   flow as the `garth` library) and pulls golf scorecards + shot locations
   from the golf-community API, plus the original ACTIVITY .fit (GPS track)
   from the download service. No Bluetooth conflict, nothing to plug in.
   Notes: two-factor Garmin accounts aren't supported yet; cloud scorecards
   are JSON (Garmin consumes the SCORE .fit at upload), so course
   rating/slope and per-hole pin positions come through only partially — pins
   are approximated from where your final putt finished, and hole pars fall
   back to "–" if Garmin's response omits them.

2. **File import.** Plug the watch into a computer (or use the
   watch's USB mass storage via OTG) and grab:
   - `GARMIN/Scorecards/SCORE_*.fit` — scorecard, hole info, shots, clubs
   - `GARMIN/Activity/*.fit` — the matching golf activity (GPS track, HR)

   Then *Rounds → Import FIT*. Files exported by Gadgetbridge work too.
   Importing the SCORE file alone is enough for the scorecard, shots and club
   stats; the ACTIVITY file adds the walked track and heart rate.

3. **Bluetooth sync (experimental).** Only possible if the watch is NOT
   paired with Garmin Connect (a watch holds one BLE host at a time — this is
   the same constraint Gadgetbridge has). *Watch* tab → scan → tap your watch →
   *List golf files*. The GFDI transport layer (COBS, CRC, framing, ANT-FS
   directory parsing, file download state machine) is implemented, but modern
   Garmin firmware may demand an encrypted auth handshake that isn't
   implemented yet. The screen logs every frame so the protocol can be
   iterated against a real watch. File import is the reliable fallback.

## Building

Open `caddie/` in Android Studio (it will use the checked-in Gradle wrapper),
or from the command line with an Android SDK:

```
cd caddie
./gradlew :app:assembleDebug        # APK at app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest    # parser tests run against real FIT samples
```

`minSdk 26`, `targetSdk 34`, Kotlin + Jetpack Compose + Room + osmdroid
(Esri World Imagery satellite tiles — no API key needed).

Two real vivoactive 5 files (a round at The Marshes Golf Club) ship in
`app/src/main/assets/samples/`; the unit tests parse them, and the empty
Rounds screen offers a one-tap sample import.

## How the FIT parsing works

Garmin does not publish its golf messages in the FIT SDK profile, so
`fit/GolfFit.kt` documents what was reverse-engineered from vivoactive 5
SCORE files (`file_id.type = 38`):

| mesg | meaning | fields |
|------|---------|--------|
| 190 | round summary | 1=course name, 8/9/10=front/back/total par, 11=tee name, 12=slope, 13=distance walked (dm), 20=total putts, 21=course rating (f32), 3=round start (FIT ts) |
| 191 | player | 0=name, 2/3/4=front/back/total score |
| 192 | hole score | 253=finished ts, 1=hole, 2=strokes, 3=strokes excl. putts (so putts = f2 − f3) |
| 193 | hole info | 0=hole, 1=length (cm), 2=par, 3=stroke index, 4/5=pin lat/lon (semicircles) |
| 194 | shot | 253=ts, 1=hole, 2/3=start lat/lon, 4/5=end lat/lon, 7=club ID (0 = putt/no club) |

Cross-checks that validate this mapping on the sample round: hole pars sum to
36/36, strokes sum to the 52/51 player totals, `Σ(f2−f3) = 42` matches the
round-summary putts field, and per-club average distances are plausible
(longest-average club ≈ 207 yd = driver).

The generic decoder (`fit/FitReader.kt`) is ~200 lines, dependency-free, and
exposes every message by raw global/field number, so the ACTIVITY file's
standard `session`/`record` messages are read with the same code.

## Project layout

```
app/src/main/java/dev/jbiffis/caddie/
  fit/    FitReader.kt (generic decoder), GolfFit.kt (golf messages)
  data/   Db.kt (Room), Repository.kt (import + miss geometry),
          Lie.kt (point-in-polygon lie detection, fairway miss classifier),
          Overpass.kt (OpenStreetMap course geometry fetch)
  data/garmin/  GarminAuth.kt (SSO/OAuth), GarminClient.kt (Connect API),
          GarminGolfImport.kt (cloud scorecard JSON → Room)
  ble/    Cobs.kt, Crc16.kt, Gfdi.kt (protocol), GarminBleClient.kt (GATT)
  ui/     Rounds, Scorecard, Hole (satellite map), ShotMap (drawn hole view),
          Stats, Clubs, Sync screens (Compose)
app/src/test/  parser + framing + lie-detection tests (real sample files)
```

## Known limitations / next steps

- BLE auth: newer Garmin firmware negotiates an encrypted session (GFDI
  message 5051). Until that's implemented, syncing may stop after the initial
  handshake on some watches — use file import.
- Club IDs come from the watch; if you re-order your bag in Garmin Golf the
  IDs stay stable, but a new club gets a new ID you'll need to name.
- Multi-player scorecards: only player 0 (you) is imported.
- Shot end-positions are where the watch detected the *next* swing, so putts
  and penalty drops can look odd — same limitation as Garmin Golf's AutoShot.
