# Syncthing-Fork Android - Claude Code Context

## Project Overview

**Syncthing-Fork** is an Android wrapper application for [Syncthing](https://github.com/syncthing/syncthing), a decentralized peer-to-peer file synchronization protocol. This fork continues development of the official Syncthing-Android app which was deprecated.

- **Package**: `com.github.catfriend1.syncthingfork`
- **License**: MPLv2
- **Min SDK**: 23 (Android 6.0)
- **Target SDK**: 36 (Android 14)
- **Language**: Java + Kotlin (Java 21)
- **Build System**: Gradle with Kotlin DSL

## Quick Reference

### Build Commands

```bash
# IMPORTANT: Set this environment variable to skip native builds
export IS_COPILOT=true

# Build debug APK (use debug flavor for development)
IS_COPILOT=true ./gradlew assembleDebug

# Run lint
IS_COPILOT=true ./gradlew lintDebug

# Clean build artifacts
./gradlew clean
```

**Note**: Release builds require signing keys not included in the repository. Only use debug flavor for development.

### Key Files

| File | Purpose |
|------|---------|
| `app/src/main/java/com/nutomic/syncthingandroid/service/SyncthingService.java` | Core foreground service managing Syncthing native binary |
| `app/src/main/java/com/nutomic/syncthingandroid/service/RestApi.java` | REST API communication with Syncthing binary |
| `app/src/main/java/com/nutomic/syncthingandroid/util/ConfigXml.java` | Configuration XML parsing and management |
| `app/src/main/AndroidManifest.xml` | App components, permissions, and configuration |
| `gradle/libs.versions.toml` | Dependency version catalog |

## Architecture

### Core Components

**SyncthingService** (`SyncthingService.java`)
- Foreground service that manages the Syncthing native binary lifecycle
- States: `INIT` → `STARTING` → `ACTIVE` → `DISABLED` / `ERROR`
- Responsibilities:
  - Starting/stopping native binary via `SyncthingRunnable`
  - Managing `RestApi` for REST communication
  - Handling `EventProcessor` for sync events
  - Monitoring `RunConditionMonitor` for sync conditions
  - Config backup/restore with ZIP encryption

**RestApi** (`RestApi.java`)
- Handles HTTP communication with Syncthing's REST API
- Manages configuration, device connections, folder status
- Implements custom sync conditions per device/folder
- Broadcasts sync completion events to external apps

**Config Management**
- `ConfigXml.java`: Parses and modifies Syncthing's XML configuration
- `ConfigRouter.java`: High-level config operations (ignore device/folder, pause/resume)

**UI Architecture**
- Activities (18): Main UI screens for devices, folders, settings, etc.
- Settings: Modern Jetpack Compose Material3 UI
- Navigation: Jetpack Navigation Compose

### Dependency Injection

Uses **Dagger 2** for dependency injection:
- `SyncthingApp.java`: Application class that sets up Dagger component
- `SyncthingModule.java`: Provides dependencies
- `DaggerComponent.java`: Generated component interface

## Technology Stack

| Category | Technology | Version |
|----------|-----------|---------|
| **UI Framework** | Jetpack Compose + Material3 | - |
| **Navigation** | Navigation Compose | 1.0.1 |
| **DI** | Dagger | 2.59.2 |
| **Permissions** | Accompanist Permissions | 0.37.3 |
| **Networking** | Volley | 1.2.1 |
| **JSON** | Gson | 2.13.2 |
| **QR Code** | ZXing Core | 3.3.0 (for Android 6 compat) |
| **Compression** | Zip4j | 2.11.6 |
| **Kotlin** | Kotlin | 2.3.20 |
| **Java** | OpenJDK | 21 |
| **Gradle** | Android Gradle Plugin | 9.1.0 |

## Important Development Notes

### DO NOT:
- ❌ Upgrade Kotlin version in `gradle/libs.versions.toml` (causes warnings/errors)
- ❌ Edit `wiki/CHANGELOG.md` (maintainer handles this)
- ❌ Build release flavor (signing keys not available)
- ❌ Run full `assembleDebug` unless necessary (use partial builds)

### DO:
- ✅ Always set `IS_COPILOT=true` for Gradle tasks
- ✅ Use debug flavor for development
- ✅ Write code and comments in English only
- ✅ Check manufacturer-specific workarounds in `wiki/manufacturer-specific/`
- ✅ Test on Android 6.0+ devices (min SDK 23)

### Native Binary
- Syncthing native binary is built separately via `./gradlew buildNative`
- Native code is in separate `syncthing/` submodule
- JNI interface defined in `app/src/main/cpp/libSyncthingNative.mk`
- When `IS_COPILOT=true`, native build is skipped

## Key Features

1. **P2P File Sync**: Decentralized file synchronization using Syncthing protocol
2. **Run Conditions**: Custom sync conditions per device/folder (WiFi, charging, location, etc.)
3. **Web GUI**: Embedded Syncthing web interface accessible from app
4. **Device Management**: Add/remove devices, QR code pairing
5. **Folder Types**: Send & Receive, Send Only, Receive Only
6. **Versioning**: File version recovery and conflict resolution
7. **Camera Mode**: Standalone "Syncthing Camera" launcher for photo sync
8. **Config Backup**: Encrypted (AES-256) ZIP export/import
9. **External Integration**: Broadcast intents for folder sync completion
10. **Quick Settings**: Tiles for force run, schedule, camera

## Known Issues & Workarounds

Check `wiki/known-bug-workarounds/` for:
- Android 6 certificate issues
- SD card write access on Android 11+
- Import/export config relative path issues
- Low resource device handling

Manufacturer-specific fixes in `wiki/manufacturer-specific/`:
- Huawei device connection issues
- Xiaomi welcome screen loops
- Nokia phone preparations
- Android TV preparations

## Testing

### Web GUI Access (Emulator)
```bash
# Forward port to access Web GUI from development machine
adb forward tcp:18384 tcp:8384

# Access at https://127.0.0.1:18384
```

### Device Testing
- Test on Android 6.0 (API 23) for minimum compatibility
- Test on Android 14 (API 36) for latest features
- Verify storage permissions handling
- Test battery optimization whitelist behavior

## File Structure

```
app/src/main/java/com/nutomic/syncthingandroid/
├── activities/          # UI activities (18)
├── fragments/           # UI fragments
├── service/             # Background services
│   ├── SyncthingService.java    # Core service
│   ├── RestApi.java              # REST API client
│   ├── EventProcessor.java       # Event handling
│   └── RunConditionMonitor.java  # Sync conditions
├── settings/            # Settings UI (Compose)
├── model/               # Data models
├── http/                # HTTP client layer
├── receiver/            # Broadcast receivers
├── util/                # Utilities
├── views/               # Custom views
└── theme/               # App theming

app/src/main/
├── cpp/                 # JNI native interface
└── res/                 # Resources (layouts, strings, etc.)
```

## Security Considerations

- HTTPS certificates managed internally
- API key authentication for REST API
- Encrypted config backups (AES-256)
- Network security config in `res/xml/network_security_config.xml`
- Storage permissions properly requested and handled
- Foreground service with persistent notification (Android requirement)

## External Integration

Apps can receive folder sync completion broadcasts:

**Manifest declaration:**
```xml
<uses-permission android:name="${applicationId}.permission.RECEIVE_SYNC_STATUS"/>
```

**Intent action:**
```java
String ACTION = ".ACTION_NOTIFY_FOLDER_SYNC_COMPLETE";
```

See `wiki/developers/Integrate-your-app-with-Syncthing-receive-folder-sync-status.md`

## Build Environment

**Recommended:** Debian Linux or WSL
**Required:**
- GCC
- Git
- Go 1.25.8+
- OpenJDK 21
- Python 3
- Android SDK (auto-installed by script)

**Windows:** Use `build-windows.cmd` with appropriate antivirus exceptions

## Resources

- **Wiki**: `wiki/README.md` - Comprehensive documentation
- **Building**: `wiki/developers/Building-and-Development.md`
- **Migration**: `wiki/migration/Switching-from-the-deprecated-official-version.md`
- **Tips**: `wiki/tips-and-tricks/` - Remote control, logging, etc.
- **Privacy**: `privacy-policy.md`

## Contributing

When making changes:
1. Test on multiple Android versions (6.0, 10, 14)
2. Verify storage permission handling
3. Check battery optimization behavior
4. Test manufacturer-specific workarounds
5. Ensure backward compatibility
6. Run lint: `IS_COPILOT=true ./gradlew lintDebug`

## License

This project is licensed under MPLv2. See `LICENSE` for details.

Forked from: [syncthing/syncthing-android](https://github.com/syncthing/syncthing-android)

Special thanks to former maintainers: Catfriend1, imsodin, nutomic
