# WhereAreYou

An **event-driven family safety agent for Android**.

WhereAreYou is not intended to be a continuous family tracker. The protected phone remains mostly idle and reacts to explicit safety-related events such as repeated missed calls from trusted contacts, an authenticated SMS command, or a low-battery event. When a safety session is triggered, the app collects the smallest useful set of device status data and replies primarily over SMS.

The project started from a simple real-world problem: a family member was not answering repeated calls, and there was no lightweight way to know the phone's last/current location, battery level, or connectivity state without relying on a continuously running tracker or an internet service.

## Product principles

1. **Event-driven, not continuously tracking.** No permanent GPS tracking loop and no always-running background service.
2. **SMS-first.** Phase 1 must work without mobile data, Wi-Fi, accounts, push notifications, or a central server.
3. **Transparent and consensual.** The app is visible on the protected phone. Trusted contacts are explicitly configured by the phone owner.
4. **Minimal permissions.** Request only permissions required by enabled features.
5. **Multiple trusted contacts.** A protected person can add several family members or caregivers.
6. **Compact one-segment responses.** SMS payloads are deliberately short and contain no map URL. A decoder reconstructs the readable status and map link locally.
7. **Freshness is explicit.** Never claim a location is current when it is not. Every sample carries a timestamp and accuracy.
8. **No mandatory backend.** Internet transport is deliberately deferred. Future online transports must remain provider-agnostic and optional.
9. **No covert surveillance.** No hidden microphone capture, audio recording, secret calls, or invisible tracking.

## Phase 1 scope

### Protect mode — Android

A minimal visible UI allows the protected user to:

- add/remove trusted contacts;
- configure which safety triggers each trusted contact may use;
- review permissions and app readiness;
- see/cancel an active safety session;
- manually send an "I'm OK" / status response;
- optionally enable emergency callback for selected trusted contacts.

Initial triggers:

- N missed calls from one trusted contact within a configurable time window; calls do not need to be consecutive;
- a configurable number of missed calls from trusted contacts;
- an authenticated SMS command from a trusted contact;
- Android low-battery event;
- manual status/check-in from the Protect UI.

Initial response fields:

- location;
- location accuracy;
- sample timestamp;
- battery percentage;
- charging state;
- usable internet state (yes/no), for information only in Phase 1;
- sample/session sequence metadata.

Primary transport: **SMS only**.

### Decoder — platform-neutral

The person checking on the protected phone may use iPhone or another non-Android device. Phase 1 therefore does **not** require a second native mobile app.

Instead, the repository should include a tiny static, offline-capable decoder/PWA that:

- accepts a compact SMS payload by paste/share;
- decodes it entirely on-device/in-browser;
- displays location, accuracy, timestamp, battery, charging and network status;
- generates a map link locally after decoding;
- can combine several samples from one safety session and highlight the best estimate;
- has no server dependency and sends no telemetry.

## Emergency callback

A trusted contact may request an **Emergency Callback** only if the protected user explicitly enabled that capability for that contact.

The implementation must be a normal Android outgoing phone call through the system telecom stack. It must be visible on the protected device and cancellable. A remote request must never activate the microphone directly, record audio, create a hidden call, hide call UI, or bypass Android's normal call indicators.

Recommended default behavior: **Ask first** or a visible countdown before placing the call. Automatic callback, if implemented, must be an explicit opt-in setting and rate-limited.

## Internet transport

Internet delivery is **out of scope for Phase 1** even when connectivity exists. The app may report whether usable internet is available, but it must not depend on any private server, Firebase project, hosted API, or developer-operated infrastructure.

Phase 2 should define a transport adapter interface so users can optionally choose a self-hosted endpoint or a third-party relay without changing the core safety engine. See `docs/ROADMAP.md`.

## Documentation

- [`AGENTS.md`](AGENTS.md) — implementation instructions for Codex/agents
- [`docs/PRODUCT_SPEC.md`](docs/PRODUCT_SPEC.md) — full product requirements
- [`docs/ANDROID_ARCHITECTURE.md`](docs/ANDROID_ARCHITECTURE.md) — Android architecture and event/state model
- [`docs/SMS_PROTOCOL.md`](docs/SMS_PROTOCOL.md) — compact command/response protocol
- [`docs/SECURITY_PRIVACY.md`](docs/SECURITY_PRIVACY.md) — consent, abuse prevention and threat model
- [`docs/TEST_PLAN.md`](docs/TEST_PLAN.md) — functional, reliability and device test matrix
- [`docs/ROADMAP.md`](docs/ROADMAP.md) — phased implementation plan
- [`docs/DISCOVERY_NOTES.md`](docs/DISCOVERY_NOTES.md) — design rationale and conversation-derived decisions
- [`docs/DEVIATIONS.md`](docs/DEVIATIONS.md) — Android/build-environment restrictions that forced a deviation from the spec, and why

## Distribution note

SMS and Call Log permissions are highly restricted on Google Play. Early builds should be treated as open-source/private-test builds distributed through GitHub Releases or another appropriate channel. Play Store publication needs a separate policy review and may require a materially different permission strategy.

## Status

**Phase 1A/1B implemented.**

- `:core` — pure Kotlin/JVM domain layer: trusted-contact + capability model, missed-call
  trigger rules (per-contact and aggregate), the `TriggerEngine`, SMS command
  authentication/replay/rate-limiting, the status + command SMS protocol codec, and the
  `SafetySession` state machine. 120 unit tests, all passing.
- `:app` — Android/Kotlin/Jetpack Compose scaffold: Protect home screen, trusted-contact
  CRUD with per-contact capability toggles, Room + DataStore persistence, a permission
  readiness screen skeleton. No dangerous permissions requested yet and no platform event
  adapters (BroadcastReceivers) wired — that is Phase 1C onward, intentionally out of scope
  here. This module could not be build-verified in the environment that wrote it; see
  `docs/DEVIATIONS.md`.
- `tools/decoder/` — offline static decoder/PWA, dependency-free, with its own test suite
  sharing vectors with `:core`'s protocol tests.

Start by reading `AGENTS.md` and the documents under `docs/` before writing production code.
Phase 1C+ (real SMS/call-log/telecom/background-location integration) should build on this
domain layer rather than around it.
