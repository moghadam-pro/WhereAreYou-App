# Roadmap — WhereAreYou

## Guiding rule

Do not turn Phase 1 into a full family-tracking platform. Build the smallest reliable event-driven safety loop first.

---

## Phase 0 — Product discovery and technical framing

Status: **documented**

Outputs:

- product definition;
- trigger model;
- trusted-contact model;
- SMS-first strategy;
- compact protocol proposal;
- Android background constraints;
- safety/privacy boundary;
- internet transport deferred;
- native iOS checker deferred;
- physical-device test strategy.

Open decisions before public release:

- final product name/branding;
- open-source license (for example permissive vs copyleft);
- final protocol marker/command phrases;
- minimum supported Android version after device testing.

---

## Phase 1A — Buildable Android foundation

Goal: establish structure and testable domain logic before requesting dangerous permissions.

Deliverables:

- Kotlin/Compose project;
- Protect-mode home screen;
- trusted-contact CRUD;
- per-contact capability settings;
- DataStore/persistence foundation;
- permission-readiness screen;
- domain `TriggerEngine`;
- `SafetySession` state model;
- phone-number normalization;
- unit tests.

No real SMS/location/call automation is required to finish 1A.

### Exit criteria

- app builds cleanly;
- 3+ trusted contacts supported;
- trigger rules can be simulated by tests/dev tools;
- no backend/network dependency.

---

## Phase 1B — Compact SMS protocol + decoder

Goal: lock the offline communication contract early.

Deliverables:

- pure Kotlin protocol encoder/decoder;
- shared protocol test vectors;
- Android segment-length validation;
- static offline-capable decoder/PWA under `tools/decoder/`;
- local map-link generation;
- multiple-sample grouping/ranking;
- no analytics/no backend.

### Exit criteria

- Android and PWA decode the same test vectors;
- normal payload fits one Unicode SMS segment;
- decoder works offline on iPhone Safari/PWA.

---

## Phase 1C — Real SMS commands and responses

Goal: trigger a safety session from a trusted SMS without internet.

Deliverables:

- `SMS_RECEIVED` integration;
- trusted sender authorization;
- shared command code/authentication;
- request ID/replay protection;
- SMS sending;
- rate limiting;
- local audit events;
- malformed/unauthorized silent rejection.

### Exit criteria

- authorized SMS triggers test session while app is not open;
- unauthorized sender never receives protected data;
- response remains one segment in test cases.

---

## Phase 1D — Missed-call trigger

Goal: reproduce the original real-world scenario.

Deliverables:

- phone/call-state integration;
- call-log reconciliation/deduplication;
- 3-in-window rule;
- answered/outgoing-call reset behavior;
- aggregate trusted-contact rule optional/configurable;
- cooldowns.

### Exit criteria

Three non-consecutive missed calls from the same trusted contact within the configured window produce exactly one authorized safety session.

---

## Phase 1E — Location + device snapshot safety session

Goal: provide useful evidence quickly and improve it over a few bounded samples.

Deliverables:

- battery snapshot;
- charging state;
- validated internet state;
- last-known location + age/accuracy;
- fresh location attempt;
- bounded follow-up scheduler;
- safety-session notification and cancel action;
- SMS sample sequence;
- decoder best-estimate timeline.

### Sampling target

Best effort:

```text
T+0
~T+2m
~T+5m
```

Do not promise exact wake times.

### Enhanced Reliability experiment

Only after standard-mode physical testing:

- evaluate background-location access;
- evaluate exact alarms/special scheduling if justified;
- evaluate temporary visible location foreground service;
- document OEM differences;
- keep this optional and user-visible.

### Exit criteria

Original missed-call and SMS-command scenarios work end-to-end on multiple physical devices without a permanent service.

---

## Phase 1F — Low battery + manual status

Deliverables:

- Android system low-battery event;
- actual battery/charging value capture;
- recipient selection;
- manual `I'm OK` / send-status action;
- rate/cost safeguards.

Avoid permanent battery polling.

---

## Phase 1G — Emergency Callback (optional/experimental)

This feature is useful but should not block the core location/SMS MVP.

Goal: allow a previously authorized trusted contact to request a **normal visible callback** during a crisis.

Deliverables:

