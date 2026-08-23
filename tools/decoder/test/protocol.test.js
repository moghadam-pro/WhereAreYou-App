// Shares its test vectors with core/src/test/kotlin/.../protocol/StatusProtocolTest.kt and
// CommandProtocolTest.kt per AGENTS.md "The PWA decoder and Android encoder must share the
// same documented test vectors." Run with: node --test test/

import { test } from "node:test";
import assert from "node:assert/strict";
import {
  encodeStatus,
  decodeStatus,
  encodeCommand,
  decodeCommand,
  InternetState,
  CommandCode,
} from "../protocol.js";

function samplePayload(overrides = {}) {
  return {
    sessionId: "3174",
    sequence: 1,
    location: { latitude: 36.29714, longitude: 59.12345, accuracyMeters: 18 },
    batteryPercent: 9,
    charging: false,
    internetState: InternetState.VALIDATED,
    timestampEpochMinute: 29784562,
    ...overrides,
  };
}

test("documented example encodes to the documented wire string", () => {
  assert.equal(encodeStatus(samplePayload()), "و1|3174|1|3629714|5912345|18|9|0|1|29784562");
});

test("documented wire string decodes to the documented fields", () => {
  const result = decodeStatus("و1|3174|1|3629714|5912345|18|9|0|1|29784562");
  assert.equal(result.ok, true);
  assert.equal(result.payload.sessionId, "3174");
  assert.equal(result.payload.sequence, 1);
  assert.equal(result.payload.location.latitude, 36.29714);
  assert.equal(result.payload.location.longitude, 59.12345);
  assert.equal(result.payload.location.accuracyMeters, 18);
  assert.equal(result.payload.batteryPercent, 9);
  assert.equal(result.payload.charging, false);
  assert.equal(result.payload.internetState, InternetState.VALIDATED);
  assert.equal(result.payload.timestampEpochMinute, 29784562);
});

test("encode then decode round trips for positive coordinates", () => {
  const payload = samplePayload({ location: { latitude: 36.29714, longitude: 59.12345, accuracyMeters: 18 } });
  const decoded = decodeStatus(encodeStatus(payload));
  assert.equal(decoded.ok, true);
  assert.deepEqual(decoded.payload.location, payload.location);
});

test("encode then decode round trips for negative coordinates", () => {
  const payload = samplePayload({ location: { latitude: -6.26031, longitude: -35.12345, accuracyMeters: 42 } });
  const decoded = decodeStatus(encodeStatus(payload));
  assert.equal(decoded.ok, true);
  assert.deepEqual(decoded.payload.location, payload.location);
});

test("battery boundary 0 round trips", () => {
  const decoded = decodeStatus(encodeStatus(samplePayload({ batteryPercent: 0 })));
  assert.equal(decoded.payload.batteryPercent, 0);
});

test("battery boundary 100 round trips", () => {
  const decoded = decodeStatus(encodeStatus(samplePayload({ batteryPercent: 100 })));
  assert.equal(decoded.payload.batteryPercent, 100);
});

test("accuracy at the representable cap round trips", () => {
  const decoded = decodeStatus(
    encodeStatus(samplePayload({ location: { latitude: 1, longitude: 1, accuracyMeters: 9999 } })),
  );
  assert.equal(decoded.payload.location.accuracyMeters, 9999);
});

test("accuracy above the representable cap is clamped on encode", () => {
  const encoded = encodeStatus(samplePayload({ location: { latitude: 1, longitude: 1, accuracyMeters: 50000 } }));
  const decoded = decodeStatus(encoded);
  assert.equal(decoded.payload.location.accuracyMeters, 9999);
});

test("no-location response omits coordinates and accuracy", () => {
  const encoded = encodeStatus(samplePayload({ location: null }));
  assert.equal(encoded, "و1|3174|1|-|-|-|9|0|1|29784562");
  const decoded = decodeStatus(encoded);
  assert.equal(decoded.ok, true);
  assert.equal(decoded.payload.location, null);
});

test("internet state 0/1/2 round trip", () => {
  for (const state of [InternetState.UNAVAILABLE, InternetState.VALIDATED, InternetState.UNKNOWN]) {
    const decoded = decodeStatus(encodeStatus(samplePayload({ internetState: state })));
    assert.equal(decoded.payload.internetState, state);
  }
});

test("unsupported version is rejected", () => {
  const result = decodeStatus("و2|3174|1|3629714|5912345|18|9|0|1|29784562");
  assert.equal(result.ok, false);
  assert.match(result.reason, /version/);
});

test("malformed field count is rejected", () => {
  const result = decodeStatus("و1|3174|1|3629714|5912345|18|9|0|1");
  assert.equal(result.ok, false);
});

test("partial missing location fields are rejected rather than guessed", () => {
  const result = decodeStatus("و1|3174|1|3629714|-|-|9|0|1|29784562");
  assert.equal(result.ok, false);
});

test("non-numeric field is rejected", () => {
  const result = decodeStatus("و1|3174|one|3629714|5912345|18|9|0|1|29784562");
  assert.equal(result.ok, false);
});

test("battery out of range is rejected", () => {
  const result = decodeStatus("و1|3174|1|3629714|5912345|18|150|0|1|29784562");
  assert.equal(result.ok, false);
});

test("garbage input never throws and always fails cleanly", () => {
  assert.doesNotThrow(() => decodeStatus("not a payload at all"));
  assert.equal(decodeStatus("not a payload at all").ok, false);
});

test("carrier-added surrounding whitespace is trimmed before decoding", () => {
  const result = decodeStatus("  و1|3174|1|3629714|5912345|18|9|0|1|29784562  \n");
  assert.equal(result.ok, true);
});

test("compact command round trips for status request", () => {
  const payload = { command: CommandCode.STATUS_REQUEST, requestId: "4821", authCode: "7314" };
  assert.equal(encodeCommand(payload), "ک1|1|4821|7314");
  const decoded = decodeCommand(encodeCommand(payload));
  assert.equal(decoded.ok, true);
  assert.equal(decoded.payload.command, CommandCode.STATUS_REQUEST);
});

test("compact command round trips for emergency callback request", () => {
  const payload = { command: CommandCode.EMERGENCY_CALLBACK_REQUEST, requestId: "9001", authCode: "7314" };
  const decoded = decodeCommand(encodeCommand(payload));
  assert.equal(decoded.ok, true);
  assert.equal(decoded.payload.command, CommandCode.EMERGENCY_CALLBACK_REQUEST);
});

test("unknown command code is rejected", () => {
  const result = decodeCommand("ک1|99|4821|7314");
  assert.equal(result.ok, false);
});
