# AGENTS.md — WhereAreYou implementation guide

This file is the primary instruction set for Codex or any coding agent working in this repository.

## Before coding

Read these documents in order:

1. `README.md`
2. `docs/PRODUCT_SPEC.md`
3. `docs/ANDROID_ARCHITECTURE.md`
4. `docs/SMS_PROTOCOL.md`
5. `docs/SECURITY_PRIVACY.md`
6. `docs/TEST_PLAN.md`
7. `docs/ROADMAP.md`
8. `docs/DISCOVERY_NOTES.md`

Do not infer a continuous-tracking product from the repository name. The core product is an **event-driven safety agent**.

## Phase 1 implementation target

Build:

- one native Android application with **Protect mode**;
- a small static client-side SMS decoder/PWA under `tools/decoder/`;
- tests for trigger logic, trusted-contact authorization, session scheduling and SMS encoding/decoding.

Do **not** build:

- a cloud backend;
- Firebase/FCM dependency for product operation;
- user accounts;
- continuous location tracking;
- a native iOS app;
- hidden app behavior;
- microphone recording or covert audio capture;
- online telemetry/analytics;
- mandatory Google-hosted infrastructure.

## Recommended Android stack

- Kotlin
- Jetpack Compose for UI
- AndroidX lifecycle/navigation
- DataStore for settings and small configuration
- Room only if event/session history genuinely needs structured persistence
- Coroutines/Flow
- WorkManager for best-effort deferred work where appropriate
- `BroadcastReceiver` for supported system/telephony/SMS events
- Android platform telecom APIs for normal outgoing calls
- a `LocationProvider` abstraction; prefer platform APIs or keep Google Play Services optional rather than hard-wiring product logic to one provider

Use the latest stable Android SDK/toolchain available when implementation begins. Prefer `minSdk 26` unless a documented platform limitation forces a higher minimum.

## Architecture rules

Keep domain logic independent from Android framework code where possible.

Suggested packages/modules:

```text
app/
  ui/
  onboarding/
  permissions/
  contacts/
  triggers/
  safety/
  location/
  sms/
  telecom/
  battery/
  connectivity/
  persistence/
  platform/
core/
  model/
  rules/
  protocol/
  security/
tools/
  decoder/
```

Exact module boundaries may change, but preserve separation of:

1. event collection;
2. authorization;
3. trigger evaluation;
4. safety-session orchestration;
5. sampling;
6. transport;
7. protocol encoding;
8. UI.

## Core invariants

1. No permanent location loop.
2. No always-running background service.
3. No location response without an authorized trigger or local user action.
4. Every remote trigger must be associated with a configured trusted contact.
5. Every location sample must carry freshness and accuracy.
6. The UI must never label an old location as current.
7. SMS is the only required Phase 1 transport.
8. Internet state may be measured, but no Phase 1 data may be sent over the internet.
9. A safety session must have a bounded lifetime and a maximum number of samples/messages.
10. The protected user can see and cancel an active safety session.
11. Emergency Callback must be a normal visible system call; never open or record the microphone independently.
12. Do not request broad permissions that are not required for an enabled feature.

## Trusted contacts

A protected device supports multiple trusted contacts.

Each contact should have independent capability flags, for example:

```text
missedCallTrigger
smsCommand
receiveLowBatteryAlert
manualStatusRecipient
emergencyCallback
```

Emergency Callback must default to disabled per contact.

Avoid `READ_CONTACTS` if possible. Prefer manual entry or a user-initiated system contact picker. Normalize phone numbers before comparison and account for local/international formatting.

## Trigger defaults for development

Defaults are configuration, not hard-coded product policy:

- same trusted contact: 3 missed calls within 60 minutes;
- calls need not be consecutive;
- a successfully answered call from/to that contact should reset or suppress the relevant escalation window;
- SMS safety request from a trusted contact + valid shared code triggers immediately;
- system `ACTION_BATTERY_LOW` can trigger a low-battery alert if configured;
- manual local status send is always allowed.

Create tests around time windows and reset behavior before wiring receivers.

## Safety session

A safety session is short-lived and event-scoped.

