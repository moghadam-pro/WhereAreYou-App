# Android Architecture — WhereAreYou

## 1. Architectural goal

Build a reliable **event-driven** safety agent without continuous GPS polling or an always-running service.

Android is allowed to wake the app for supported system events. Event receivers must do minimal work, persist an event, evaluate authorization/rules, and hand bounded work to an appropriate scheduler/session component.

The architecture must acknowledge modern Android background-execution restrictions rather than assuming a receiver can freely start long-running background work.

## 2. High-level flow

```text
System / user event
      |
      v
Event Adapter / BroadcastReceiver
      |
      v
Normalize Event
      |
      v
Trusted Contact Authorization
      |
      v
Trigger Engine
      |
   no | yes
      |   |
      |   v
      | SafetySessionOrchestrator
      |   |
      |   +--> SnapshotProvider
      |   |      - battery
      |   |      - charging
      |   |      - connectivity
      |   |      - last-known location
      |   |
      |   +--> LocationProvider (fresh fix when allowed)
      |   |
      |   +--> SessionScheduler (bounded follow-ups)
      |   |
      |   +--> ProtocolEncoder
      |   |
      |   +--> SmsTransport
      |   |
      |   +--> Local notification / cancel action
      |   |
      |   `--> SessionStore
      |
      `--> stop
```

## 3. Core domain types

Keep these independent from Android APIs as much as possible.

### TrustedContact

```kotlin
data class TrustedContact(
    val id: String,
    val displayName: String,
    val canonicalPhoneNumber: String,
    val enabled: Boolean,
    val capabilities: ContactCapabilities,
    val commandSecret: String?
)
```

### ContactCapabilities

```kotlin
data class ContactCapabilities(
    val missedCallTrigger: Boolean,
    val smsCommand: Boolean,
    val receiveLowBatteryAlert: Boolean,
    val manualStatusRecipient: Boolean,
    val emergencyCallback: EmergencyCallbackMode
)
```

### TriggerEvent

Suggested sealed types:

```text
MissedCallObserved
AnsweredCallObserved
OutgoingCallToTrustedContact
SmsCommandReceived
SystemLowBattery
ManualStatusRequested
SessionCancelledLocally
```

### SafetyTrigger

A validated/evaluated event that is allowed to start a safety session.

Contains:

- trigger type;
- initiating trusted-contact ID if applicable;
- event timestamp;
- rule evidence (for example missed-call count/window);
- authentication result;
- correlation/replay key when applicable.

### SafetySession

States:

```text
CREATED
COLLECTING_INITIAL
FIRST_RESPONSE_SENT
WAITING_FOR_FOLLOWUP
COLLECTING_FOLLOWUP
COMPLETED
CANCELLED
FAILED
COOLDOWN
```

A session must be bounded by:

- maximum lifetime;
- maximum samples;
- maximum outbound SMS count;
- cooldown rules.

## 4. Event adapters

### SMS

Use `Telephony.Sms.Intents.SMS_RECEIVED_ACTION` / SMS received broadcast behavior as supported by the target Android versions.

Receiver responsibilities:

1. parse sender + message body;
2. normalize phone number;
3. reject sender if not a trusted contact with SMS capability;
4. parse/validate command;
5. hand a small immutable event to the domain layer;
6. return quickly.

Do not scan SMS history just to find commands if the received broadcast contains the required message content.

### Phone state / missed calls

Use Android telephony/call-state signals to know a call state changed, then reconcile against call log as needed to identify a missed call.

Important implementation detail: call-state broadcasts can be noisy/duplicated depending on permissions/API behavior. Do not count transitions directly as missed calls. Create a reconciliation layer that deduplicates by call-log identity/timestamp/number.

Missed-call logic belongs in the domain rule engine, not the receiver.

### Low battery

Use the system low-battery event rather than permanent polling.

At event time, read the sticky battery state / `BatteryManager` values to obtain actual percentage and charging status.

### Boot

Only use boot-completed events for restoring lightweight application state/scheduled intent if the implementation genuinely requires it. Do not use boot as an excuse to start a permanent service.

