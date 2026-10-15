# Android Device Test Checklist

[English](device-test-checklist.md) | [Português (Brasil)](device-test-checklist.pt-BR.md)

Use a test device with **Mock GPS** installed and device location enabled. Android marks these positions as mock locations. Stop the simulation after testing.

## Preparation

- [ ] Install the APK using the [README instructions](../README.md#install-on-an-android-device).
- [ ] Grant the permissions requested by the app.
- [ ] Select **Mock GPS** in **Developer options → Select mock location app**.
- [ ] Open a test app that displays the received position and lets you refresh it.

## Test flows

- [ ] **Authorization missing:** Temporarily deselect Mock GPS in Developer options, return to the app, and tap **Aplicar** (Apply). The app should show **Permissão necessária** (Permission required) and offer to open Developer options. Select Mock GPS again before continuing.
- [ ] **Validation and map:** Enter `0,0001` and `-0,0001`; confirm that the decimal comma is accepted. Tap another point on the map and check that the fields and marker move together. Try latitude `91` and confirm that the range error appears.
- [ ] **Start and update:** Apply `0.0001`, `-0.0001`; confirm the **Ativo** (Active) status, persistent notification, and position in the test app. Change the coordinates while active and apply again; confirm that the new position appears without stopping the service.
- [ ] **Variation:** Turn **Pequenas variações de coordenadas** (Small coordinate variations) off, apply, and check that the published point is the selected center. Turn it on, apply, and observe smooth updates within 5 m without continuous drift away from the center.
- [ ] **Screen locked:** With simulation active, lock the screen for at least one minute. Unlock it and confirm that the service and notification remain active and the test app still receives updates.
- [ ] **Stop from notification:** Tap **Parar** (Stop) in the notification. It should disappear, the app should show **Inativo** (Inactive), and the test app should stop receiving simulated positions.
- [ ] **Stop from app and repeat:** Start and stop from the app at least five times. Confirm each start works and the notification disappears after every stop.
- [ ] **Failure and recovery:** While active, deselect Mock GPS in Developer options. The app should stop the simulation and show an error if Android revokes the provider. Select Mock GPS again, tap **Aplicar**, and confirm that a new cycle works.
- [ ] **Offline use:** Turn on airplane mode, enter coordinates manually, and confirm that the controls remain usable even if the map does not load. Finish the test and restore connectivity.

## Results

Record the device manufacturer and model, Android version, and result for each item. This checklist has not yet been run on a physical device; do not mark items as passed without verifying them on the device.
