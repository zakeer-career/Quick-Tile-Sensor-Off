# SensorsOff

   <p align="center">
  <img src="https://github.com/user-attachments/assets/5e387daf-d6c9-4e86-a6a8-661a8d81b0c4" width="200" alt="Your Logo"/>
</p>


[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84.svg?style=flat&logo=android)](https://www.android.com)
[![Release](https://img.shields.io/badge/Release-v2.0-brightgreen.svg?style=flat)](https://github.com/LinerSRT/SensorsOff)
[![API](https://img.shields.io/badge/API-29%2B-blue.svg?style=flat)](https://developer.android.com/about/versions/10)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2F%20Material%203-4285F4.svg?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)

**SensorsOff** is an enterprise-grade Android privacy utility and system service management application designed to control hardware sensor privacy states (`SensorPrivacyManager`) across Android 10+ (API 29 to API 34+). 

It delivers zero-latency hardware sensor toggling, real-time diagnostic telemetry, and persistent Quick Settings tile synchronization with multiple privileged backend execution pipelines.

---

## Architecture & System Overview

```
+-----------------------------------------------------------------------+
|                         SensorsOff Client UI                          |
|             (Jetpack Compose • Material 3 • MVVM Architecture)        |
+------------------------------------+----------------------------------+
                                     |
                                     v
+-----------------------------------------------------------------------+
|                    Privileged Execution Pipeline                      |
|  +---------------------+  +--------------------+  +----------------+  |
|  |     Shizuku API     |  |   Root Shell (su)  |  | Secure Settings|  |
|  |  (IPC Binder Proxy) |  |   (Batched Process)|  | (WRITE_SECURE)  |  |
|  +----------+----------+  +---------+----------+  +--------+-------+  |
+-------------|-----------------------|----------------------|----------+
              +-----------------------+----------------------+
                                      |
                                      v
+-----------------------------------------------------------------------+
|                   Android OS Subsystems (AOSP)                        |
|  • SensorPrivacyManager (`cmd sensor_privacy` / Binder Transaction)   |
|  • SystemUI Quick Settings (`ACTIVE_TILE` TileService)                |
|  • Hardware Sensor HAL (Accelerometer, Gyroscope, Magnetometer, Mic)  |
+-----------------------------------------------------------------------+
```

---

## Key Capabilities

- **Zero-Latency Quick Settings Integration**:
  - Implements `ACTIVE_TILE` metadata to ensure the Quick Settings tile is permanently pre-warmed and never marked as `STATE_UNAVAILABLE` by Android `SystemUI`.
  - Bi-directional real-time state synchronization between the app UI, SystemUI shade, and Android `ContentObserver`.
  - Supports configurable tile operation modes (Global Sensors Off, Selective Camera + Microphone privacy, or Custom matrices).

- **Multi-Tiered Privileged Execution**:
  - **Shizuku API Integration**: Executes non-root elevated system commands via Shizuku's privileged AIDL Binder IPC.
  - **Direct Root (su)**: Single-invocation compound process batching for instant hardware state switching.
  - **ADB Secure Settings (`WRITE_SECURE_SETTINGS`)**: Native non-root permission flow.

- **Real-Time Sensor Telemetry & Diagnostics**:
  - Live hardware monitoring for Accelerometer, Gyroscope, Magnetometer, Proximity, Ambient Light, Camera, and Audio subsystems to visually verify privacy isolation.

- **Enterprise Reliability**:
  - Automated system state re-binding via `RECEIVE_BOOT_COMPLETED` and `USER_PRESENT` broadcast listeners to survive device restarts and OEM battery management cycles.

---

## Attribution & Acknowledgments

This project is built upon foundational concepts and designs established by the open-source community.

We gratefully acknowledge and credit:

* **Original Creator & Author**: [LinerSRT](https://github.com/LinerSRT)
* **Upstream Project Repository**: [https://github.com/LinerSRT/SensorsOff](https://github.com/LinerSRT/SensorsOff)

Their initial work on system sensor privacy controls on Android provided the baseline architecture and inspiration for this implementation.

---

## Documentation & Changelog

- **[CHANGELOG.md](CHANGELOG.md)**: Full release notes, problem analyses, root causes, and verification metrics following Keep a Changelog format.
- **[CONVENTIONAL_COMMITS.md](CONVENTIONAL_COMMITS.md)**: Complete ledger of standardized Conventional Commit messages for all releases, formatted for git commit workflows.
- **[PROBLEM_ANALYSIS_ROOT_CAUSE.md](PROBLEM_ANALYSIS_ROOT_CAUSE.md)**: Comprehensive repository of all deep technical problem analyses, operating system root causes, and engineered resolutions.

---

## Privileged Permission Setup

### Option 1: Shizuku (Recommended)
1. Install and launch **Shizuku** on the device.
2. Pair via Wireless Debugging or start via Root / ADB.
3. Grant permission to **SensorsOff** when prompted.

### Option 2: ADB Command Line
Grant elevated secure settings permissions via Android Debug Bridge:
```bash
adb shell pm grant com.aistudio.sensorsoff android.permission.WRITE_SECURE_SETTINGS
```

---

## License

Distributed under the Apache License, Version 2.0. See `LICENSE` for more information.