## 5. Trigger engine

The trigger engine consumes normalized events and persisted recent event history.

### Same-contact missed-call rule

Pseudo-code:

```text
on MissedCall(contact, time):
  if !contact.enabled or !contact.missedCallTrigger:
    ignore

  add event
  calls = missed calls from contact in configured window
             excluding events before last reset/suppression marker

  if calls >= threshold and contact not in cooldown:
      emit SafetyTrigger(REPEATED_MISSED_CALLS)
```

### Reset/suppression markers

Create markers when:

- a call with the contact is answered;
- protected user successfully calls the contact;
- a session caused by that contact completes;
- manual cancellation specifies a suppression/cooldown.

### SMS command rule

Required checks in order:

1. canonical sender is a trusted contact;
2. SMS trigger capability enabled;
3. syntax/version recognized;
4. shared code/authentication valid;
5. nonce/request ID not replayed;
6. rate limit/cooldown permits request;
7. emit trigger.

Return no protected data for rejected commands.

## 6. Snapshot provider

Create a synchronous/quick `DeviceSnapshotProvider` for values that do not require waiting for GPS:

```text
battery percentage
charging state
network capability / validated internet state
last-known location (if available)
last-known location timestamp
last-known location accuracy
```

This enables a useful first response without waiting for a fresh location fix.

## 7. Location strategy

### Do not promise exact real-time location

Every location object must contain:

```text
latitude
longitude
accuracyMeters
capturedAt
source/provider (internal/debug; not necessarily transmitted)
```

Calculate `age = now - capturedAt` in UI/decoder.

### Initial location

At session start:

1. inspect the newest reasonable last-known location;
2. if present, prepare an immediate sample and clearly retain its timestamp/accuracy;
3. concurrently/best-effort request a fresher fix;
4. if fresh fix improves the estimate, send a later sequence message.

### Follow-up behavior

Desired sampling targets: roughly 0, 2, and 5 minutes.

Treat them as scheduling goals, not guarantees.

Modern Android restricts foreground-service starts from background state. A location foreground service also has special restrictions. If the app needs reliable timed follow-up location from background, it must use platform-supported mechanisms and permissions rather than a hidden always-on workaround.

### Scheduler abstraction

Define:

```kotlin
interface SessionScheduler {
    suspend fun scheduleFollowUp(sessionId: String, targetAt: Instant): ScheduleResult
    suspend fun cancel(sessionId: String)
}
```

Possible implementation tiers:

#### Standard mode

- immediate receiver work stays short;
- use last-known/current-location APIs when allowed;
- use best-effort WorkManager/OS scheduling for follow-up attempts;
- accept that exact 2/5 minute execution is not guaranteed.

#### Enhanced Reliability Mode (optional)

If justified by physical-device testing, offer an explicit user-facing reliability option that may request special scheduling/battery settings.

Potential strategy:

- `ACCESS_BACKGROUND_LOCATION`;
- user-granted exact-alarm capability where appropriate;
- exact alarm acts as a valid wake point for time-sensitive follow-up work;
- start a visible, bounded location foreground service only when Android allows it;
- immediately stop it after sample collection.

Do not silently request or assume special access. Document device/OEM limitations.

## 8. Temporary foreground work

The product rule is **no permanent service**, not **never use a foreground service**.

A temporary foreground service may be appropriate during a user-visible safety session if Android requires it for reliable work and permission/state rules allow it.

Requirements:

- visible notification;
- reason explains that a safety check is active;
- cancel action;
- bounded timeout;
- stop immediately when session completes/cancels;
- no sticky permanent restart loop.

## 9. SMS transport abstraction

```kotlin
interface SafetyTransport {
    suspend fun send(
        recipient: TrustedContact,
        payload: EncodedPayload
    ): TransportResult
}
```

Phase 1 implementation:

```text
SmsSafetyTransport
```

Future implementations can be added without changing trigger/session logic.

The core domain must not know about HTTP endpoints, Firebase or developer servers.

## 10. Connectivity state

Report **usable/validated internet**, not merely whether Wi-Fi/mobile-data toggles are enabled.

