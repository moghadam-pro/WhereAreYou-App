# Security, Privacy & Abuse Boundaries

## 1. Security posture

WhereAreYou handles highly sensitive data: location, call-event metadata, phone numbers and emergency actions. The product must be designed as a **consensual safety utility**, never as stealth monitoring software.

A technically possible behavior is not automatically an acceptable product behavior.

## 2. Consent model

The protected device owner must explicitly:

- install/open the app;
- complete onboarding;
- grant Android permissions;
- add trusted contacts;
- choose which capabilities each contact has;
- separately enable Emergency Callback for a contact if desired.

A remote party must never be able to:

- add themselves as trusted;
- enable a new capability;
- grant Android permissions;
- hide the application;
- turn on microphone recording;
- turn on continuous tracking.

## 3. Visibility

The app should have a normal launcher presence and a recognizable name/icon.

When a safety session is active, the protected device should have visible local evidence such as a notification and a cancel action, subject to Android notification/privacy behavior.

Do not implement:

- hidden launcher icon;
- misleading app identity;
- silent background microphone use;
- hidden call state;
- secret continuous GPS service.

## 4. Trusted contacts

Multiple trusted contacts are supported.

Each contact has independent permissions/capabilities. Use the principle of least privilege.

Recommended defaults:

```text
Missed-call trigger:       On
SMS status request:        On after auth setup
Low-battery recipient:     Configurable
Manual status recipient:   On
Emergency Callback:        Off
```

The protected user should be able to disable/remove a contact immediately.

## 5. Remote command authentication

Phone-number whitelisting alone is useful but not sufficient as a long-term security model because sender identity can be affected by number reassignment, SIM compromise or spoofing in some environments.

### Phase 1 minimum

Require:

- normalized trusted sender number;
- per-contact capability check;
- shared secret/code;
- request ID / deduplication;
- rate limit and cooldown.

### Public-release improvement

Prefer a short authenticated command generated from a shared secret, for example a truncated HMAC or TOTP-like construction, if this can be made usable without a native checker app.

The static PWA/decoder may later include an offline command generator that stores the shared secret only on the checking device.

Never send the protected location in response to an unverified command.

## 6. SMS security limitations

SMS is chosen for availability, not cryptographic security.

Assume:

- SMS is not end-to-end encrypted;
- a carrier/network or someone with access to either phone may see message contents;
- messages may be delayed, duplicated or delivered out of order;
- sender identity is not a perfect cryptographic proof;
- sent/received messages may remain in the system messaging history.

Mitigation:

- transmit the minimum data required;
- no personal prose/name in payload;
- no long route/history;
- bounded sampling;
- optional stronger command authentication;
- future internet transport should use authenticated encryption.

## 7. Location privacy

Location is released only after:

- local manual action; or
- a validated safety trigger tied to a trusted contact; or
- a locally enabled system alert rule such as low battery.

Do not maintain a permanent location history.

Recommended retention:

- keep only short recent safety-session history needed for debugging/audit;
- automatically purge precise coordinate history after a configurable short period;
- allow the protected user to clear local history immediately.

The SMS recipient naturally retains messages in their SMS history; explain this in onboarding.

## 8. Missed-call data

The app does not need a social graph or full call-history product.

Use call-log access only to establish the minimal facts needed by enabled missed-call rules and deduplication.

Do not upload call logs. Do not profile calling patterns. Do not store unrelated call history longer than necessary.

## 9. SMS access

Prefer `RECEIVE_SMS` event handling over full `READ_SMS` history access if the feature can be implemented without reading the inbox.

Do not parse unrelated message content beyond what is required to identify the authorized command event.

Do not upload or index the user's SMS history.

## 10. Emergency Callback boundary

### Intended user need

A trusted contact may want the protected phone to call back during a serious situation. Once the trusted contact answers, the communication is a normal phone call and may naturally convey surrounding sound as any ordinary call does.

### Mandatory safety boundary

