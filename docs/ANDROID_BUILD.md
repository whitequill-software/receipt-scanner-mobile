# Android build verification

## Toolchain and wrapper provenance

Use JDK 17, Android SDK Platform `platforms;android-36.1`, Build Tools
`build-tools;36.0.0`, and `platform-tools`. The existing AGP 9.1.1, Kotlin
Compose 2.2.10, KSP 2.3.5, and Java 11 source/target settings are unchanged.
See the [AGP compatibility table](https://developer.android.com/build/releases/agp-9-1-0-release-notes).

The wrapper scripts and JAR were generated using an installed official Gradle
9.3.1 distribution in a separate empty project, so Android project configuration
was not needed to repair the wrapper:

```text
gradle -p <empty-project> wrapper --gradle-version 9.3.1 --distribution-type bin --gradle-distribution-sha256-sum b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06 --no-daemon
```

The generated files were copied into the repository, retaining executable mode
for `gradlew`. The distribution SHA-256 is pinned in the wrapper properties.
The generated JAR SHA-256 is
`b3a875ddc1f044746e1b1a55f645584505f4a10438c1afea9f15e92a7c42ec13`, verified
against [Gradle's published wrapper checksum](https://services.gradle.org/distributions/gradle-9.3.1-wrapper.jar.sha256).
See the [official wrapper instructions](https://docs.gradle.org/9.3.1/userguide/gradle_wrapper.html).

## GitHub Actions

`.github/workflows/android.yml` runs on pull requests, pushes to `main`, and
manual dispatch. It installs JDK 17 and the required SDK packages, validates the
wrapper through `gradle/actions/setup-gradle`, and runs:

```sh
./gradlew --no-daemon --stacktrace --continue :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

`--continue` attempts independent tasks after a failure and still fails the job
if any requested task fails. No tests, lint rules, or app features are disabled
or changed. Connected device tests remain a separate, device-dependent check.
Actions are pinned to commit SHAs and the workflow token has read-only contents
permission; checkout does not persist credentials.

After all checks succeed, download `receipt-scanner-debug-<commit>` from the run's
Artifacts section. It contains `app-debug.apk`; missing output fails the upload.
Test results and lint reports are retained as `android-reports-<commit>` even if
checks fail, when those reports exist. Artifact retention is seven days. Early
configuration failures may produce no reports; inspect the build step's log.

## Debug signing

The existing `debugConfig` still reads root `debug.keystore`. CI creates a fresh
development-only key at that ignored path with restrictive file permissions,
the standard Android debug alias/password, and removes it in an always-run
cleanup step. Only APK/report paths are uploaded. No signing key is committed,
cached, or uploaded, and no release or AI provider secrets are required.

Each CI run has a different signing identity. Its APK is for testing and may
require uninstalling a previously signed app before installation. For local
development, restore your existing debug key to preserve update compatibility,
or generate a key as documented in the SOP. Release signing remains unchanged;
keep release keys outside the repository. `*.keystore`, `*.jks`, and `*.p12`
are ignored as an additional safeguard.

## Validation on 28 October 2026

- Wrapper generation: passed using Gradle 9.3.1; JAR checksum matched the official value.
- `gradlew.bat --version`: passed with Temurin JDK 17.0.20.1 and Gradle 9.3.1.
- Workflow syntax: passed `actionlint`.
- Android unit tests, lint, and debug APK: validation in progress; see the PR checks for CI results.

The Windows execution environment initially failed before project configuration
with `java.io.IOException: Unable to establish loopback connection`, caused by
`java.net.SocketException: Invalid argument: connect` in Java Unix-domain socket
initialization. Local validation used
`JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=Z:\nonexistent` to select Java's
TCP fallback. This is an execution-environment workaround, is not a project
setting, and is not used by the Linux CI workflow.
