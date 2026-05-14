# Kavach Android Build Environment Manifest

This document tracks the deterministic toolchain versions used for the Kavach project.

## Toolchain Components

| Component | Version | Path | Source |
|-----------|---------|------|--------|
| **JDK (OpenJDK)** | 17.0.2 | `AppData\Local\Java\jdk-17.0.2` | [java.net](https://download.java.net/java/GA/jdk17.0.2/dfd4a8d0985749f896bed50d7138ee7f/8/GPL/openjdk-17.0.2_windows-x64_bin.zip) |
| **Android CMD Tools** | 12.0 (11076708) | `AppData\Local\Android\Sdk\cmdline-tools\latest` | [google.com](https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip) |
| **Platform Tools** | 35.0.1 (latest) | `AppData\Local\Android\Sdk\platform-tools` | via sdkmanager |
| **Build Tools** | 34.0.0 | `AppData\Local\Android\Sdk\build-tools\34.0.0` | via sdkmanager |
| **Platforms** | android-34 | `AppData\Local\Android\Sdk\platforms\android-34` | via sdkmanager |
| **Gradle** | 8.7 (wrapper) | Project Root | via gradlew |
| **Kotlin** | 2.0.21 | libs.versions.toml | project config |

## Build Memory Constraints
- **Daemon Heap**: 4GB (`org.gradle.jvmargs=-Xmx4g`)
- **Kotlin Daemon**: 2GB (`-Dkotlin.daemon.jvm.options="-Xmx2g"`)

## Environment Maintenance
- To verify environment integrity: Run `.\env_check.ps1`
- To recover from Gradle cache corruption:
  ```powershell
  .\gradlew --stop
  Remove-Item -Path "$HOME\.gradle\caches" -Recurse -Force
  ```
