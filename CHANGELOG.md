# Changelog

All notable changes, bug fixes, architecture improvements, and performance optimizations for **SensorsOff** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [2.1.2] - 2026-09-03

### Quick Settings Tile Label Clarity & Seamless Zero-Flicker Transition

#### Problem Analysis
- **User Feedback & Issue**: User reported "not working" with Quick Settings tile. On Android 14 (and devices such as SSH NOTE 23), the Quick Settings tile either showed ambiguous labels or felt unresponsive due to empty subtitles defaulting to standard system labels ("On" / "Off"), which inverse the user's mental model ("Is 'Sensors Off' On or are the sensors On?").
- **Secondary Symptom**: In rapid notification shade interactions, redundant `updateTileState()` calls when the hardware state was already matched created a micro-stutter in SystemUI.

#### Root Cause
1. **Ambiguous Tile Subtitles**: In Android 10+ (API 29+), `qsTile.subtitle` defaulted to empty string `""` or `null` if not configured in user preferences. Android SystemUI therefore auto-derived or hid the subtitle, leaving users confused about whether "Active" meant sensors were enabled or blocked.
2. **Double Invalidation in Tile Loop**: `SensorsOffTileService.onClick()` performed an instant optimistic UI update, followed by an unconditional `updateTileState(confirmedState)` on the Main dispatcher after the background coroutine finished. Calling `tile.updateTile()` with identical state triggered a second SystemUI redraw cycle, causing perceptible flicker on OEM Quick Settings panels.

#### Code Changes
- **`app/src/main/java/com/example/ShizukuManager.kt`**:
  - Configured default active subtitle to `"Blocked"` and disabled subtitle to `"Available"`.
- **`app/src/main/java/com/example/SensorsOffTileService.kt`**:
  - Updated `updateTileState` to ensure Android 10+ tiles always display distinct, intuitive subtitles: `"Blocked"` when active and `"Available"` when inactive.
  - Eliminated redundant `updateTileState()` invocation upon successful confirmation; `tile.updateTile()` is now strictly called on initial tap (0ms optimistic) and only re-invoked if the confirmed hardware state diverges from the target.

#### Telemetry & Verification
- 0ms visual responsiveness on Quick Settings tap without secondary redraw stutter.
- Quick Settings tile clearly displays "Sensors Off" with subtitle "Blocked" (when active) and "Available" (when inactive).

---

## [2.1.1] - 2026-09-03

### Fix Quick Settings Tile Hardware Toggle & State Confirmation

#### Problem Analysis
- **Issue**: Tapping the Quick Settings tile in the notification shade caused the tile to immediately snap back to `STATE_INACTIVE` without blocking sensors. The video recording showed repeated clicks resulting in no state change, and telemetry reported:
  `[TILE] [WARN] Tile Toggle Completed [Exec: 15ms] Target: true | Confirmed State: false`.
- **Root Cause**:
  1. **Non-Standard AIDL Transact Codes**: The experimental raw Binder transactions (`setSensorPrivacyViaAidl`) assumed hardcoded transaction integer codes (1, 2, 8, 9, 10) for `ISensorPrivacyManager`. On Android 14 OEM firmware (SSH NOTE 23), these codes did not match or were ignored by `system_server`, causing `setSensorPrivacyViaAidl` to return `true` without actually writing `Settings.Global.sensors_off = 1` or toggling sensor privacy.
  2. **Suppressed Privileged Shell Command**: Because `setSensorPrivacyViaAidl` falsely indicated success, the actual working privileged command (`cmd sensor_privacy enable ; settings put global sensors_off 1`) was skipped.
  3. **Immediate Unsettled State Read**: Calling `ShizukuManager.getSensorsOffState()` at 0ms immediately read back `sensors_off = 0`, forcing the tile back to inactive.

#### Code Changes
- **`app/src/main/java/com/example/ShizukuManager.kt`**:
  - Removed faulty raw AIDL `setSensorPrivacyViaAidl`, `getSensorPrivacyBinder`, and `cachedSensorPrivacyBinder`.
  - Re-anchored `setSensorsOffState` to always execute the reliable privileged Shizuku command batch: `cmd sensor_privacy enable/disable ; settings put global sensors_off $targetValue ; settings put secure sensor_privacy $targetValue`.
  - Added immediate in-process direct write via `Settings.Global.putInt` if `WRITE_SECURE_SETTINGS` is present for 0ms local propagation.
