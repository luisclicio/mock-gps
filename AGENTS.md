# AGENTS.md — Android Mock GPS

## 1. Project Overview

Build a minimal, privacy-focused Android application that allows users to simulate a geographic location using Android's official mock location APIs.

The application must support:

- Manual latitude and longitude input.
- Interactive map selection.
- Apply and Stop buttons.
- Optional small coordinate variations (GPS jitter).
- Continuous location updates while simulation is active.
- Completely Dockerized development and build environment.
- Installation on a compatible physical Android device.

The project is intended for local development and location-related app testing.

**Project name:** Mock GPS  
**Application ID:** `dev.local.mockgps`  
**Language:** Kotlin  
**Platform:** Android  
**Minimum Android version:** Android 8.0 (API 26)  
**Test device:** A physical Android device compatible with the app's supported Android versions.

## 2. Core Principles

1. Keep the implementation minimal and maintainable.
2. Prefer native Android APIs over third-party dependencies.
3. Never require Android Studio.
4. Never require Java, Gradle, Kotlin, or Android SDK installed on the host.
5. Use Docker for all build and development tooling.
6. Do not require a specific code editor.
7. Do not implement root-dependent functionality.
8. Do not collect analytics or transmit user coordinates to a backend.
9. Keep the application usable without an account.
10. Clearly indicate when mock location is active.
11. Never attempt to conceal Android's mock-location status or bypass application detection mechanisms.

## 3. Technology Stack

### Android application

- Kotlin
- Native Android SDK
- Android Gradle Plugin
- Gradle Wrapper
- Android LocationManager
- Android Foreground Service
- Android SharedPreferences or DataStore
- MapLibre Native Android for map interaction

Use native Android Views with XML layouts. Avoid Jetpack Compose for the initial implementation to reduce build dependencies and complexity.

### Development environment

- Docker
- Docker Compose
- Eclipse Temurin JDK
- Android SDK Command-Line Tools
- Gradle Wrapper
- ADB on the host, only for USB installation and debugging

The host must not need Android Studio, Java, Gradle, or the Android SDK.

Pin compatible, supported versions of the JDK, Gradle, Android Gradle Plugin, Kotlin, SDK platform, and build tools. Do not use floating versions or `latest` for reproducible build dependencies.

## 4. Development Environment

Create a Docker-based development environment with:

- `Dockerfile`
- `compose.yaml`
- `.dockerignore`
- `.gitignore`
- Gradle Wrapper
- `scripts/build.sh`
- `scripts/test.sh`
- `scripts/install.sh`
- `README.md`

The Docker image must include everything necessary to compile and test the Android application.

Use Docker volumes to persist Gradle caches.

The source code must be mounted into the container so changes made on the host are immediately available.

### Required commands

Build the Docker environment:

`docker compose build`

Build a debug APK:

`docker compose run --rm android ./gradlew assembleDebug`

Run unit tests:

`docker compose run --rm android ./gradlew testDebugUnitTest`

Run lint:

`docker compose run --rm android ./gradlew lintDebug`

Install on the connected device using host ADB:

`adb install -r app/build/outputs/apk/debug/app-debug.apk`

Do not require USB passthrough into Docker.

Ensure that all Docker commands work when executed from the repository root.

## 5. Application Interface

The application must contain a single main screen.

### 5.1 Coordinate inputs

Provide two numeric inputs:

- Latitude
- Longitude

Requirements:

- Support negative values.
- Support decimal values.
- Accept both comma and dot as decimal separators.
- Validate latitude between -90 and 90.
- Validate longitude between -180 and 180.
- Display meaningful validation errors.
- Persist the last selected coordinates locally.

Default coordinates may be empty until the user selects a location.

### 5.2 Interactive map

Display an interactive map below the coordinate inputs.

Requirements:

- Support dragging and zooming.
- Allow selecting coordinates by tapping the map.
- Display a marker at the selected location.
- Update latitude and longitude inputs when the map is tapped.
- Update the marker when coordinates are entered manually.
- Center the map on the selected coordinates.
- Keep the map and input fields synchronized.

Use MapLibre with a documented, configurable tile source.

Do not assume that public OpenStreetMap tile servers provide unlimited usage. Respect the tile provider's terms, attribution requirements, and rate limits.

The app's location simulation must work offline when coordinates are entered manually. The map may require internet access to download tiles.

Do not include Google Maps API keys or paid services.

### 5.3 Action buttons

Provide two buttons.

**Apply**

- Validate the selected coordinates.
- Check whether the application is authorized as the mock location provider.
- Start the location simulation.
- Begin periodic location updates.
- Update the UI status.
- Start a foreground service if required.

**Stop**

- Stop periodic updates.
- Remove only the test providers registered by the application.
- Stop the foreground service.
- Restore normal system location-provider behavior.
- Update the UI status.

Stopping must be safe to execute multiple times.

### 5.4 Status indicator

Display one of these states:

- Inactive
- Starting
- Active
- Stopping
- Permission required
- Error

When active, display the selected base coordinates and whether coordinate variation is enabled.

## 6. Coordinate Variation (GPS Jitter)

Provide a switch labeled:

