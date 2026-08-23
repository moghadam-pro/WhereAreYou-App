# Test Plan — WhereAreYou Phase 1

## 1. Testing philosophy

The hardest parts of this product are not the screens. They are:

- Android background behavior;
- telephony event deduplication;
- permission state;
- SMS delivery/segment size;
- stale vs fresh location handling;
- OEM battery optimization;
- avoiding unauthorized disclosure.

Unit-test domain logic heavily, then validate the real behavior on physical devices.

## 2. Unit test suites

### Trusted contacts

Test:

- add/remove/disable contact;
- duplicate number handling;
- same number in international and local formats;
- capability enable/disable;
- disabled contact cannot trigger anything;
- Emergency Callback defaults off.

### Phone normalization

Include:

- `+98...` vs local Iranian formats;
- spaces/hyphens/parentheses;
- leading national trunk prefix;
- generic E.164 numbers;
- invalid/ambiguous values;
- dual-SIM device default country context where available.

### Missed-call rules

Test default 3-in-60 rule:

```text
3 missed / 60m -> trigger
2 missed / 60m -> no trigger
3 missed / 61m -> no trigger
3 non-consecutive calls from same contact -> trigger
calls from two different contacts -> independent counters
answered call in between -> reset/suppress according to rule
outgoing callback -> reset/suppress
replayed/duplicate call-log row -> not double counted
```

### Aggregate missed-call rule

Test:

- enabled vs disabled;
- multiple trusted contacts contribute;
- non-trusted callers do not contribute;
- answered call behavior;
- cooldown behavior.

### SMS command authorization

Test:

- trusted number + correct auth -> accept;
- trusted number + wrong auth -> reject;
- unknown number + correct-looking command -> reject;
- capability disabled -> reject;
- duplicate request ID -> reject;
- old/replayed request -> reject if freshness is implemented;
- malformed command -> reject;
- Persian digits and Latin digits normalization;
- surrounding whitespace;
- unexpected multipart input.

### Safety-session lifecycle

Test:

- session created once per accepted trigger;
- first response state;
- bounded follow-ups;
- completion after max samples;
- timeout;
- local cancellation;
- cooldown;
- new authorized session after cooldown;
- simultaneous requests from different trusted contacts;
- repeated trigger during existing session.

### Protocol

Use shared test vectors from `SMS_PROTOCOL.md`.

Test:

- encode/decode round trip;
- E5 coordinate precision;
- negative coordinates;
- location unavailable;
- timestamp conversion;
- battery 0/100;
- internet unknown;
- malformed field count;
- unsupported version;
- sequence ordering;
- duplicate samples;
- payload fits one segment under normal worst-case values.

### Sample ranking

Test cases:

```text
new ±10m beats old ±150m
very old ±5m may lose to fresh ±15m
obvious geographic outlier is flagged
moving sequence is not averaged into a fake midpoint
location unavailable sample doesn't replace valid sample
```

The final algorithm may evolve, but tests must document expected reasoning.

### Rate limits

Test:

- session cooldown;
- per-contact command rate;
- maximum outbound SMS per session;
- Emergency Callback cooldown;
- duplicate/replayed commands.

## 3. Android integration tests

### Permission state

Test each important permission independently missing and granted.

The app must show degraded behavior clearly rather than crash.

Examples:

- SMS receive missing;
- SMS send missing;
- call-log missing;
- fine location missing;
- background location missing;
- notifications missing;
- call permission missing when callback enabled.

### SMS receiver

Physical-device tests:

- app foreground;
- app background;
- app process killed by OS;
- screen locked;
- Doze/idle state;
- after device reboot;
- sender number local format vs international format;
- single-part command;
- duplicate SMS delivery if simulated.

### Call behavior

Test:

- incoming answered;
- incoming missed;
- incoming rejected;
- very short ring/hangup;
- multiple call-state transitions;
- call-log update delay;
- repeated caller separated over time;
- calls from non-trusted numbers;
- dual-SIM phone if available.

Verify one real missed call produces exactly one normalized `MissedCallObserved` event.

### Location

Test:

