# Discovery Notes & Decision Log

Date of initial discovery: 2026-08-23

This document preserves the reasoning behind the current product specification so future contributors/agents do not accidentally turn the project into a generic GPS tracker.

## 1. Original problem

The project began after a real incident:

- a son repeatedly called his father;
- the phone rang, but the father did not answer;
- calls were separated by time rather than made back-to-back;
- uncertainty was the problem: there was no lightweight way to know whether the phone was at home, moving, low on battery, offline, or simply unattended;
- after the father was found and the immediate concern ended, the goal became to create a fallback for the next time this happens.

The initial thought was: install a small app on the father's Android phone that could return location/status when a recognizable safety situation occurs.

## 2. First market question

Before building, the idea was compared conceptually with existing categories:

### Mainstream family/location products

- Google Maps Location Sharing
- Apple Find My / Family Sharing
- Life360

These already solve continuous/explicit location sharing well, and Life360 includes family safety, battery and no-show concepts.

However, the desired project is intentionally different:

- does not require continuous sharing;
- should not depend on internet;
- should not require both people to use the same full native app;
- should wake only on meaningful events;
- should use SMS as a resilient fallback;
- should be small/open and understandable.

### Privacy/self-hosted trackers

- OwnTracks
- Traccar

These provide strong open/self-hosted location infrastructure, but are primarily tracking/reporting systems rather than the event-driven safety interaction desired here.

### SMS-oriented open-source references

#### PhoneTrack SMS

Repository:

https://github.com/gideontek/phonetrack

Relevant ideas:

- Android;
- SMS request/response;
- no server/data connection required;
- location, accuracy and battery response.

Difference from WhereAreYou direction:

- WhereAreYou is centered on safety events, especially repeated missed calls;
- response protocol should avoid map URLs and remain extremely compact;
- multiple short follow-up samples form one safety session;
- multiple trusted contacts/capabilities are first-class.

#### Anchor

Repository:

https://github.com/aunchagaonkar/Anchor

Relevant ideas:

- remote SMS commands;
- phone whitelist/password;
- location/device information;
- callback command;
- no mandatory internet.

Anchor is a useful implementation reference, but it is positioned as lost/stolen-device remote control and includes behaviors/features outside the desired scope. Do not clone its product model or permissions wholesale. Review its license before reusing source code.

#### Traccar Client SDK

Reference:

https://www.traccar.org/traccar-client-sdk/

The SDK is open source and solves difficult background tracking problems. The July 2026 release supports native Android/iOS plus Flutter/React Native and does not require Google Play Services.

However, its core model is persistent background tracking with a foreground location service and server upload. That conflicts with WhereAreYou's Phase 1 principle of **event-driven bounded sessions rather than permanent tracking**.

Decision:

- do not make Traccar Client SDK the core Phase 1 engine;
- keep `LocationProvider` abstract so useful implementation ideas/providers can be evaluated later;
- internet/server tracking is not needed for MVP.

## 3. Refined product concept

The concept evolved from "family GPS tracker" to:

> **An event-driven family safety agent.**

The protected phone is normally idle. It reacts when an event suggests that a trusted person is trying to check on the owner.

This framing is central.

## 4. User-proposed trigger events

The initial desired triggers were:

1. More than a configured number of calls from one contact are unanswered.
2. Total unanswered calls on the phone reach a configured threshold.
3. Battery becomes critically/very low.
4. A specific SMS phrase arrives from a specific authorized number.

Important clarification:

Repeated calls do **not** need to be consecutive. Example: three unanswered calls from the same trusted contact spread across an hour should count.

## 5. User-proposed permissions/capabilities

Initial list included:

- location;
- SMS access;
- call-log access;
- send SMS;
- make calls;
- internet if available.

The permission model was refined to least privilege:

- receive SMS rather than reading the entire SMS inbox where possible;
- call-log/phone-state only for missed-call rules;
- avoid broad contacts permission by using manual entry/system contact picker;
- call permission only if Emergency Callback is enabled;
- no microphone/audio-recording permission for callback;
- internet state may be inspected, but no Phase 1 online upload.

## 6. Primary missed-call scenario

Desired flow:

```text
Trusted contact calls father -> unanswered
wait
Trusted contact calls again -> unanswered
wait
Trusted contact calls third time -> unanswered

=> Protect app recognizes configured safety pattern
=> starts bounded safety session
=> sends status via SMS
=> attempts better location samples over the next several minutes
```

The app should not need to be open during this flow.

## 7. SMS command scenario

Desired human interaction:

A trusted person sends a recognizable message such as:

```text
باباکجایی-پاسخ-خودکار
```

The Protect app recognizes the command and returns the same safety information.

Security refinement:

The phrase alone is not authentication. Final command validation should combine:

