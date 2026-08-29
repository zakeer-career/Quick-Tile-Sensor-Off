# SensorsOff

<p align="center">
  <img src="app/src/main/res/drawable/ic_app_logo_hero.xml" width="128" height="128" alt="SensorsOff Logo" />
</p>

<p align="center">
  <strong>Complete Hardware Privacy Control & Quick Settings Tile for Android</strong>
</p>

<p align="center">
  <a href="https://www.android.com"><img src="https://img.shields.io/badge/Platform-Android-3DDC84.svg?style=flat&logo=android" alt="Platform"></a>
  <a href="https://github.com/LinerSRT/SensorsOff"><img src="https://img.shields.io/badge/Release-v2.0-brightgreen.svg?style=flat" alt="Release"></a>
  <a href="https://developer.android.com/about/versions/10"><img src="https://img.shields.io/badge/API-29%2B-blue.svg?style=flat" alt="API"></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20%2F%20Material%203-4285F4.svg?logo=jetpackcompose" alt="Compose"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache_2.0-blue.svg" alt="License"></a>
</p>

---

## Overview

**SensorsOff** gives you direct control over your Android device's hardware sensors. Toggle system-level sensor privacy with a single tap from your Quick Settings shade, run live telemetry diagnostics, and protect your privacy against unauthorized background tracking—powered by **Shizuku**, **Root (su)**, or **ADB Secure Settings**.

---

## App Screenshots & Interface

<div align="center">

| **Dashboard & Master Switch** | **Live Telemetry & Diagnostics** | **Quick Settings Tile Customizer** |
|:---:|:---:|:---:|
| <pre>┌───────────────────────────┐<br/>│ 🛡️ <b>SensorsOff</b>            │<br/>│                           │<br/>│  ┌─────────────────────┐  │<br/>│  │  <b>MASTER SENSORS OFF</b> │  │<br/>│  │       [ ON ]        │  │<br/>│  │  All Sensors Blocked│  │<br/>│  └─────────────────────┘  │<br/>│                           │<br/>│  <b>Selective Matrix</b>         │<br/>│  📷 Camera       [OFF]    │<br/>│  🎙️ Microphone   [OFF]    │<br/>│  🧭 Motion/Gyro  [OFF]    │<br/>└───────────────────────────┘</pre> | <pre>┌───────────────────────────┐<br/>│ 📊 <b>Live Sensor Telemetry</b>  │<br/>│                           │<br/>│ <b>Accelerometer</b>             │<br/>│  X: 0.00  Y: 0.00  Z: 0.00│<br/>│  <font color="#4CAF50">● BLOCKED / SILENCED</font>    │<br/>│                           │<br/>│ <b>Gyroscope</b>                 │<br/>│  X: 0.00  Y: 0.00  Z: 0.00│<br/>│  <font color="#4CAF50">● BLOCKED / SILENCED</font>    │<br/>│                           │<br/>│ <b>Proximity & Light</b>         │<br/>│  Reading: 0.0 lux         │<br/>└───────────────────────────┘</pre> | <pre>┌───────────────────────────┐<br/>│ 🎛️ <b>Tile Customizer</b>        │<br/>│                           │<br/>│  <b>Quick Settings Label</b>     │<br/>│  [ Sensors Off          ] │<br/>│                           │<br/>│  <b>Click Behavior Mode</b>      │<br/>│  (•) Global Sensors Off   │<br/>│  ( ) Camera + Mic Only    │<br/>│                           │<br/>│  <b>Tile Icon Style</b>          │<br/>│  [ Shield ] [ Slash ] [ X]│<br/>│                           │<br/>│  <i>✓ Active Pre-Warmed</i>    │<br/>└───────────────────────────┘</pre> |

</div>

---

## How It Works (Video & Visual Flow)

