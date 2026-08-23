# Product Specification — WhereAreYou

## 1. Product definition

WhereAreYou is a lightweight Android safety utility for situations where a family member, older parent, dependent person, or other consenting protected user is unexpectedly unreachable.

It is intentionally **not** a continuous family-location tracker. The application is mostly dormant and reacts to specific device events. When an authorized trigger occurs, it starts a short safety session, gathers device status, and replies using SMS.

### Core question

> A trusted family member is not answering. What useful evidence can their phone provide without requiring them to open an app, without requiring internet access, and without tracking them continuously?

## 2. Origin scenario

The initial use case was repeated unanswered calls to a parent. The calls were separated over time rather than made consecutively. The desired behavior was:

1. recognize that the same trusted person had called repeatedly without an answer;
2. wake the safety logic without a permanently running service;
3. collect location, location accuracy, battery and connectivity state;
4. send a compact SMS response;
5. collect a few additional samples over several minutes because the first location may be stale or inaccurate;
6. let the recipient combine the samples into a better current estimate.

This scenario is the primary design reference for Phase 1.

## 3. Product modes

The same Android APK may conceptually support multiple roles in the future, but Phase 1 implements **Protect mode** only.

### Protect mode

Installed on the phone of the person being protected.

The app is deliberately visible. Its existence should not be hidden. If another person finds the phone during a crisis, the app may help them identify which trusted contacts should be called.

The UI should remain minimal and calm. The primary object is the list of **Trusted Contacts**.

### Check/Decoder mode

A full second native app is not required in Phase 1. The checking person may use iPhone or another platform.

Use a static client-side decoder/PWA instead. A future version may add a native companion mode.

## 4. Users

### Protected user

Examples:

- parent;
- older family member;
- person who lives alone;
- family member travelling alone;
- person who explicitly wants a safety fallback.

The protected user owns the device, installs/configures the app, grants permissions, and chooses trusted contacts.

### Trusted contact

A family member/caregiver explicitly added by the protected user.

There may be several trusted contacts: for example multiple children/siblings may all need the ability to check on one parent.

## 5. Protect UI

Keep the application simple rather than feature-heavy.

### Home

Show:

- app readiness/status;
- trusted contacts;
- primary `Add trusted contact` action;
- current/last safety-session state if relevant;
- concise permission problems requiring attention;
- a visible way to view emergency contact information.

### Trusted contact entry

Fields/settings:

- display name;
- normalized phone number;
- enabled/disabled;
- missed-call trigger allowed;
- SMS command allowed;
- receives low-battery alert;
- eligible for manual status send;
- Emergency Callback allowed (default off);
- shared command code/PIN or equivalent authentication material.

Avoid broad address-book access where possible. Manual number entry or a user-driven contact picker is preferred.

### Safety-session UI

When a session is active, display a clear local notification and/or in-app state:

- reason it started;
- which trusted contact triggered it;
- how many responses have been sent;
- next planned best-effort sample;
- `Cancel` action;
- optional `I'm OK` action.

## 6. Phase 1 triggers

### T1 — Repeated missed calls from one trusted contact

Default development rule:

- 3 missed calls;
- from the same trusted contact;
- within 60 minutes;
- calls do **not** need to be consecutive.

Example:

```text
12:10 missed from A
12:26 missed from A
12:58 missed from A
=> safety trigger for A
```

Reset/suppression conditions should include:

- a call with that contact is answered;
- protected user calls that contact back;
- rule window expires;
- the resulting safety session finishes and enters cooldown.

Threshold/window must be configurable later; domain logic must not assume exactly `3/60`.

### T2 — Aggregate missed calls from trusted contacts

Optional configurable rule:

- if total missed calls from trusted contacts reaches a defined number within a time window, trigger a safety session.

This is distinct from T1 and should be independently switchable.

### T3 — Authenticated SMS request

A trusted contact sends a recognizable safety command to the Protect phone.

Desired human concept:

```text
باباکجایی-پاسخ-خودکار
```

The actual protocol should use:

- sender phone-number authorization;
- a configurable command token/phrase;
- a shared short code/PIN or another lightweight anti-spoofing value;
- replay and rate-limit protection.

The user-facing phrase and wire protocol do not have to be identical.

### T4 — Low battery

Listen for Android's system low-battery condition. When received:

- read actual battery percentage and charging state;
- optionally send a compact low-battery status to configured trusted contacts;
- do not run a permanent battery polling service merely to catch exactly 10%.

A future version may add richer battery thresholds if they can be implemented without compromising the event-driven model.

### T5 — Manual local status/check-in

The protected user can explicitly send an `I'm OK` or current-status response to one or more trusted contacts.

## 7. Safety session

A trigger creates a bounded `SafetySession`.

### Session fields

At minimum:

- session ID;
- trigger type;
- requester/trusted-contact ID when applicable;
- start timestamp;
- state;
- samples collected;
- messages sent;
- cancellation reason;
- completion reason;
- cooldown state.

### Immediate response strategy

Do not wait indefinitely for a perfect GPS fix.

Preferred behavior:

1. inspect last-known location;
2. if it exists, package it with age + accuracy and send/queue the first response quickly;
3. request a fresher location when platform state permits;
4. send improved samples when useful;
5. terminate after the bounded sampling window/max message count.

### Follow-up samples

Initial desired targets:

- T+0;
- approximately T+2 minutes;
- approximately T+5 minutes.

A later optional sample may be considered if accuracy is still poor.

These times are **best effort**. Android background execution restrictions mean exact execution must not be assumed.

### Why several samples?

The first location can be:

- stale;
- cell/Wi-Fi based rather than GPS based;
- temporarily inaccurate;
- captured while the device is moving.

Several time-stamped samples let the receiver see convergence or movement.

### Sample selection

Do not blindly average coordinates.

The receiving decoder should:

- retain each individual sample;
- identify obvious outliers;
- consider sample age;
- strongly consider reported accuracy;
- detect likely movement;
- highlight a best current estimate with its own uncertainty.

## 8. Response data

Phase 1 response payload should contain only useful compact fields:

- protocol version;
- session ID;
- sequence number;
- latitude;
- longitude;
- accuracy radius;
- sample timestamp;
- battery percentage;
- charging yes/no;
- validated/usable internet yes/no.

Possible later fields should not be added unless justified by safety value and SMS size.

## 9. SMS-first transport

SMS is the required Phase 1 transport because it can remain available when internet access is unavailable or unreliable.

### Requirements

- strive to fit each response in a single SMS segment;
- default format should avoid transmitted map URLs;
- avoid verbose English text in the wire payload;
- default profile should be suitable for networks where long/English/link-heavy messages may be less reliable;
- decoder reconstructs readable labels and map URL locally;
- duplicate or delayed messages must still be understandable through session/sequence/timestamp fields.

See `SMS_PROTOCOL.md`.

## 10. Internet status vs internet transport

Phase 1 may report whether the phone currently has usable internet access.

It must **not** transmit safety data over the internet in Phase 1.

Reason: an online channel implies choosing/operating a relay path, backend, identity model, push mechanism and privacy model. The project must not become dependent on the maintainer's private server or a mandatory proprietary service.

A future internet layer must use a provider-neutral transport abstraction. See `ROADMAP.md`.

## 11. Emergency Callback

### User need

In a serious situation, a trusted contact may want the Protect phone to place a callback so normal call audio can help determine whether someone nearby is able to respond.

### Product boundary

This must never become covert ambient listening.

Allowed design:

- protected user explicitly enables Emergency Callback per trusted contact;
- request is authenticated;
- app presents a visible notification/countdown or asks first according to the user's setting;
- the app places a **normal outgoing cellular call** using Android's telecom stack;
- standard call UI/indicators remain visible;
- the call can be cancelled locally;
- no independent microphone capture or recording occurs.