Suggested flow:

```text
AUTHORIZED_TRIGGER
  -> create session
  -> send/prepare immediate status using best available location
  -> attempt fresh location
  -> schedule limited follow-up samples
  -> stop after success/timeout/max samples/cancellation
```

Suggested initial sample targets are around T+0, T+2 minutes and T+5 minutes. These times are **best effort**, not guaranteed by Android.

Do not build fragile assumptions that Android will wake the app at an exact minute. Implement a scheduler abstraction. Enhanced reliability can use special platform capabilities only when the user has explicitly granted them.

## Location behavior

Prefer this order:

1. read the newest acceptable last-known location immediately;
2. mark its age and accuracy honestly;
3. request a fresh fix when platform state permits;
4. transmit improved samples as they arrive;
5. never average coordinates blindly.

When multiple samples exist, rank by a combination of freshness and reported accuracy and detect obvious outliers/movement. The decoder should show the individual samples as well as the current best estimate.

## SMS protocol

Implement the protocol described in `docs/SMS_PROTOCOL.md` in a pure/testable core package.

Requirements:

- default response fits in one Unicode SMS segment whenever possible;
- no URL in transmitted payload;
- no English prose in the default payload;
- deterministic encoder/decoder;
- protocol version field;
- session ID and sequence field;
- malformed payloads fail safely;
- authenticated command messages are accepted only from a trusted number;
- rate limiting and replay protection are required.

## Emergency Callback

Treat this feature as a safety callback, not a listening feature.

Implementation requirements:

- protected user explicitly enables it per trusted contact;
- remote request is authenticated;
- default setting is `Ask first` or a visible cancellable countdown;
- if automatic countdown mode is added, it remains opt-in and rate-limited;
- use `TelecomManager.placeCall(...)` / normal system telecom behavior;
- do not hide call UI;
- do not suppress call indicators;
- do not start `RECORD_AUDIO`/microphone capture;
- do not record calls;
- do not attempt to auto-answer an incoming call;
- do not place emergency-service numbers.

If Android/OEM behavior makes the feature unreliable from background state, document the limitation instead of implementing a stealth workaround.

## Permission strategy

Build a permission/readiness screen. Ask permissions contextually.

Likely permissions/features include:

- fine/coarse location;
- background location for remote-triggered location workflows;
- receive SMS;
- send SMS;
- call log / phone state only for missed-call rules;
- phone call permission only if Emergency Callback is enabled;
- network state;
- boot completed only if needed to restore scheduled state/configuration.

Do not add `READ_SMS` just to inspect one incoming command if `RECEIVE_SMS` provides the necessary event content. Do not add `READ_CONTACTS` solely for trusted-contact setup.

Google Play restrictions are a distribution concern, not a reason to disguise behavior. Never misrepresent the app category to obtain restricted permissions.

## Decoder/PWA

Build a minimal static decoder with no backend.

It should:

- work offline after load/install;
- accept pasted compact payloads;
- optionally accept payload from URL fragment/query for local tooling/Shortcuts integration;
- store nothing remotely;
- decode multiple messages from one session;
- choose/highlight best location sample;
- generate a map URL locally;
- clearly show timestamp, age, accuracy, battery, charging, connectivity and sequence.

Do not add analytics or remote logging.

## Testing priority

Before real SMS/call/location integration, unit-test:

- phone-number normalization;
- trusted-contact authorization;
- missed-call counting windows;
- resets after answered calls;
- duplicate/replayed SMS commands;
- protocol size;
- encoder/decoder round trips;
- sample ranking;
- safety-session termination;
- rate limits.

Then test on physical devices from at least two OEMs and multiple Android versions. Background execution behavior cannot be validated only in an emulator.

## First coding milestone

The first PR/commit series should aim for:

1. Android project scaffolding and buildable app;
2. minimal Protect UI with trusted-contact CRUD;
3. domain models + trigger engine with tests;
4. protocol encoder/decoder with tests;
5. static decoder/PWA;
6. permission readiness UI;
7. only then wire real SMS/call/location platform receivers.

Keep commits small and document any Android restriction that forces deviation from the spec.