- fresh outdoor GPS;
- indoor Wi-Fi/cell estimate;
- location services off;
- airplane mode;
- stale last-known location;
- no last-known location;
- background location granted/not granted;
- screen locked;
- battery saver;
- Doze;
- app background restrictions enabled by OEM.

Record:

- time to first usable status;
- location age;
- reported accuracy;
- whether follow-up samples execute near target windows;
- battery impact during a bounded session.

### Battery trigger

Test Android system low-battery behavior on at least one physical device. Since forcing the real threshold is slow, unit/instrumentation tests may inject normalized `SystemLowBattery` events while at least one end-to-end physical run validates the platform receiver.

### Connectivity

Test:

- validated Wi-Fi;
- connected Wi-Fi with no internet;
- mobile data;
- airplane mode;
- captive portal if practical;
- transient switch between networks.

Report usable internet rather than raw toggle state.

## 4. Emergency Callback tests

Do not test covert audio because the product does not implement it.

Test:

- feature disabled -> callback command rejected;
- trusted contact enabled -> request accepted;
- unknown contact -> rejected;
- Ask-first mode;
- countdown mode;
- local cancel;
- active existing phone call;
- emergency call already active (must not interfere);
- phone locked;
- app background;
- OEM variants;
- normal system call UI remains visible;
- no `RECORD_AUDIO` permission exists for this feature;
- callback only targets the authenticated trusted contact;
- rate limit prevents repeated dialing.

If Android blocks background call initiation on a tested configuration, verify safe degradation to a visible notification/request.

## 5. Decoder/PWA tests

Test in at least:

- iOS Safari;
- installed iOS PWA if supported;
- desktop Safari/Chrome;
- Android Chrome.

Requirements:

- decode while offline after cached installation/load;
- no network requests when decoding;
- one sample;
- multiple samples same session;
- multiple sessions pasted together;
- malformed message;
- negative coordinates;
- local time conversion;
- map link generated locally;
- best-estimate selection matches shared test vectors.

Use browser developer tools/network logs to verify no telemetry or API calls.

## 6. SMS real-carrier test matrix

This product depends on real SMS behavior. Emulator tests are insufficient.

For each available carrier/network combination, record:

```text
message payload length
platform calculated segment count
actual delivery success
latency
sender formatting on recipient phone
whether message content is modified
multipart behavior if deliberately tested
```

Test default compact Persian/Unicode protocol on real networks relevant to early users.

## 7. Device/OEM matrix

Minimum before calling Phase 1 reliable:

- at least 2 Android OS generations;
- at least 2 OEMs;
- one device with aggressive battery optimization if possible;
- one dual-SIM device if possible.

Suggested result table:

| Device | Android | SMS trigger | Missed-call trigger | Initial status | T+2 sample | T+5 sample | Notes |
|---|---:|---|---|---|---|---|---|
| Device A | ... | ... | ... | ... | ... | ... | ... |

## 8. Failure injection

Simulate/inject:

- SMS send failure;
- no SIM;
- call-log query failure;
- permission revoked after setup;
- location timeout;
- process killed between samples;
- device reboot during session;
- duplicate scheduler callback;
- clock/timezone change;
- corrupted persisted session;
- full/failed local database write.

The app should fail closed: no unauthorized data leakage and no infinite retry loops.

## 9. Privacy tests

Verify:

- no analytics SDK;
- no network upload in Phase 1;
- no unrelated SMS content stored;
- no unrelated call history retained;
- location history retention/purge works;
- removed contact loses capabilities immediately;
- local audit is visible to protected user;
- lock-screen notifications do not expose more sensitive detail than intended.

## 10. Acceptance run — origin scenario

Reproduce the original scenario end-to-end:

1. Protect phone configured with trusted contact A.
2. A calls once; no answer.
3. Wait a realistic interval.
4. A calls a second time; no answer.
5. Wait again.
6. A calls a third time; no answer.
7. Protect app recognizes the threshold without needing to be open.
8. Recipient receives a compact first SMS containing best available status.
9. Decoder shows timestamp, accuracy, battery, charging and internet state.
10. Protect device attempts later bounded location samples.
11. Recipient combines later responses and sees improved/latest estimate.
12. No cloud service is involved.

A second acceptance run should trigger the same session through an authenticated SMS command rather than missed calls.
