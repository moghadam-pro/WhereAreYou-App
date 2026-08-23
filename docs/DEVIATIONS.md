# Deviations from the spec — Phase 1A/1B implementation

AGENTS.md asks that any Android platform restriction forcing a deviation from the spec be
documented alongside the implementation. This tracks all of them so far, plus a couple of
sandbox-specific limitations that shaped how the code was written and verified.

## 1. `:app` build verification history (resolved)

**Status: resolved.** `:app` now compiles and packages an installable debug APK
(`app/build/outputs/apk/debug/app-debug.apk`, `com.whereareyou.app.debug`, minSdk 26,
targetSdk/compileSdk 35) on a normal developer machine with Android Studio's SDK.
`:core:test` (120 tests) passes in the same build. This section is kept as history,
because it explains why several build files look the way they do.

### What the original sandbox could not do

The Android Gradle Plugin and the Android SDK components it needs are published only to
Google's Maven repository (`dl.google.com`). The sandbox this code was first written in
blocked outbound access to that host at the network-policy level (`403` on `CONNECT`) — an
infrastructure restriction, not an Android platform restriction, but it shaped how this
phase was written and verified, so it belongs here.

Consequently `:core` (plain Kotlin/JVM, Maven Central only) was built and tested there,
while every file in `:app` was only hand-reviewed against the AGP / Compose / Room APIs of
the day. Review is not a build: the first real compile found an invalid
`import androidx.compose.foundation.layout.weight` in `HomeScreen.kt` (`weight` is a
`RowScope`/`ColumnScope` member, not a top-level function), and showed that several call
sites — `enableEdgeToEdge()`, `Icons.AutoMirrored.Filled.ArrowBack`, `MenuAnchorType` —
required newer AndroidX artifacts than the dependency block actually declared.

### The `BaseVariant` CI failure, and what it really was

A `.github/workflows/build-debug-apk.yml` workflow was added to do the build the sandbox
could not. Its first ~23 runs all failed with
`NoClassDefFoundError: com/android/build/gradle/api/BaseVariant` while applying
`kotlin("android")` on top of `com.android.application`, unchanged across many attempted
AGP/Gradle/Kotlin version combinations and several different ways of applying the plugin.

The cause was classloader scoping, not versions: Kotlin's `KotlinAndroidTarget` is
instantiated from the shared root-project plugin scope, while AGP had only ever been
declared inside `:app`'s own local `buildscript` scope. Gradle's scopes are
child-sees-parent and never the reverse, so the shared scope structurally could not see
AGP. Declaring every plugin at the root with `apply false` — the ordinary multi-module
pattern — puts them in one scope and fixes it. None of the version changes that were tried
could ever have mattered.

### Current toolchain

| Component | Version | Why |
| --- | --- | --- |
| Gradle | 8.13 | AGP 8.9 floor is 8.11.1 |
| AGP | 8.9.1 | supports `compileSdk 35`, needs JDK 17+ |
| Kotlin | 2.0.21 (both modules) | with `org.jetbrains.kotlin.plugin.compose`; K2 Compose compiler is no longer a separate `kotlinCompilerExtensionVersion` |
| KSP | 2.0.21-1.0.28 | must track the Kotlin version exactly |
| compileSdk / targetSdk / minSdk | 35 / 35 / 26 | 35 is what AGP 8.9 fully supports |
| JVM target | 17 | both modules |

Two build-config choices are deliberate and worth not "tidying away":

- Neither module uses `jvmToolchain(...)`. A toolchain makes Gradle demand a JDK of that
  exact version and fail if the machine only has a newer LTS installed; any JDK ≥ 17 can
  emit 17 bytecode, so the target is set on the compiler instead. An earlier
  `jvmToolchain(21)` in `:core` also broke `:app`, which cannot inline JVM-21 bytecode
  into a JVM-17 target.
- `org.gradle.configureondemand` was removed from `gradle.properties`. It existed only so
  that `:core:test` would not configure `:app` (and so would not need AGP) in the
  offline sandbox; AGP warns that it does not support the flag, and it is unnecessary now.

### Reproducing the build

The build needs a JDK 17/18/21 — Android Studio's own bundled JBR is currently JDK 25,
which Gradle 8.13 will not run on, so set the Gradle JDK explicitly (Settings → Build,
Execution, Deployment → Build Tools → Gradle → Gradle JDK) rather than relying on the
default. On a network where `dl.google.com` is filtered, Gradle also needs proxy details
as JVM system properties in `~/.gradle/gradle.properties`
(`systemProp.https.proxyHost` / `systemProp.https.proxyPort`); the JVM ignores the
`HTTP_PROXY`/`HTTPS_PROXY` environment variables that the rest of the shell honours.

