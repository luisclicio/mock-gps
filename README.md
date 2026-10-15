# Mock GPS

[English](README.md) | [Português (Brasil)](README.pt-BR.md)

A native Android app for testing mock locations. Enter coordinates manually, choose a point on the map, enable smooth variations of up to 5 meters, and keep location updates running with a foreground service.

The project uses Kotlin, Android Gradle Plugin 8.11.1, Gradle 8.13, Kotlin 2.2.21, Android SDK 36, and MapLibre Native Android 11.8.0. Docker provides the JDK and Android SDK. The host needs Docker Compose and, only to install the app on a device, Android Platform Tools (`adb`). The app uses English by default and switches to Portuguese when the device language is Portuguese.

## Build and test

From the repository root, build the Docker image once:

```sh
docker compose build
```

Then build the APK and run unit tests and lint:

```sh
./scripts/build.sh
./scripts/test.sh
```

Equivalent Gradle commands are:

```sh
docker compose run --rm android ./gradlew assembleDebug
docker compose run --rm android ./gradlew testDebugUnitTest
docker compose run --rm android ./gradlew lintDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. The Docker volume `gradle-cache` preserves dependency caches and the debug signing key between runs. Keep this volume if you want later debug APKs to update an existing debug installation. If the volume is removed, Gradle creates a new key and Android requires the previous installation to be uninstalled before installing the new APK. The repository source is mounted into the container for builds. On the first run, the script downloads the Gradle Wrapper from the project's official repository and verifies its pinned SHA-256; Gradle also verifies the distribution checksum. The image build downloads the Android tools pinned in the `Dockerfile`.

To change the map style, set `mapStyleUrl` in the Gradle command, for example:

```sh
docker compose run --rm android ./gradlew assembleDebug -PmapStyleUrl=https://tiles.openfreemap.org/styles/liberty
```

## GitHub Actions and releases

Pull requests to `main` and pushes to `main` run unit tests, lint, and a debug build in Docker. After a successful push to `main`, the workflow also builds a signed release APK and publishes a GitHub Release named `v1.0.<run-number>` with the APK attached. The `versionCode` uses the workflow run number so each release can update the previous one.

To keep the same signature across releases, create one release key and keep a secure backup outside the repository. The `.secrets/` directory is ignored by Git. From the repository root, generate the key once:

```sh
docker compose build android
mkdir -p .secrets
docker compose run --rm --user "$(id -u):$(id -g)" \
  --volume "$PWD/.secrets:/secrets" android \
  keytool -genkeypair -v -storetype JKS \
    -keystore /secrets/mock-gps-release.jks \
    -alias mockgps -keyalg RSA -keysize 2048 -validity 10000
```

In the GitHub repository, open **Settings → Secrets and variables → Actions** and add these repository secrets:

- `ANDROID_SIGNING_KEYSTORE_BASE64`: the keystore file `.secrets/mock-gps-release.jks` encoded as a single line with `base64 .secrets/mock-gps-release.jks | tr -d '\n'`.
- `ANDROID_SIGNING_STORE_PASSWORD`: the keystore password.
- `ANDROID_SIGNING_KEY_ALIAS`: `mockgps`.
- `ANDROID_SIGNING_KEY_PASSWORD`: the key password.

Do not share the key or passwords or add them to Git. Losing the key prevents Android from accepting updates signed with a replacement key. Existing debug installations use a different signature; installing the first release over one requires uninstalling it once, which deletes its local data. Later releases use the same signature and can update the installation normally. The publishing job uses `GITHUB_TOKEN` with `contents: write`; if an organization policy blocks this permission, enable workflow write permissions in the repository settings.

## Install on an Android device

If `adb install -r` returns `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, the installed APK was signed with a different key. Note your selected coordinates and app preferences, uninstall the old app once with `adb uninstall dev.local.mockgps`, then install the new APK. Uninstalling deletes the app's local data. Later debug builds reuse the key in the `gradle-cache` volume and can update the installation normally.

1. Enable Developer options in the device settings. The path and the name of **Build number** vary by manufacturer and Android version; it usually needs to be tapped seven times. Confirm the device lock when prompted.
2. In **Developer options**, enable **USB debugging** and connect the device to the computer. Approve the RSA key on the device.
3. Build and install the APK with host `adb`:

   ```sh
   ./scripts/build.sh
   ./scripts/install.sh
   ```

   Or run `adb install -r app/build/outputs/apk/debug/app-debug.apk` directly. ADB runs on the host; USB passthrough to Docker is not needed.
4. Open **Mock GPS**, grant the requested location and notification permissions, and enable device location.
5. In **Developer options → Select mock location app**, choose **Mock GPS**. Menu paths and names vary by manufacturer and Android version.
6. Enter coordinates or tap the map, then press **Aplicar** (Apply). To stop, press **Parar** (Stop) in the app or in the persistent notification.

The app does not require root. Android continues to mark these positions as mock locations; the app does not try to hide or change that status.

## Privacy and limitations

- Selected coordinates and preferences are stored locally in `SharedPreferences`. The app has no account, analytics, ads, backend, or remote database, and it does not read the device's real location.
- Location permission is requested because Android requires it for this foreground service. Android 8–11 also requires precise location permission to register a test provider; the manifest limits that permission to those versions. The app does not use it to obtain the user's location.
- Internet access is used to load the map style, fonts, and tiles. The default provider, [OpenFreeMap](https://openfreemap.org/quick_start/), does not require an API key. Map tile requests reveal the displayed area to the provider, so do not use the map if you do not want to make those requests. Manual coordinate entry works offline.
- The public map provider is offered “as is”; [OpenFreeMap's terms](https://openfreemap.org/tos/) and availability may change. Map attribution remains visible. For production or high traffic, configure an appropriate provider and follow its terms and limits.
- The app uses Android's official test provider API. Apps can detect mock locations with `Location.isMock()`, and some apps use fused location providers or their own policies, so behavior varies by app and device.
- Coordinate variations use a smooth random walk limited to 5 m and update every second. To maintain this interval while the screen is locked, the service holds a partial CPU wake lock while active, which uses battery. It releases the lock when stopped and does not resume the simulation after a reboot.
- Mock provider selection, the foreground service, notifications, and provider integration must be checked on a physical device. This repository does not claim that physical-device tests have passed.

## Development

The app uses XML Views and separates the screen (`MainActivity`), `LocationManager` integration (`MockLocationController`), background execution (`MockLocationService`), coordinate validation and variation, and map interaction. The map uses MapLibre Native Android with the OpenFreeMap Liberty style. Latitude and longitude inputs accept either a dot or comma as the decimal separator.

Unit tests cover input boundaries, NaN/infinity, disabled variations, distance limits, deterministic movement, poles, and the international date line. Permission, service, notification, and test-provider flows still require manual verification on a physical Android device.

See the [Android device test checklist](docs/device-test-checklist.md). For the Portuguese version, see the [roteiro de testes em português](docs/device-test-checklist.pt-BR.md).
