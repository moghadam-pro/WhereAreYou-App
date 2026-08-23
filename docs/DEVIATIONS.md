# Deviations from the spec — Phase 1A/1B implementation

AGENTS.md asks that any Android platform restriction forcing a deviation from the spec be
documented alongside the implementation. This tracks all of them so far, plus a couple of
sandbox-specific limitations that shaped how the code was written and verified.

## 1. This build environment cannot compile `:app`

The Android Gradle Plugin and the Android SDK components it needs are published only to
Google's Maven repository (`dl.google.com`). The sandbox this code was written in blocks
outbound access to that host at the network-policy level (`403` on `CONNECT`, confirmed via
the local egress proxy's status endpoint) — this is an infrastructure/environment
restriction, not an Android platform restriction, but it materially affected how this phase
was implemented and verified, so it belongs here.

Consequences:

- `:core` (plain Kotlin/JVM, Maven Central only — no Google-hosted dependency at all) builds
  and its full test suite runs in this sandbox. All 120 domain-layer tests referenced in
  other commits were actually executed here, not just written.
- `:app` (the Android module) could **not** be compiled, run, or tested in this sandbox.
  Every file in it was written and hand-reviewed against current AGP / Compose BOM
  2024.09.03 / Room 2.6.1 / Navigation-Compose 2.8.1 APIs, but review is not a build. A real
  build (see below) was needed to find the first bug this created.
- The Gradle setup works around this only for local development ergonomics: root
  `gradle.properties` sets `org.gradle.configureondemand=true` so that `:core:test` never
  needs to configure `:app` (and therefore never needs to resolve AGP) — this is not a
  permanent design decision, just what let this phase's `:core` work be built and tested
  without the Android SDK.
- A `.github/workflows/build-debug-apk.yml` CI workflow now does what this sandbox cannot:
  on a GitHub-hosted runner (full internet, preinstalled Android SDK) it runs
  `:core:test` then `:app:assembleDebug` and uploads the resulting debug APK. Its first
  three runs all failed identically with
  `NoClassDefFoundError: com/android/build/gradle/api/BaseVariant` while applying
  `kotlin("android")` on top of `com.android.application` — and stayed identical across two
  different fix attempts that turned out to be wrong: neither downgrading AGP (8.5.2 ->
  8.4.2) nor downgrading the Gradle wrapper (8.14.3 -> 8.6, the floor AGP 8.4.x itself
  requires) changed the failure at all. That ruled out both as the cause and pointed at the
  one thing common to every attempt: Kotlin Gradle Plugin **2.0.21** itself. Its
  post-K2-rewrite Android target still reflects on the old `BaseVariant`-based API at
  configuration time and appears to hit a real regression doing so, independent of which
  AGP/Gradle version it's paired with. Fixed by pinning `:app` to Kotlin **1.9.24** (with a
  matching KSP `1.9.24-1.0.20` and Compose compiler extension `1.5.14`) — the pre-rewrite
  Kotlin Android plugin uses the same `BaseVariant`-based API directly and doesn't hit this.
  `:core` stays on Kotlin 2.0.21 (pure `kotlin("jvm")`, no AGP interaction, unaffected); the
  two modules deliberately run different Kotlin Gradle Plugin versions as a result.
  This is exactly why this document says "review is not a build" above — treat `:app` as
  verified only as of the last **green** run of that workflow, not as of the last time
  someone read the code or guessed a version number, and don't trust the first plausible-
  looking cause: two of the three hypotheses tried here were wrong despite each looking
  like a reasonable, well-justified fix at the time.

This is unrelated to the actual Google Play SMS/Call-Log policy restriction discussed in
`SECURITY_PRIVACY.md` section 17 and `PRODUCT_SPEC.md` section 18 — that one is a real
product/distribution constraint independent of any particular build machine.

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