- trusted sender number;
- contact capability;
- shared code/auth value;
- replay/rate limiting.

A later decoder/PWA may generate compact authenticated command messages so users do not need to remember protocol syntax.

## 8. Why SMS payload must be short

The original environment assumes SMS may be more resilient than data but still imperfect.

Observed/design concern:

- multipart SMS is less desirable because losing one segment harms the response;
- link-heavy or English-heavy messages may be less reliable/desirable in some network conditions;
- the response should therefore be short, structured and self-decodable.

Decision:

- default to one Unicode SMS segment;
- no map link in the message;
- compact numeric fields;
- decoder generates the map link locally;
- check segment count on the Android device before automatic send.

## 9. Multi-sample location idea

A single location fix can be stale or inaccurate.

Original request:

- send several responses;
- roughly every 2–5 minutes;
- use the combined result to make location more reliable.

Refinement:

- each sample carries timestamp and accuracy;
- do not average latitude/longitude blindly;
- show all samples;
- rank by freshness/accuracy/consistency;
- movement should remain visible rather than collapsing into a fake midpoint.

Desired initial targets:

```text
T+0
~T+2m
~T+5m
```

Android reality:

These are best-effort targets. Background execution and location foreground-service rules can prevent exact timing. The architecture therefore uses a scheduler abstraction and optional later Enhanced Reliability mode rather than pretending exact wakeups are guaranteed.

## 10. Device status in SMS

Core Phase 1 response:

- location;
- accuracy;
- timestamp;
- battery;
- charging state;
- usable/validated internet yes/no;
- session ID;
- sequence.

Important distinction:

"Internet on" is not the same as usable internet. Report validated/reachable state where platform APIs can determine it.

## 11. One app, roles and platform reality

The idea of one product with two logical sides was accepted:

### Protect side

Native Android app.

### Checking side

The original checking user does not use Android, while Phase 1 development is intentionally Android-only.

Decision:

Do not build a native iOS app yet. Build a simple static offline-capable decoder/PWA that runs on iPhone and other platforms.

The decoder:

- receives text by paste/share;
- decodes locally;
- groups multi-sample sessions;
- generates map link locally;
- requires no account/backend.

A native checker app can be Phase 3 if usage justifies it.

## 12. Protect UI clarification

The Protect app is **not hidden** and should not try to disappear.

The protected user needs a minimal interface primarily to manage **multiple trusted contacts**.

Reason:

A father may have several children/siblings/caregivers who should all be able to check on him in unusual circumstances.

The app's visibility is actually useful: if another person has legitimate access to the phone during a crisis, they can see that safety contacts exist.

Privacy refinement:

Do not expose all family numbers on the lock screen by default. Emergency-contact-card behavior can be a later explicit option.

## 13. Per-contact capabilities

Trusted contacts should not be one undifferentiated whitelist.

Each contact can independently be allowed to:

- trigger through repeated missed calls;
- request status by SMS;
- receive low-battery alert;
- receive manual status;
- request Emergency Callback.

Emergency Callback defaults off.

## 14. Emergency Callback idea

Original reason for requesting call permission:

In a serious situation, the checking person may ask the Protect phone to place a call back. When answered, a normal phone call can reveal whether the protected person or somebody nearby responds and can naturally convey surrounding sound.

Critical boundary added during design:

This must not become covert listening software.

Decision:

- only trusted contact;
- explicitly enabled by protected user;
- authenticated command;
- normal outgoing Android telecom call;
- visible system call UI/indicators;
- local cancel;
- default Off/Ask-first, optional visible countdown after explicit opt-in;
- rate limit;
- no microphone service;
- no `RECORD_AUDIO`;
- no audio recording/upload;
- no hidden call;
- no auto-answering incoming calls;
- no arbitrary remote dialing.

If Android/OEM restrictions prevent reliable automatic background callback, fall back to a visible callback notification rather than bypassing platform protections.

## 15. No permanent service

A key product constraint from the beginning:

> The app should not keep a service running all the time merely to monitor GPS/state.

Refinement:

A **temporary visible foreground service during an active safety session** is not philosophically forbidden if Android requires it. The prohibited architecture is a permanent tracker/daemon.

The system should normally wake from supported events, do bounded work and go idle again.

## 16. Low-battery behavior

Initial idea mentioned a fixed threshold such as under 10%.

Android provides a system low-battery event, but the exact threshold is controlled by the system/OEM.

Decision for MVP:

- react to system low-battery event;
- read actual battery percentage at that moment;
- do not create a permanent polling service just to hit exactly 10%.

More exact thresholds can be researched later if they preserve event-driven operation.

## 17. Internet transport discussion

If the protected phone has internet, it could send richer/more frequent status than SMS.

But online transport immediately creates questions:

- Which server?
- Who operates it?
- Does the project become dependent on one personal/private service?
- How are identities/push notifications handled?
- How is location encrypted?
- What happens when the server is blocked/offline?

The project owner explicitly does **not** want the public project tied to a personal server.

Decision:

**Internet transport is out of scope for Phase 1.**

Phase 1 may report internet availability only.

Future architecture must use a pluggable `SafetyTransport` abstraction and may offer:

- user-provided encrypted webhook;
- self-hostable open-source relay;
- optional third-party relay adapters.

No mandatory WhereAreYou central server.

## 18. Distribution discussion

Google Play's current SMS/Call Log permission policy is a significant constraint.

Relevant policy:

https://support.google.com/googleplay/android-developer/answer/10208820

It recognizes some exceptions such as device automation and physical-safety SMS sending, but also explicitly includes family/device locator in invalid-use examples for SMS/Call Log permissions.

Decision:

- do not optimize MVP around Play approval;
- do not disguise the product to obtain restricted permissions;
- initial open-source builds can be tested via GitHub/direct distribution;
- Play-specific architecture/review belongs to a later phase.

## 19. Technical Android validation during discovery

Official Android docs confirm/shape these architectural facts:

- manifest-declared broadcast receivers can wake an app for supported broadcasts, but receivers must return quickly;
- `SMS_RECEIVED` exists as a broadcast event;
- phone-state change broadcast exists, while number visibility depends on permissions;
- Android restricts starting foreground services from background state;
- location foreground services have additional background-location constraints;
- low-battery system broadcast exists;
- `TelecomManager.placeCall` uses normal Android telecom behavior and requires phone-call permission for managed cellular calls.

References:

- https://developer.android.com/develop/background-work/background-tasks/broadcasts
- https://developer.android.com/reference/android/provider/Telephony.Sms.Intents
- https://developer.android.com/reference/android/telephony/TelephonyManager
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/develop/background-work/services/fgs/service-types
- https://developer.android.com/reference/android/content/Intent
- https://developer.android.com/reference/android/telecom/TelecomManager

## 20. Product gap hypothesis

The opportunity is not "another location tracker."

The hypothesis is that there is value in a very small utility with this combination:

```text
consensual
+ visible
+ event-driven
+ SMS-first
+ no mandatory internet
+ several trusted contacts
+ repeated-missed-call trigger
+ compact multi-sample session
+ open source
+ optional future self-hosting
```

Existing products solve overlapping pieces, but this specific workflow is the design target.

## 21. Current Phase 1 decisions summary

Locked for implementation unless physical testing disproves them:

- Android Protect app only;
- minimal visible UI;
- multiple trusted contacts;
- no permanent tracker service;
- repeated missed calls are a core trigger;
- authenticated SMS is a core trigger;
- system low battery is a core trigger;
- SMS is the only required transport;
- first response uses best available location quickly;
- follow-up location samples are bounded and best effort;
- location timestamp + accuracy always matter;
- no map link in SMS;
- PWA decoder for iPhone/non-Android checking side;
- no Phase 1 backend;
- Emergency Callback is normal visible telecom only;
- no covert microphone/audio functionality;
- GitHub/open testing before Play-store optimization.

## 22. Open questions for implementation/testing

Codex/maintainers should investigate rather than guess:

1. Which Android API/device combinations are most reliable for detecting a true missed call without duplicate counts?
2. What is the best standard-mode scheduler for ~2/~5 minute follow-up attempts without exact guarantees?
3. Is optional exact-alarm/special-access worth the setup burden for Enhanced Reliability?
4. Which location provider gives the best no-GMS compatibility vs accuracy/battery tradeoff?
5. How should dual-SIM outbound SMS subscription be chosen?
6. Which phone-number normalization library/strategy best handles intended regions?
7. What is the minimum auth mechanism acceptable for an initial private prototype, and when should it move to HMAC-style commands?
8. Which open-source license should this repository use?
9. Which Android versions/OEMs should define the initial support matrix?
10. Does Emergency Callback behave consistently enough from locked/background states to remain an automated feature, or should it be notification-assisted only on some devices?

## 23. Research links

- PhoneTrack SMS: https://github.com/gideontek/phonetrack
- Anchor: https://github.com/aunchagaonkar/Anchor
- OwnTracks: https://owntracks.org/
- Traccar Client SDK: https://www.traccar.org/traccar-client-sdk/
- Apple Find My family location: https://support.apple.com/en-ie/guide/iphone/iph6231f621a/26/ios/26
- Life360 aging-parent positioning: https://www.life360.com/aging-parents
- Life360 No Show Alerts: https://support.life360.com/hc/en-us/articles/34106208950935-No-Show-Alerts
- Google Play SMS/Call Log policy: https://support.google.com/googleplay/android-developer/answer/10208820