**Small coordinate variations**

Default: OFF.

When enabled, the application should simulate small, smooth variations around the selected base coordinates.

This feature is intended for testing location accuracy and applications that respond to position changes.

### 6.1 Configuration

Initial defaults:

- Maximum radius: 5 meters
- Update interval: 1 second
- Variation mode: Smooth bounded movement

Keep these values as constants in a dedicated configuration class.

### 6.2 Variation algorithm

Implement a bounded, temporally correlated random movement model.

Requirements:

1. Treat the selected coordinates as the fixed center.
2. Generate small random movement increments in meters.
3. Apply smoothing to avoid abrupt coordinate changes.
4. Limit the resulting position to the configured radius.
5. Convert local meter offsets to latitude and longitude.
6. Handle high-latitude coordinates safely.
7. Never allow invalid latitude or longitude values.
8. Keep the original coordinates unchanged.
9. Reset variation state when a new base location is applied.
10. When the switch is disabled, publish the exact base coordinates.

Prefer a bounded random-walk or mean-reverting process rather than independent random points.

Use a deterministic random seed in unit tests.

### 6.3 Limitations

Do not implement:

- Mock-location detection bypasses.
- Root-based system modifications.
- Tampering with `Location.isMock()`.
- Hooks that modify other applications.
- Concealment of developer settings.
- Claims that GPS jitter makes a simulated location indistinguishable from a real one.

Android test locations remain identifiable as mock locations.

## 7. Android Location Engine

Create a dedicated component named `MockLocationController`.

Responsibilities:

- Register the GPS test provider.
- Enable the test provider.
- Publish valid Location objects.
- Update locations periodically.
- Stop publishing.
- Remove registered test providers.
- Handle permission and provider errors.

Use Android's official `LocationManager` APIs.

Relevant methods:

- `addTestProvider()`
- `setTestProviderEnabled()`
- `setTestProviderLocation()`
- `removeTestProvider()`

Use the appropriate API overload for the device's Android version.

Every published Location must include:

- Latitude
- Longitude
- Accuracy
- Current wall-clock timestamp
- Elapsed realtime timestamp

Publish updated timestamps on every location update.

Do not modify unrelated system settings.

Do not assume that all applications use the same location provider. Document limitations involving fused location providers and device-specific behavior.

## 8. Foreground Service

Implement a dedicated foreground service named `MockLocationService`.

Requirements:

- Continue publishing coordinates while the screen is locked.
- Display a persistent notification while active.
- Allow stopping simulation from the notification.
- Respect modern Android foreground-service requirements.
- Declare the correct foreground-service type and permissions.
- Handle service termination gracefully.
- Avoid unnecessary battery usage.
- Stop publishing when the user explicitly stops the service.

Do not silently restart simulation after a device reboot.

Do not automatically resume simulation without clear user intent.

The agent must validate service behavior against the selected Android target SDK and supported Android versions.

## 9. Permissions and Privacy

Request only permissions necessary for the selected implementation.

The app must be selected manually in:

Settings → Developer Options → Select mock location app

If not selected, show a helpful explanation and offer to open the relevant Android settings screen when supported.

Never request root access.

No advertising SDKs.

No analytics.

No user accounts.

No remote database.

No background upload of coordinates.

Internet access is permitted only for loading map tiles when the map is enabled.

Store application settings locally.

Do not log precise coordinates unnecessarily.

## 10. Suggested Architecture

Use a simple separation between UI, location simulation, and coordinate calculations.

Suggested components:

- `MainActivity`: screen and user interaction.
- `MockLocationController`: Android mock location API integration.
- `MockLocationService`: foreground execution.
- `CoordinateJitter`: pure coordinate variation algorithm.
- `CoordinateValidator`: input validation.
- `SettingsRepository`: local settings persistence.
- `MapController`: map selection and marker synchronization.

Avoid introducing dependency injection frameworks, databases, or complex architecture patterns unless clearly justified.

Use Kotlin coroutines only when they materially simplify asynchronous operations.

## 11. Suggested Project Structure

```text
mock-gps/
├── AGENTS.md
├── README.md
├── Dockerfile
├── compose.yaml
├── .dockerignore
├── .gitignore
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── gradle/
│   └── wrapper/
├── scripts/
│   ├── build.sh
│   ├── test.sh
│   └── install.sh
└── app/
    ├── build.gradle.kts
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/dev/local/mockgps/
        │   │   ├── MainActivity.kt
        │   │   ├── location/
        │   │   │   ├── MockLocationController.kt
        │   │   │   └── MockLocationService.kt
        │   │   ├── geo/
        │   │   │   ├── CoordinateJitter.kt
        │   │   │   └── CoordinateValidator.kt
        │   │   ├── map/
        │   │   │   └── MapController.kt
        │   │   └── settings/
        │   │       └── SettingsRepository.kt
        │   └── res/
        │       ├── layout/
        │       │   └── activity_main.xml
        │       └── values/
        │           └── strings.xml
        └── test/
            └── java/dev/local/mockgps/
                ├── CoordinateJitterTest.kt
                └── CoordinateValidatorTest.kt
```

## 12. Testing Requirements

