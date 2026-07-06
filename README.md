# Speed Compass

<p align="center">
  <img src="speed-round.png" alt="Speed Compass round icon" width="160" />
</p>

<p align="center">
  A simple Android GPS bike speedometer and compass with route tracking.
</p>

## What It Does

Speed Compass is a minimal bike dashboard for Android. It keeps the display intentionally clean: a near-black background, a big two-digit speed readout, and a ring-style compass that is easy to glance at while riding.

The app supports:

- Portrait layout with speed above compass.
- Landscape layout with speed and compass side by side.
- MPH / km/h speed toggle.
- GPS course heading while moving, with phone sensor heading fallback.
- Foreground-service route recording.
- GPX export for recorded rides.
- Pixel/API 26+ compatible adaptive launcher icon using `speed-round.png`.

## Build

Requirements:

- JDK 17
- Android SDK with API 36 installed

Build a debug APK:

```bash
./gradlew :app:assembleDebug
```

Run unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Permissions

Speed Compass requests location permissions for live speed, compass course, and route logging. When route recording is active, it uses a foreground service notification so tracking can continue while the app is backgrounded.

## Project Shape

- `app/src/main/java/com/speedcompass/MainActivity.kt` - Compose dashboard, permissions, sensor heading, GPX share flow.
- `app/src/main/java/com/speedcompass/TrackingService.kt` - foreground service for background route recording.
- `app/src/main/java/com/speedcompass/TrackingRepository.kt` - location updates, route state, local point persistence.
- `app/src/main/java/com/speedcompass/GpxWriter.kt` - GPX serialization.
- `app/src/test/java/com/speedcompass/` - JVM tests for formatting, heading logic, and GPX output.

## Status

This is an initial implementation focused on the core bike-computer experience. It does not include maps, ride history browsing, cloud sync, or account features.