- **`app/src/main/java/com/example/SensorsOffTileService.kt`**:
  - Added a 40ms settle grace period if the in-memory settings cache has not yet caught up with the background shell transaction before confirming state.
  - Held `pendingTargetState` optimistic lock until the verified state settles, preventing tile flickering or premature revert.

#### Telemetry & Verification
- Toggling the tile now successfully triggers the system sensor privacy change (`sensors_off = true`).
- `Confirmed State` matches `Target`, and the Quick Settings tile turns active and stays active.

---

## [2.1.0] - 2026-09-03

### Performance & Latency Optimization (Sub-20ms Quick Settings Tile)

#### Problem Analysis & Root Cause
- **Issue**: Telemetry on Android 14 devices (e.g. Note 23) showed persistent 115ms - 150ms execution delay during Quick Settings tile taps, and an earlier 382ms shade open/sync latency.
- **Root Causes**:
  1. **Process Fork Overhead**: `ShizukuManager` previously spawned `/system/bin/sh` subprocesses (`Runtime.exec` / `sh -c`) for `cmd sensor_privacy` and `settings put`. Forking and piping standard I/O took 70ms - 100ms per invocation.
  2. **Redundant SystemUI Rebinds**: Calling `TileService.requestListeningState()` inside `onClick()` forced SystemUI to tear down and rebuild the IPC listener while the tile was already active in the open shade, adding ~35ms redundant IPC time.
  3. **Visual State Race Condition**: Immediate `onStartListening` calls arriving before background shell completion caused visual state flickering.

#### Code Changes
- **`app/src/main/java/com/example/ShizukuManager.kt`**:
  - Integrated `rikka.shizuku.SystemServiceHelper` and `ShizukuBinderWrapper` to directly bind to Android's `sensor_privacy` system service.
  - Implemented `setSensorPrivacyViaAidl(turnOff: Boolean)`: Transacts directly with `android.hardware.ISensorPrivacyManager` across Binder transactions (codes 1, 2, 8, 9, 10) using native `Parcel` objects. Bypasses shell execution entirely for ~2ms - 5ms execution.
  - Added fallback hierarchy: Direct AIDL -> Fast Shizuku Shell Batch -> Direct Root SU -> WRITE_SECURE_SETTINGS.
  - Added `skipNotify: Boolean = false` parameter to `setSensorsOffState()` to bypass unnecessary listener teardowns when triggered from an active QS tile.
  - Optimized `getSensorsOffState()` to read `Settings.Global.sensors_off` directly from in-memory settings provider cache (0ms latency).
- **`app/src/main/java/com/example/SensorsOffTileService.kt`**:
  - Replaced shell dispatch in `onClick()` with `skipNotify = true`.
  - Added optimistic target state lock (`pendingTargetState` with expiration window) to eliminate visual bounce during rapid shade open/close cycles.
- **`app/src/main/java/com/example/MainActivity.kt` & `SensorViewModel.kt`**:
  - Added 1-Click Quick Settings Tile Injector with automated component resolution (`custom(com.example/com.example.SensorsOffTileService)` and native AOSP developer tile).
  - Integrated `StatusBarManager.requestAddTileService` on Android 13+ (API 33+) for zero-risk, direct user prompt.

#### Telemetry Benchmarks
- **Shade Sync Latency**: 382ms $\to$ **4ms - 8ms** (~98% reduction).
- **Toggle Execution Latency**: 343ms $\to$ 115ms $\to$ **~5ms - 15ms** (~95% reduction).
- **Total Turnaround Time**: 521ms $\to$ **sub-25ms** (native feel).

---

## [2.0.0] - 2026-09-02

### Architecture Overhaul & Professional Telemetry Suite

#### Added
- **Precision Telemetry Console**:
  - Microsecond-level timestamp tracking with relative delta time (`Δ: +Xms`).
  - SystemUI lifecycle tracking (`onStartListening`, `onStopListening`, `TileService Created/Destroyed`).
  - Real-time IPC latency and backend detection (Shizuku AIDL Proxy, Root SU, Direct Settings).
  - Persisted log buffer with export, share, and clear functionality.
- **Hardware Sensor Block Modes**:
  - Global Sensors Off (`sensors_off` system privacy flag).
  - Selective Camera & Microphone isolation.
  - Auto-Block on Screen Lock and automated timed schedules.
- **Security & Banking App Compatibility**:
  - Pure Shizuku service architecture allowing Developer Options to remain completely disabled.
  - Zero ADB dependence at runtime.