```
                       HOW SENSORSOFF WORKS
                       ═════════════════════

  [ Quick Settings Shade ]  ───( Tap Tile )───►  [ SensorsOff Service ]
            │                                             │
            │ (Real-time Feedback)                        │
            ▼                                             ▼
  ┌───────────────────┐                         ┌───────────────────┐
  │  Tile State: ON   │ ◄──( System UI Sync )── │  Privilege Engine │
  └───────────────────┘                         └─────────┬─────────┘
                                                          │
                    ┌─────────────────────────────────────┴─────────────────────────────────────┐
                    ▼                                     ▼                                     ▼
           ┌─────────────────┐                   ┌─────────────────┐                   ┌─────────────────┐
           │   Shizuku API   │                   │    Root (su)    │                   │ Secure Settings │
           │  (AIDL Binder)  │                   │(Batched Shell)  │                   │ (WRITE_SECURE)  │
           └────────┬────────┘                   └────────┬────────┘                   └────────┬────────┘
                    │                                     │                                     │
                    └─────────────────────────────────────┼─────────────────────────────────────┘
                                                          ▼
                                            ┌───────────────────────────┐
                                            │   SensorPrivacyManager    │
                                            │  (AOSP cmd sensor_privacy)│
                                            └─────────────┬─────────────┘
                                                          ▼
                                            ╔═══════════════════════════╗
                                            ║   ALL HARDWARE SENSORS    ║
                                            ║   BLOCKED & SILENCED      ║
                                            ║   • Camera & Mic Off      ║
                                            ║   • Gyroscope & Accel Off ║
                                            ╚═══════════════════════════╝
```

### Video Demonstration:
> **Watch the Quick Settings Tile in Action:**
>
> [![SensorsOff Demonstration](https://img.shields.io/badge/Demo_Video-Watch_Walkthrough-blue?style=for-the-badge&logo=youtube)](https://github.com/LinerSRT/SensorsOff)
>
> *(A video guide demonstrating pairing with Shizuku, pulling down the Quick Settings shade, and tapping the Sensors Off tile to instantly silence hardware sensors in real-time.)*

---

## Key Features

- **Persistent Quick Settings Tile**:
  - Configured with `ACTIVE_TILE` metadata and boot receivers so the tile remains active across device reboots and never shows `STATE_UNAVAILABLE`.
  - Instant optimistic UI feedback when clicked from the notification shade.
  - Choose between Global Sensors Off or Selective Camera & Microphone privacy modes.

- **Multi-Backend Privilege Support**:
  - **Shizuku (Recommended)**: Elevated Android Binder system integration without root.
  - **Root (su)**: Fast superuser execution for rooted smartphones.
  - **ADB Secure Settings**: Standard permission-based toggling.

- **Live Diagnostic Telemetry**:
  - Real-time monitors for Accelerometer, Gyroscope, Magnetometer, Proximity, and Ambient Light sensors to verify privacy isolation.

- **Boot & Background Reliability**:
  - Listens for `BOOT_COMPLETED` and `USER_PRESENT` to maintain synchronization across all Android power-saving and background states.

---

## Permission Setup

### Option 1: Shizuku (Recommended)
1. Install and launch **Shizuku** on your device.
2. Pair via Wireless Debugging or launch with Root.
3. Open **SensorsOff** and grant permission when prompted.

### Option 2: ADB Command Line
Grant the secure settings permission using a one-time ADB command:
```bash
adb shell pm grant com.aistudio.sensorsoff.pomujq android.permission.WRITE_SECURE_SETTINGS
```

---

## Acknowledgments & Credits

Special thanks and credit to **[LinerSRT](https://github.com/LinerSRT)** for the original project and inspiration:
- **Author**: [LinerSRT](https://github.com/LinerSRT)
- **Repository**: [https://github.com/LinerSRT/SensorsOff](https://github.com/LinerSRT/SensorsOff)

---

## License

Distributed under the Apache License, Version 2.0. See `LICENSE` for details.