- per-contact setting: Off / Ask first / Countdown;
- authenticated callback command;
- visible local notification/countdown;
- `TelecomManager` normal outgoing call;
- local cancel;
- active-call/emergency-call checks;
- callback cooldown;
- safe fallback to notification if background call placement is restricted.

Strictly excluded:

- microphone capture API;
- recording;
- hidden call;
- auto-answer incoming call;
- arbitrary-number remote dialing.

### Exit criteria

Any implemented callback remains indistinguishable from a normal visible Android call in terms of system UI/indicators and cannot be used as a hidden listening channel.

---

## Phase 1H — Hardening and release candidate

Deliverables:

- two+ OEM physical-device matrix;
- multiple Android versions;
- dual-SIM behavior documented;
- Doze/battery-saver tests;
- permission revocation tests;
- retention/purge behavior;
- crash-free malformed input handling;
- README install/config guide;
- GitHub Release APK;
- privacy/safety documentation.

Distribution target for early release: GitHub/open-source testing rather than depending on Google Play approval.

---

# Phase 2 — Optional internet transport

## Why deferred

If the Protect phone has internet, richer data could be sent more easily. But choosing an internet path introduces:

- server ownership;
- identity/account model;
- push/wake model;
- hosting cost;
- trust and privacy;
- encryption/key management;
- availability/censorship risk;
- dependency on a maintainer-operated service.

The project explicitly does **not** want Phase 1 tied to one private server.

## Architectural requirement

Define a provider-neutral transport interface:

```text
SafetyTransport
  - SmsTransport          [Phase 1]
  - InternetTransport     [interface only]
      - GenericWebhook?   [future]
      - SelfHostedRelay?  [future]
      - ThirdPartyRelay?  [future]
```

The safety engine produces a transport-independent envelope. Adapters decide how to deliver it.

## Recommended Phase 2 research directions

### Option A — User-configured encrypted webhook

User supplies an HTTPS endpoint.

Pros:

- simple;
- self-hostable;
- no central WhereAreYou service.

Cons:

- setup is technical;
- endpoint must somehow notify checker device;
- endpoint metadata/privacy still matters.

### Option B — Reference open-source relay

Project publishes a tiny relay server users/providers can host.

Requirements:

- relay does not require plaintext location;
- E2E-encrypted payload;
- minimal persistence;
- easy Docker deployment;
- no hardcoded maintainer domain.

### Option C — Pluggable third-party relay adapters

Allow optional adapters to established messaging/notification systems.

Do not make any one provider mandatory. Assess privacy, wake reliability and regional availability before choosing supported adapters.

## Internet behavior principle

Even after Phase 2 exists:

- SMS remains a fallback transport;
- app should report which transport delivered a sample;
- network failure must not prevent SMS safety behavior;
- duplicate delivery across transports must be deduplicatable by session/sequence IDs.

---

# Phase 3 — Native checker apps (optional)

Only if real usage justifies them.

Possible additions:

- Android Check mode in same APK;
- native iOS checker app;
- automatic local SMS parsing where platform policy permits;
- encrypted contact pairing;
- richer location timeline;
- one-tap authenticated command generation.

Until then, keep the PWA decoder sufficient for non-Android trusted contacts.

---

# Phase 4 — Store distribution strategy

## Google Play

Needs separate investigation because SMS and Call Log permissions are restricted and family/device locator is explicitly called out in invalid-use examples, despite some device-automation/physical-safety exceptions.

Possible paths to research:

- whether the exact event-driven safety/automation core qualifies for an approved exception;
- whether a Play edition can preserve enough value with a different trigger architecture;
- whether distribution should remain GitHub/F-Droid/direct for the full-capability edition.

Never misrepresent features or permission purposes.

## Other stores / direct distribution

Assess after MVP reliability and security are proven.

---

# Future ideas — not commitments

Only consider after core use is validated:

- device-recovered/back-online notification;
- simple emergency contact card;
- optional encrypted internet relay;
- cryptographic pairing instead of static SMS PIN;
- device-auth protection for sensitive settings;
- user-configurable retention;
- localization;
- native checker app;
- accessibility-focused setup;
- caregiver templates.

Avoid feature creep into route tracking, social/location history or lifestyle monitoring unless the product direction intentionally changes.
