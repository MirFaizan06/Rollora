# Dependency pins and official sources
Checked on 2026-09-16, and re-verified while completing the build on 2026-09-17. These are explicit, deliberately compatible release pins, not a claim that every version is the newest available. No dynamic `+` versions. Compose modules use Google's BOM. Transitive versions are resolved by Gradle from these pins.

| Component | Pin | Official source |
|---|---|---|
| Android Gradle Plugin | 8.13.2 | https://developer.android.com/build/releases/agp-8-13-0-release-notes |
| Gradle | 8.13 | https://gradle.org/release-checksums/ |
| Build JDK | 17 recommended; 21 accepted | https://docs.gradle.org/current/userguide/compatibility.html |
| Kotlin Android + Compose compiler plugin | 2.2.21 | https://github.com/JetBrains/kotlin/releases/tag/v2.2.21 |
| KSP | 2.2.21-2.0.4 | https://github.com/google/ksp/releases/tag/2.2.21-2.0.4 |
| Compile/target SDK | 36 | https://developer.android.com/build/releases/agp-8-13-0-release-notes |
| SDK Build Tools | 36.0.0 | https://developer.android.com/build/releases/agp-8-13-0-release-notes |
| Compose BOM | 2025.10.01 | https://developer.android.com/develop/ui/compose/bom/bom-mapping |
| Activity Compose | 1.11.0 | https://developer.android.com/jetpack/androidx/releases/activity |
| Core KTX | 1.17.0 | https://developer.android.com/jetpack/androidx/releases/core |
| Fragment KTX | 1.8.9 | https://developer.android.com/jetpack/androidx/releases/fragment |
| Lifecycle ViewModel + Runtime KTX | 2.9.4 | https://developer.android.com/jetpack/androidx/releases/lifecycle |
| Biometric | 1.1.0 | https://developer.android.com/jetpack/androidx/releases/biometric |
| Room runtime, KTX, compiler | 2.8.4 | https://developer.android.com/jetpack/androidx/releases/room#2.8.4 |
| WorkManager KTX | 2.10.5 | https://developer.android.com/jetpack/androidx/releases/work#2.10.5 |
| Kotlin coroutines Android | 1.10.2 | https://github.com/Kotlin/kotlinx.coroutines/releases/tag/1.10.2 |
| JUnit (tests only) | 4.13.2 | https://junit.org/junit4/ |
| Robolectric (tests only) | 4.16 | https://github.com/robolectric/robolectric/releases |
| androidx.test:core (tests only) | 1.7.0 | https://developer.android.com/jetpack/androidx/releases/test |

Room 2.8.5 was visible in the current release notes; this project deliberately pins the documented 2.8.4 version. Kotlin and its Compose compiler plugin share the same version. KSP is paired with Kotlin 2.2.21. Compose dependency versions come from the BOM rather than hand-mixing UI/Foundation/Material3 releases.

**Build Tools changed from 35.0.0 to 36.0.0** during completion: the Unity 6000.5.8f1 Android SDK actually bundles `platforms;android-36` with `build-tools;36.0.0` (no 35.0.0 side by side), and 36.0.0 is the AGP 8.13-compatible tool version for a compileSdk 36 project, so the pin was corrected to match what is actually installed and used, per AGP 8.13 release notes.

Robolectric 4.16 is pinned to run Room/SQLite integration tests on the JVM in `app/src/test`; it needs JDK 21 only if a test's `@Config(sdk=...)` targets API 36 shadows. This project's integration tests pin `@Config(sdk = [34])` deliberately so they run correctly under the same JDK 17 used for the main build (Robolectric 4.16 supports API 23–36; 34 was chosen to stay JDK-17-compatible, not because 34 is otherwise significant to this app). Robolectric and androidx.test are `testImplementation`-only: they are never packaged into the app.

No Excel, ZIP, HTTP, JSON or cryptography dependency is needed: the app uses a small OOXML writer and Java/Android platform APIs. It includes no native libraries, so the NDK and ABI-specific packages are unnecessary. Python is optional and only regenerates the vector logo.

## Toolchain actually used to complete this build (Windows)
- JDK: Unity 6000.5.8f1's bundled OpenJDK 17.0.18 (Temurin), at `D:\UnityEditor\6000.5.8f1\Editor\Data\PlaybackEngines\AndroidPlayer\OpenJDK`. This satisfies "a separate JDK 17" without installing anything new — Unity already ships one.
- Android SDK: Unity 6000.5.8f1's bundled SDK, at `D:\UnityEditor\6000.5.8f1\Editor\Data\PlaybackEngines\AndroidPlayer\SDK`, containing `platforms;android-36` and `build-tools;36.0.0` already installed. Nothing was added to or removed from this SDK.
- System Java 26 and Unity's own tools were left untouched; `JAVA_HOME`/`ANDROID_HOME` were only set for the duration of the build commands below.

Gradle distribution SHA-256: `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78`.
Wrapper JAR SHA-256: `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`.

Both were checked against Gradle's official checksum page. The wrapper JAR is included; SDKs, Gradle's full distribution and cached dependencies are downloaded on first build.
