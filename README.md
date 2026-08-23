# AutoRecorder

Gesture-triggered background audio/video recorder for Android. Runs as a foreground service and starts recording — camera + microphone — invisibly, when you trigger it with a gesture. Everything is configurable at runtime from the settings screen.

Built for **personal use** on your own device (body-cam style capture, evidence recording, note-taking). Android enforces visible OS indicators (mic/camera dot, service notification) — this app does not and cannot hide those.

## Features

- **Foreground service** with `microphone|camera` type, silent low-priority notification, `stopWithTask=false` (keeps running when the app is swiped away)
- **No preview, no UI during recording** — video records to an offscreen surface while the screen is on or locked
- **Recording**: video + audio via CameraX (`Movies/AutoRecorder`, MediaStore), audio-only fallback if the camera is unavailable
- **Invisible to the user while recording** — only the OS-enforced indicator dot/notification shows

### Triggers (all runtime-configurable)

| Trigger | How it works |
|---|---|
| **Shake xN** | Accelerometer, count (default 3) + sensitivity configurable. Sensor-batched for near-zero idle battery |
| **Volume key** | 1 press toggles recording. Uses `MediaSession` + `VolumeProvider.setPlaybackToRemote` — works in background, screen off |
| **Custom gesture** | "Learn current gesture" records a shake pattern; matched live with Dynamic Time Warping on normalized accelerometer data |

Any gesture both starts **and** stops recording (toggle). A max recording length can be set as a safety limit.

### Recording reliability

The mic is only used during actual recordings (no always-on listening). If the mic is busy when a trigger fires, the recorder automatically falls back through **video+audio → video only → audio only**, so you always get footage. Empty audio files are cleaned up automatically.

## Build from source

No Android Studio required — the APK is built in the cloud by GitHub Actions.

1. Fork/push this repo
2. `.github/workflows/build.yml` runs on every push to `main` (or manually via **Actions → Build APK → Run workflow**)
3. Download the `autorecorder-debug-apk` artifact from the run page

Local equivalent (requires JDK 17 + Gradle 8.9 + Android SDK):

```bash
gradle assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

## Install & use

1. Copy `app-debug.apk` to your phone and install (allow *install unknown apps*)
2. Open AutoRecorder → **Grant permissions** (microphone, camera, notifications)
3. (Recommended) **Request battery optimization exemption** so gestures keep working with the screen off on aggressive OEMs
4. Toggle **Enable background service**
5. Shake the phone 3 times — recording starts; shake again (or press volume once) to stop

Recordings land in `Movies/AutoRecorder` (video) and `Music/AutoRecorder` (audio-only fallback).

### Runtime configuration

- Shake count & sensitivity
- Volume key on/off
- Custom gesture template (learn / clear)
- Front vs back camera
- Max recording length (minutes; 0 = until stop gesture)

## Project structure

```
app/src/main/java/com/autorecorder/
├── MainActivity.kt          # settings UI, permissions, template capture
├── RecorderService.kt       # foreground service wiring gestures → recording
├── config/AppConfig.kt      # persisted, live-read runtime config
├── trigger/
│   ├── GestureEngine.kt     # sensor batching + trigger dispatch
│   ├── ShakeDetector.kt     # accelerometer peak counting
│   ├── VolumeKeyReceiver.kt # MediaSession volume-key capture
│   └── CustomGestureDetector.kt # DTW template matcher
└── recorder/
    ├── CameraRecorder.kt    # CameraX video+audio → MediaStore
    ├── AudioOnlyRecorder.kt # MediaRecorder fallback
    └── ServiceLifecycleOwner.kt
```

## Permissions

`RECORD_AUDIO`, `CAMERA`, `FOREGROUND_SERVICE` (+ `_MICROPHONE`/`_CAMERA`), `POST_NOTIFICATIONS`, `WAKE_LOCK`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

## Known limitations

- **Green mic/camera dot and the service notification are OS-enforced** on Android 12+ / 14+ — not hideable
- While the volume-key trigger is active, volume buttons route through the app instead of changing system volume (by design — needed for detection)
- Aggressive OEM battery managers (Xiaomi, Samsung) may kill the service — use the battery-exemption toggle

## License

Personal use. Recording people without consent may be illegal in your jurisdiction — check local laws before use.