### Unit tests

Test coordinate validation:

- Valid coordinates.
- Invalid latitude.
- Invalid longitude.
- Empty input.
- Decimal comma.
- Decimal dot.
- Non-numeric input.
- NaN and infinity.

Test coordinate variation:

- Disabled variation returns exact base coordinates.
- Enabled variation remains within configured radius.
- Generated coordinates are valid.
- Repeated updates do not produce unbounded drift.
- Deterministic tests using seeded randomness.
- Behavior near poles and longitude boundaries.

### Android integration tests

Test:

- Missing mock-location authorization.
- Starting simulation.
- Applying new coordinates while active.
- Stopping simulation.
- Repeated start/stop cycles.
- Foreground service behavior.
- Screen locking.
- Notification stop action.
- Provider cleanup after errors.

Do not claim physical-device tests have passed unless they were actually executed.

## 13. Error Handling

Handle at least:

- Invalid coordinate input.
- Missing mock-location authorization.
- Failure to register the test provider.
- Failure to publish a location.
- Foreground-service startup failure.
- Missing internet connection for map tiles.
- Map initialization failure.
- App lifecycle changes.

Never silently swallow an error that prevents simulation from functioning.

Provide understandable error messages in Brazilian Portuguese.

## 14. User Experience

All visible application text must be in Brazilian Portuguese.

Keep the interface clean and minimal.

Prefer a single-screen layout.

Avoid unnecessary animations.

Use clear labels and accessible touch targets.

The map must not prevent manual coordinate entry when offline.

The Apply and Stop buttons must always reflect the current simulation state.

The coordinate variation switch must clearly show whether it is enabled.

## 15. Build and Delivery

The agent must provide:

1. Complete source code.
2. Docker environment.
3. Gradle Wrapper.
4. Working debug APK build command.
5. Unit tests.
6. README with host setup instructions.
7. ADB installation instructions for a compatible Android device.
8. Generic Android device configuration instructions.
9. Known limitations.

Expected APK output:

`app/build/outputs/apk/debug/app-debug.apk`

The APK must not require root access.

Do not introduce proprietary services unless explicitly approved.

## 16. Implementation Order

Implement incrementally.

### Phase 1 — Environment

- Create the Gradle project.
- Configure Docker.
- Verify dependency resolution.
- Build a minimal Android application.
- Run unit tests.

### Phase 2 — Manual GPS

- Create latitude and longitude inputs.
- Implement input validation.
- Implement MockLocationController.
- Implement Apply and Stop.
- Verify operation on the physical device.

### Phase 3 — Coordinate Variation

- Implement bounded coordinate jitter.
- Add the enable/disable switch.
- Add deterministic unit tests.
- Verify that disabling jitter restores exact coordinates.

### Phase 4 — Interactive Map

- Integrate MapLibre.
- Configure a suitable map tile provider.
- Implement tap-to-select.
- Synchronize marker and inputs.
- Handle map loading failures.

### Phase 5 — Background Operation

- Implement foreground service.
- Add persistent notification.
- Support stopping from the notification.
- Test screen locking and app lifecycle behavior.

### Phase 6 — Final Validation

- Run unit tests.
- Run lint.
- Build APK.
- Document manual test steps.
- Review permissions.
- Update README.

## 17. Acceptance Criteria

The project is complete when:

- The entire Android build runs inside Docker.
- No host Android SDK, Gradle, Kotlin, or JDK installation is required.
- The project source can be edited with a standard code editor.
- The APK builds using a documented Docker Compose command.
- The APK installs on a compatible physical Android device.
- The user can enter coordinates manually.
- The user can select coordinates on a map.
- The selected coordinates remain synchronized between map and inputs.
- Apply starts the mock location simulation.
- Stop terminates simulation and cleans up the provider.
- The variation switch enables and disables bounded GPS jitter.
- Simulation can continue with the screen locked through a foreground service.
- Errors are handled visibly.
- Unit tests pass.
- The application does not require root.
- The application does not collect or transmit coordinates to an application backend.

## 18. Instructions for Coding Agents

When working on this repository:

1. Read this AGENTS.md before modifying code.
2. Inspect the existing project before making changes.
3. Follow the implementation phases in order.
4. Prefer simple, readable Kotlin code.
5. Keep classes focused and small.
6. Avoid unnecessary abstractions.
7. Do not add libraries without a clear reason.
8. Use official Android APIs whenever possible.
9. Keep build dependencies pinned.
10. Always run the available build and tests after making changes.
11. Report commands executed and their actual results.
12. Never claim a build or test succeeded without executing it.
13. Document incomplete features and known limitations.
14. Do not change project scope without explicit approval.
15. Prioritize a working APK over architectural sophistication.
16. Maintain user-facing documentation in English and Brazilian Portuguese. Keep `README.md` and `docs/device-test-checklist.md` in English, with matching `README.pt-BR.md` and `docs/device-test-checklist.pt-BR.md` translations. Keep their language links and instructions aligned. Keep this agent guidance file in English.

**Primary objective:** deliver a small, maintainable, Docker-built Android mock GPS application that is easy for a beginner to understand, modify, compile, and install.