This is all unrelated to the Google Play SMS/Call-Log policy restriction discussed in
`SECURITY_PRIVACY.md` section 17 and `PRODUCT_SPEC.md` section 18 — that one is a real
product/distribution constraint independent of any build machine.

## 2. `PhoneNumberNormalizer` is hand-rolled, not a real phone-number library

`ANDROID_ARCHITECTURE.md` section 13 recommends "a tested phone-number library ... or
Android normalization utilities." `core/.../model/phone/PhoneNumberNormalizer.kt` is
instead a small dependency-free implementation (E.164 pass-through + Iranian national-prefix
handling + Persian/Arabic-Indic digit normalization), so that `:core` has zero third-party
dependencies and stays a plain-JVM build target with no dependency resolution surface at
all. It passes the `TEST_PLAN.md` "Phone normalization" matrix for the cases it targets, but
it is not a substitute for a real library's handling of every region's numbering plan.

This is flagged in `DISCOVERY_NOTES.md` open question #6 as something to revisit before
public release — likely swapping in `libphonenumber` (or Android's own
`PhoneNumberUtils`/`Phonenumber` APIs once the code lives in `:app`) behind the same
`NormalizedPhoneNumber` sealed-result contract, so call sites in `core.rules` and
`app.contacts` would not need to change.

## 3. `TriggerEngine` in `AppContainer` does not live-reload rule settings yet

`RuleSettingsStore` (DataStore-backed `MissedCallRuleConfig`) exists and is wired into
`AppContainer`, but the `TriggerEngine` singleton is built once from the *default* config,
not re-built when a user changes the threshold/window/cooldown. The reason: `MissedCallEvaluator`
and `CallLogDeduplicator` are stateful (in-memory per-contact windows and a dedup set), so
naively reconstructing `TriggerEngine` on every settings change would silently discard that
in-flight history mid-session.

This is a real gap to close, but it is deferred rather than solved with a quick hack because
nothing in this build calls `triggerEngine.evaluate(...)` yet — there are no
BroadcastReceivers wired (see below), so there is no live state to lose today. The right fix
(reload config into the *existing* evaluator instances, or replay recent history into a
freshly configured one) should land alongside the Phase 1C/1D receivers that actually feed
events into the engine.

## 4. No platform event adapters (BroadcastReceivers) in this phase

Per `AGENTS.md`'s "First coding milestone" ordering and the explicit instruction for this
work ("Before implementing real SMS, call-log, telecom, or background-location integration,
make sure the domain layer and tests are complete"), this phase (1A/1B) stops short of any
real SMS/call/battery/location platform integration:

- The manifest declares **zero** dangerous permissions.
- `AppContainer.triggerEngine` is fully wired against real persistence but has no event
  source.
- `permissions/PermissionReadiness.kt` reports every feature as `NOT_YET_REQUIRED` — it is a
  structural placeholder, not a real permission-state reader, because there is nothing yet
  that would request any of these permissions.

This is the intended scope boundary for 1A/1B, not an accident — recorded here so it reads
as "not yet built" rather than "forgotten."

## 5. `SmsSegmentEstimator` is a simplified, pure-JVM approximation

`core/.../protocol/SmsSegmentEstimator.kt` estimates GSM-7 vs. UCS-2 segment counts using a
coarse rule (any non-ASCII-printable character forces UCS-2 for the whole message) so that
protocol tests can assert the one-Unicode-segment budget without the Android SDK. It is not
a replacement for the real on-device check: `AGENTS.md`'s SMS protocol requirements still
call for using the platform's own length calculation (the current equivalent of
`SmsMessage.calculateLength`) before an actual send, once the SMS transport is implemented
(Phase 1C).

## 6. Decoder PWA: no PNG app icon yet

`tools/decoder/icons/` only has an SVG icon. `apple-touch-icon` is intentionally omitted
from `index.html` rather than pointed at a nonexistent file; iOS's "Add to Home Screen" will
fall back to a page screenshot until a real icon set is added. Noted in
`tools/decoder/README.md` "Known gaps" as well.

## 7. No `SessionScheduler`/`WorkManager` implementation yet

`ANDROID_ARCHITECTURE.md` section 7 describes a `SessionScheduler` abstraction for the
best-effort ~T+2m/~T+5m follow-up samples. `core.rules.SafetySessionMachine` implements the
*pure state transitions* a scheduler would drive (`SessionEvent.FollowUpDue`, etc.), fully
tested, but no concrete `WorkManager`-backed scheduler exists in `:app` yet — there is
nothing to schedule against until a safety session can actually be triggered, which is
Phase 1C/1D/1E territory per `ROADMAP.md`.
