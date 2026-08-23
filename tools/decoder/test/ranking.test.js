// TEST_PLAN.md "Sample ranking":
//   new ±10m beats old ±150m
//   very old ±5m may lose to fresh ±15m
//   obvious geographic outlier is flagged
//   moving sequence is not averaged into a fake midpoint
//   location unavailable sample doesn't replace valid sample

import { test } from "node:test";
import assert from "node:assert/strict";
import { groupBySession, dedupeByRawPayload, pickBestEstimate, flagOutliers, looksLikeMovement } from "../ranking.js";
import { InternetState } from "../protocol.js";

const NOW_MINUTE = 30000000;

function sample({ sequence, lat, lon, acc, ageMinutes, sessionId = "1", raw }) {
  return {
    sessionId,
    sequence,
    location: lat === null ? null : { latitude: lat, longitude: lon, accuracyMeters: acc },
    batteryPercent: 50,
    charging: false,
    internetState: InternetState.UNKNOWN,
    timestampEpochMinute: NOW_MINUTE - ageMinutes,
    raw: raw ?? `sample-${sessionId}-${sequence}`,
  };
}

test("a fresh, more accurate sample beats an old, much less accurate one", () => {
  const oldFar = sample({ sequence: 1, lat: 36.297, lon: 59.123, acc: 150, ageMinutes: 20 });
  const newClose = sample({ sequence: 2, lat: 36.298, lon: 59.124, acc: 10, ageMinutes: 0 });
  const best = pickBestEstimate([oldFar, newClose], NOW_MINUTE);
  assert.equal(best, newClose);
});

test("a very old accurate sample can lose to a fresh, merely-good sample", () => {
  const veryOldAccurate = sample({ sequence: 1, lat: 36.297, lon: 59.123, acc: 5, ageMinutes: 90 });
  const freshOkay = sample({ sequence: 2, lat: 36.298, lon: 59.124, acc: 15, ageMinutes: 0 });
  const best = pickBestEstimate([veryOldAccurate, freshOkay], NOW_MINUTE);
  assert.equal(best, freshOkay);
});

test("a location-unavailable sample never replaces a valid sample as the best estimate", () => {
  const valid = sample({ sequence: 1, lat: 36.297, lon: 59.123, acc: 40, ageMinutes: 5 });
  const unavailable = sample({ sequence: 2, lat: null, lon: null, acc: null, ageMinutes: 0 });
  const best = pickBestEstimate([valid, unavailable], NOW_MINUTE);
  assert.equal(best, valid);
});

test("all samples unavailable yields no best estimate", () => {
  const a = sample({ sequence: 1, lat: null, lon: null, acc: null, ageMinutes: 5 });
  const b = sample({ sequence: 2, lat: null, lon: null, acc: null, ageMinutes: 0 });
  const best = pickBestEstimate([a, b], NOW_MINUTE);
  assert.equal(best, null);
});

test("an obvious geographic outlier is flagged, not silently trusted or hidden", () => {
  const first = sample({ sequence: 1, lat: 36.297, lon: 59.123, acc: 20, ageMinutes: 5 });
  // ~1000km away one minute later — physically impossible, should be flagged.
  const outlier = sample({ sequence: 2, lat: 45.5, lon: 59.123, acc: 20, ageMinutes: 4 });
  const flags = flagOutliers([first, outlier]);
  assert.equal(flags.has(outlier), true);
  assert.equal(flags.has(first), false);
});

test("normal walking-speed movement between samples is not flagged as an outlier", () => {
  const a = sample({ sequence: 1, lat: 36.29700, lon: 59.12300, acc: 20, ageMinutes: 5 });
  const b = sample({ sequence: 2, lat: 36.29705, lon: 59.12305, acc: 20, ageMinutes: 0 });
  const flags = flagOutliers([a, b]);
  assert.equal(flags.size, 0);
});

test("a moving sequence is detected but never collapsed into an averaged point", () => {
  const a = sample({ sequence: 1, lat: 36.2000, lon: 59.1000, acc: 20, ageMinutes: 10 });
  const b = sample({ sequence: 2, lat: 36.2500, lon: 59.1500, acc: 20, ageMinutes: 0 });
  assert.equal(looksLikeMovement([a, b]), true);
  // pickBestEstimate must return one of the actual samples, never a synthesized midpoint.
  const best = pickBestEstimate([a, b], NOW_MINUTE);
  assert.ok(best === a || best === b);
});

test("small GPS jitter within combined accuracy is not reported as movement", () => {
  const a = sample({ sequence: 1, lat: 36.29700, lon: 59.12300, acc: 50, ageMinutes: 5 });
  const b = sample({ sequence: 2, lat: 36.29701, lon: 59.12301, acc: 50, ageMinutes: 0 });
  assert.equal(looksLikeMovement([a, b]), false);
});

test("groupBySession groups and sorts chronologically by timestamp then sequence", () => {
  const s1a = sample({ sequence: 2, lat: 1, lon: 1, acc: 10, ageMinutes: 0, sessionId: "A" });
  const s1b = sample({ sequence: 1, lat: 1, lon: 1, acc: 10, ageMinutes: 5, sessionId: "A" });
  const s2a = sample({ sequence: 1, lat: 2, lon: 2, acc: 10, ageMinutes: 0, sessionId: "B" });

  const groups = groupBySession([s1a, s1b, s2a]);
  assert.equal(groups.size, 2);
  assert.deepEqual(groups.get("A"), [s1b, s1a]);
  assert.deepEqual(groups.get("B"), [s2a]);
});

test("dedupeByRawPayload drops exact duplicate raw messages", () => {
  const a = sample({ sequence: 1, lat: 1, lon: 1, acc: 10, ageMinutes: 0, raw: "same" });
  const duplicate = sample({ sequence: 1, lat: 1, lon: 1, acc: 10, ageMinutes: 0, raw: "same" });
  const distinct = sample({ sequence: 2, lat: 1, lon: 1, acc: 10, ageMinutes: 0, raw: "different" });
  const result = dedupeByRawPayload([a, duplicate, distinct]);
  assert.equal(result.length, 2);
});
