# SMS Protocol — WhereAreYou Phase 1

## 1. Goals

The Phase 1 protocol is optimized for:

- one-segment SMS responses;
- unreliable/limited internet environments;
- networks where long, link-heavy or English-heavy messages may be less desirable;
- deterministic decoding;
- several samples from one safety session;
- no dependency on a central server.

The protocol should remain understandable enough to debug manually while still being compact.

## 2. SMS encoding constraint

A message containing Persian/Unicode text is normally encoded as UCS-2/Unicode rather than GSM-7, reducing the single-message character budget substantially (commonly about 70 UCS-2 code units rather than 160 GSM-7 characters).

Because the default wire marker/command may contain Persian text, **design and test against a one-Unicode-segment budget**.

On Android, use platform SMS length calculation before send (for example the current equivalent of `SmsMessage.calculateLength`) and assert the message count. If a payload would become multipart, compact/drop optional fields before allowing an automatic multi-part response.

Do not include emoji.

## 3. Design decisions

- No map URL is transmitted.
- Coordinates are encoded as signed integers at E5 precision to remove decimal points.
- Timestamp is absolute, not "now" or relative prose.
- Session and sequence are included so delayed/out-of-order SMS messages can be combined.
- Accuracy is always transmitted when location exists.
- Battery and network state stay compact.
- Protocol is versioned from the first implementation.

## 4. Status response v1

Recommended wire shape:

```text
و1|SID|SEQ|LAT|LON|ACC|BAT|CHG|NET|TIME
```

Example:

```text
و1|3174|1|3629714|5912345|18|9|0|1|29784562
```

Meaning:

```text
version:          1
sessionId:        3174
sequence:         1
latitude:         36.29714
longitude:        59.12345
accuracy:         18 m
battery:          9%
charging:         false
internet:         validated/usable
timestamp:        epoch minutes
```

The literal marker `و1` is only a proposed v1 marker; keep the parser versioned so it can change before release if testing finds a carrier issue.

## 5. Field definitions

### `SID`

Short numeric safety-session identifier.

Requirements:

- generated per session;
- sufficient entropy/collision resistance for recent local history;
- does not need to be globally unique forever;
- decoder groups samples by SID.

Recommended initial representation: 4–6 decimal digits.

### `SEQ`

1-based sample sequence number.

Example:

```text
1
2
3
```

Do not assume all sequence numbers will arrive.

### `LAT`, `LON`

Signed integer coordinates at E5 precision.

Encoding:

```text
round(latitude * 100000)
round(longitude * 100000)
```

Decoding:

```text
value / 100000.0
```

E5 precision is already finer than typical phone-location accuracy and saves characters.

Examples:

```text
36.29714  -> 3629714
-6.26031  -> -626031
```

### `ACC`

Reported horizontal accuracy radius in meters, rounded to integer.

Recommended bounds:

```text
0..9999
```

If platform accuracy exceeds the representable cap, clamp and treat as very poor accuracy.

### `BAT`

Battery percent:

```text
0..100
```

### `CHG`

```text
0 = not charging / unknown-not-charging
1 = charging
```

### `NET`

```text
0 = no validated usable internet
1 = validated usable internet
2 = unknown
```

This is connectivity information only. Phase 1 never uploads the status over internet.

### `TIME`

Unix/epoch time expressed in whole minutes.

Reason:

- timezone independent;
- compact;
- sufficient precision for a safety-session timeline;
- delayed SMS remains honest about sample age.

Decoder converts to local human-readable time.

## 6. No-location response

If no valid last-known/fresh location exists, do not fabricate coordinates.

Use a reserved missing-location representation, for example:

```text
و1|3174|1|-|-|-|9|0|1|29784562
```

Decoder should show:

```text
Location unavailable
Battery 9%
Internet available
Sample time ...
```

Exact missing-field syntax should be frozen in protocol tests before release.

## 7. Payload priority / compaction

If carrier/platform length calculation reports more than one SMS segment, preserve fields in this priority:

1. version;
2. session ID;
3. sequence;
4. coordinates;
5. accuracy;
6. timestamp;
7. battery;
8. charging;
9. network state.

Normally all current fields fit comfortably in one Unicode segment, so dropping fields should be exceptional.

Do not automatically send a multipart response solely to preserve a low-priority field.

## 8. Multiple samples

Example session:

```text
و1|3174|1|3629650|5912298|140|9|0|1|29784562
و1|3174|2|3629702|5912338|42|9|0|1|29784564
و1|3174|3|3629714|5912345|8|8|0|0|29784567
```

Decoder should display all three rather than replacing history:

```text
#1 ±140m
#2 ±42m
#3 ±8m  <-- likely best current estimate
```

Do not average these points blindly.

## 9. Command messages

Phase 1 needs a safety-status request and may later support Emergency Callback.

### User-friendly command aliases

The protected user configures a recognizable phrase or uses a default localized phrase.

Examples:

```text
باباکجایی 7314
وضعیت 7314
تماس 7314
```

The exact Persian word is not security. Authorization comes from:

1. trusted sender number;
2. enabled capability for that sender;
3. shared code/authentication value;
4. rate limit/replay rules.

Normalize Persian/Arabic/Latin digits before parsing.

### Compact machine command form

For a future PWA/Shortcut command generator, reserve a compact structure:

```text
ک1|CMD|RID|AUTH
```

Suggested command codes:

```text
1 = request status
2 = request Emergency Callback
```

Example:

```text
ک1|1|4821|7314
```

`RID` is a request ID used for deduplication/replay handling.

`AUTH` in the first prototype may be a user-configured shared code. Before public release, consider replacing the static code with a short message-authentication code generated from a locally shared secret, while preserving a simple user experience.

## 10. Authorization and replay rules

A command is valid only if all checks pass:

```text
sender is trusted
AND contact is enabled
AND requested capability is enabled
AND command format is valid
AND auth is valid
AND RID has not already been accepted
AND rate limit/cooldown allows the action
```

Never send location or an error containing sensitive state to an unauthorized number.

For an unauthorized/malformed command, default behavior should be silent rejection + local audit entry.

## 11. Emergency Callback command

`CMD=2` does not mean "activate microphone".

It means:

> Ask the Protect app to initiate the configured, visible Emergency Callback flow to this already-trusted sender.

The local app then applies its contact setting:

```text
Off
Ask first
Visible countdown / explicit opt-in automatic callback
```

Only the normal Android telecom path may carry audio.

## 12. Decoder behavior

The decoder/PWA must:

- accept one or many lines/messages;
- trim carrier-added whitespace safely;
- reject unsupported protocol versions;
- parse signed coordinates;
- convert E5 values;
- convert epoch minutes to local time;
- compute message/sample age;
- group by session ID;
- sort by sample timestamp/sequence;
- show reported accuracy;
- identify likely best estimate;
- create a map URL **locally**, never require the URL in the SMS;
- preserve raw payload for debugging/copying.

## 13. Best-estimate scoring

Do not encode complex scoring into the wire protocol.

A starting decoder strategy may score samples using:

- freshness;
- reported accuracy;
- consistency with adjacent samples;
- likely movement.

Rules must remain explainable. Always let the user inspect individual samples.

## 14. Protocol test vectors

Create pure unit tests containing at minimum:

- positive coordinates;
- negative coordinates;
- battery 0 and 100;
- accuracy boundary;
- no-location response;
- internet 0/1/2;
- duplicate sequence;
- out-of-order sequence;
- unsupported version;
- malformed field count;
- message at one-segment size boundary;
- encoder -> decoder round trip.

The PWA decoder and Android encoder must share the same documented test vectors.