Suggested internal model:

```kotlin
enum class InternetState {
    VALIDATED,
    UNAVAILABLE,
    UNKNOWN
}
```

Wire protocol may compress this to a small code.

Phase 1 does not send data over internet.

## 11. Emergency Callback architecture

Treat Emergency Callback as a separate capability after authorization.

Flow:

```text
Authenticated SMS callback request
        |
        v
TrustedContact capability check
        |
        v
Rate-limit / active-call safety checks
        |
        v
Local visible notification
        |
  Ask-first or countdown policy
        |
        v
TelecomManager.placeCall(trustedNumber)
        |
        v
Normal Android call UI / indicators
```

Do not create a microphone service. Do not request `RECORD_AUDIO` for this feature. The only audio path is the normal cellular/system call path.

If reliable call placement directly from a background SMS event is blocked on a target Android/OEM version, degrade to a visible high-priority callback notification rather than attempting a covert workaround.

## 12. Persistence

Persist only what is required to make rules reliable and auditable.

### DataStore

Good for:

- trigger thresholds/windows;
- enabled capabilities;
- app readiness flags;
- cooldown settings;
- protocol preferences.

### Room (if needed)

Good for:

- trusted contacts if richer structured data is useful;
- deduplicated recent call events;
- recent command IDs/nonces;
- safety sessions;
- local audit/event history.

Set a retention policy. Do not retain long location histories by default.

Recommended default: automatically purge old session/location details after a short period while retaining minimal non-sensitive diagnostics if needed.

## 13. Phone-number normalization

Incoming phone numbers can differ in formatting from stored values.

Do not compare raw strings.

Requirements:

- canonicalize country code when enough context exists;
- handle `+`, national prefix and formatting characters;
- prefer a tested phone-number library or Android normalization utilities;
- store original display value and canonical comparison value separately;
- create unit tests for Iranian/international-style numbers as well as generic E.164 cases.

## 14. Permission readiness model

Represent permission state explicitly rather than scattering checks across UI.

Example:

```text
LocationForeground: granted/missing
LocationBackground: granted/missing/not-required
ReceiveSms: granted/missing
SendSms: granted/missing
CallLog: granted/missing/not-required
PhoneState: granted/missing/not-required
CallPhone: granted/missing/not-required
Notifications: granted/missing
ExactAlarm: available/unavailable/not-enabled
BatteryOptimization: normal/exempt (optional only)
```

Show which product feature is disabled when a permission is missing.

## 15. Failure behavior

### No location permission

Send a status payload without location if SMS response itself is allowed; mark location unavailable.

### Location services disabled

Use any valid last-known location if available and mark age/accuracy; otherwise mark unavailable.

### No SMS permission / SIM unavailable

Record local session failure. Do not loop endlessly.

### Dual SIM

Do not assume one subscription. Phase 1 may initially use the system/default SMS subscription, but architecture must isolate subscription choice so dual-SIM handling can be improved.

### Airplane mode / no cellular service

Queueing/retry must be bounded. A delayed SMS should retain the original sample timestamp so the receiver does not mistake it for a new location.

### App force-stopped

Android may prevent normal background behavior after a user explicitly force-stops an app. Document this limitation in onboarding/help; do not claim guaranteed operation after force-stop.

### Device reboot

Restore configuration; verify whether platform scheduling/session should resume. Do not resend stale emergency messages automatically without a clear rule.

## 16. Android references / constraints

Implementation should be checked against current official Android documentation, especially:

- Broadcasts: https://developer.android.com/develop/background-work/background-tasks/broadcasts
- SMS received intents: https://developer.android.com/reference/android/provider/Telephony.Sms.Intents
- Phone state: https://developer.android.com/reference/android/telephony/TelephonyManager
- Foreground-service background restrictions: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- Foreground-service location type: https://developer.android.com/develop/background-work/services/fgs/service-types
- TelecomManager: https://developer.android.com/reference/android/telecom/TelecomManager
- Battery intents: https://developer.android.com/reference/android/content/Intent

Do not implement from outdated blog examples without verifying current target-SDK rules.
