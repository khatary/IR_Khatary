# PUBG GYRO

Native Android sensor utility (Kotlin) with a 3-second splash screen and dark neon gaming interface.

## Features in this build
- Hardware gyroscope and accelerometer detection
- Live X/Y/Z sensor readout
- In-app motion preview with sensitivity, smoothing, and calibration
- PUBG Mobile launcher button
- Settings/info screen
- GitHub Actions workflow builds a debug APK and uploads it as an artifact

## Build
Open in Android Studio and sync Gradle, then Build > Build APK(s).

GitHub Actions also builds on every push to main. Download the APK from the workflow run's Artifacts section.

## Limitation
A regular Android app cannot create a virtual system gyroscope that PUBG Mobile receives on a device without gyro hardware. Emulator movement works only inside PUBG GYRO; it does not modify PUBG files or inject sensor data.