Do **not** implement this as remote microphone access.

Allowed:

- authenticated request from a specifically authorized trusted contact;
- protected user has previously enabled callback behavior;
- visible local request/countdown;
- normal outgoing cellular/system phone call;
- standard Telecom/call UI and indicators;
- local cancel control;
- rate limiting.

Not allowed:

- `RECORD_AUDIO` for remote listening;
- hidden microphone service;
- recording/uploading ambient audio;
- hidden/zero-UI call;
- suppressing Android call notification/UI;
- auto-answering an incoming call to create a listening channel;
- remote arbitrary-number dialing;
- bypassing call/emergency restrictions.

### Recommended modes

```text
OFF
ASK_FIRST
COUNTDOWN_AUTO_CALL
```

Default: `OFF` for a newly added contact.

`COUNTDOWN_AUTO_CALL` requires explicit informed opt-in by the protected user and must visibly count down/cancel before a normal call is placed. If platform restrictions prevent reliable behavior, degrade safely to a callback notification.

## 11. Rate limiting

Suggested starting protections (make configurable/testable):

- one automatic missed-call safety session per trusted contact per cooldown window;
- SMS command requests limited per contact;
- maximum 3 normal location responses per session initially;
- Emergency Callback one attempt per longer cooldown;
- duplicate request IDs ignored;
- malformed/unauthorized requests never trigger a reply containing sensitive data.

Avoid SMS loops between two devices running automation.

## 12. Local audit log

Keep a short user-visible audit trail such as:

```text
13:10 — Status request from Sayid accepted
13:10 — SMS response #1 sent
13:12 — SMS response #2 sent
13:15 — Session completed
```

Also record rejected remote attempts locally without exposing protected data to the sender.

Audit data must be local-only in Phase 1.

## 13. Phone found by another person

The app should be recognizable so a person who legitimately has access to the unlocked phone can understand that trusted contacts exist.

Potential future feature: a simple emergency contact card.

Privacy requirement: do not expose family phone numbers on the lock screen by default. Lock-screen presentation needs a separate explicit privacy setting.

## 14. Lost/stolen protected phone

A stolen phone creates a different threat model from an unreachable family member.

Mitigations:

- app configuration changes should require normal device unlock/app access;
- remote SMS cannot add contacts or change policy;
- local audit should show remote requests;
- consider protecting sensitive settings behind device authentication in a later phase.

## 15. Number reassignment / SIM change

Trusted authorization is phone-number based in Phase 1, so a recycled/reassigned number could eventually become dangerous.

Mitigations:

- shared command secret in addition to number;
- ability to revoke contacts;
- periodic local reminder to review trusted contacts (future);
- optional contact key re-pairing in a later protocol.

## 16. Internet transport security (future)

Phase 1 has no online data path.

When Phase 2 internet transport is designed:

- no mandatory maintainer-owned server;
- payload encrypted before relay when feasible;
- relay should not need plaintext location;
- transport adapters are replaceable;
- users may select self-hosted or third-party providers;
- authenticate sender/recipient devices cryptographically;
- define key rotation/revocation;
- document metadata leakage separately from payload encryption.

## 17. Store/distribution policy

Google Play restricts SMS and Call Log permission groups. Its published policy includes exceptions such as some device automation and physical-safety cases, but also explicitly lists `Family or device locator` as an invalid use case for SMS/Call Log access.

Do not attempt to bypass this policy by mislabeling the app.

Reference:

https://support.google.com/googleplay/android-developer/answer/10208820

Early distribution should focus on transparent open-source testing outside a dependency on Play approval. A Play-compatible edition, if pursued, requires a separate policy review.

## 18. Safety disclaimer

WhereAreYou is a best-effort software safety aid, not an emergency service, medical device, police service, guaranteed location system or substitute for calling local emergency services when a real emergency is suspected.

The UI/documentation must not claim guaranteed delivery, guaranteed GPS freshness, or guaranteed background execution.