Prohibited design:

- hidden microphone activation;
- background audio recording;
- hiding/suppressing call UI;
- silently answering an incoming call;
- bypassing Android call indicators;
- calling arbitrary non-trusted numbers from a remote command.

### Modes

Recommended setting:

```text
Emergency Callback
- Off (default for new contact)
- Ask first
- Visible countdown / auto-call after delay (explicit opt-in)
```

Add rate limiting and cooldown.

This feature can be implemented after core SMS/location behavior proves stable if background telecom behavior requires device testing.

## 12. Permissions

Use least privilege and contextual onboarding.

Likely capabilities:

- location foreground permission;
- background location for remote-triggered location retrieval;
- receive SMS;
- send SMS;
- read call log / phone state for missed-call triggers;
- call phone only when Emergency Callback is enabled;
- network-state access;
- boot-completed if needed for state restoration/scheduling.

Avoid unless a concrete requirement appears:

- `READ_CONTACTS`;
- full SMS-history access (`READ_SMS`);
- microphone permission;
- audio-recording permission;
- arbitrary storage permission.

## 13. Transparency and consent

The app is not stealth software.

Requirements:

- visible launcher entry;
- clear onboarding explaining each capability;
- protected user chooses trusted contacts;
- remote actions show local evidence/notification when a safety session is active;
- ability to remove a trusted contact immediately;
- ability to revoke a capability per contact;
- no remote addition of trusted contacts;
- no remote activation of more powerful permissions;
- no telemetry by default.

## 14. Cooldowns and anti-abuse

Minimum safeguards:

- only trusted contacts can trigger remote behavior;
- optional shared code/PIN for SMS commands;
- command replay detection;
- trigger cooldowns;
- maximum messages per safety session;
- maximum safety sessions per trusted contact per period;
- Emergency Callback-specific cooldown;
- local event log visible to the protected user;
- reject malformed/unrecognized commands without leaking data.

## 15. Phase 1 decoder/PWA

The checking person may receive a compact payload on an iPhone.

Build a no-backend static decoder that:

1. accepts one or more raw payloads;
2. validates/version-checks them;
3. groups by session ID;
4. renders readable status;
5. shows each sample and its age/accuracy;
6. selects a best estimate;
7. builds a standard map link locally;
8. works offline after installation/load;
9. sends no data to any server.

A future iOS Shortcut can copy/share received SMS text into this decoder without requiring a native iOS app.

## 16. Non-goals for Phase 1

- continuous family tracking;
- geofencing;
- route history;
- driving analytics;
- crash detection;
- account system;
- subscription system;
- cloud sync;
- server-hosted family dashboard;
- social features;
- iOS native Protect app;
- covert surveillance;
- remote audio recording;
- mandatory Play Store publication.

## 17. Success criteria

A Phase 1 prototype is successful when, on supported physical Android devices:

1. Protect setup supports at least 3 independent trusted contacts.
2. Three non-consecutive missed calls from one authorized contact within the test window can trigger a session.
3. An authenticated SMS command from an authorized contact can trigger a session.
4. The first response can include best available location + age/accuracy, battery, charging and connectivity.
5. The app attempts bounded follow-up sampling without running a permanent service.
6. Responses stay within the defined compact SMS budget for normal coordinates/values.
7. Unauthorized callers/SMS senders receive no protected data.
8. A protected user can cancel an active session.
9. The decoder can combine several session samples and show a best estimate.
10. Product operation requires no internet server.

## 18. Distribution constraint

Google Play places strong restrictions on SMS and Call Log permissions, and its published invalid-use examples include family/device locator apps. Device automation and physical-safety exceptions exist for some permissions but require review and do not guarantee this product will qualify.

Therefore:

- do not design Phase 1 around Play Store approval;
- do not disguise the product category;
- use GitHub/private test distribution initially;
- revisit store-compatible permission architecture separately.
