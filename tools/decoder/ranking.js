// Multi-sample grouping and best-estimate scoring for one safety session.
// See /docs/SMS_PROTOCOL.md section 13 "Best-estimate scoring" and
// /docs/TEST_PLAN.md "Sample ranking" — rules stay simple and explainable, samples are
// never averaged/blended into a fake midpoint, and every individual sample stays visible.

const EARTH_RADIUS_METERS = 6371000;
/** Distance-equivalent penalty per minute of age, tuned so a fresh, mediocre-accuracy
 *  sample beats a stale, better-accuracy one (TEST_PLAN.md: "very old ±5m may lose to
 *  fresh ±15m"). */
const AGE_PENALTY_METERS_PER_MINUTE = 2;
/** Above this implied speed between two consecutive fixes, flag the newer one as a
 *  likely outlier rather than silently trusting it. */
const IMPLAUSIBLE_SPEED_KMH = 300;

function toRadians(deg) {
  return (deg * Math.PI) / 180;
}

export function haversineMeters(a, b) {
  const dLat = toRadians(b.latitude - a.latitude);
  const dLon = toRadians(b.longitude - a.longitude);
  const lat1 = toRadians(a.latitude);
  const lat2 = toRadians(b.latitude);
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2;
  return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(h)));
}

/** Groups decoded status payloads by sessionId, sorted chronologically within each group. */
export function groupBySession(payloads) {
  const groups = new Map();
  for (const p of payloads) {
    if (!groups.has(p.sessionId)) groups.set(p.sessionId, []);
    groups.get(p.sessionId).push(p);
  }
  for (const list of groups.values()) {
    list.sort((a, b) => a.timestampEpochMinute - b.timestampEpochMinute || a.sequence - b.sequence);
  }
  return groups;
}

/** Drops exact duplicate raw payloads (e.g. a carrier redelivering the same SMS). */
export function dedupeByRawPayload(payloads) {
  const seen = new Set();
  const result = [];
  for (const p of payloads) {
    const key = p.raw ?? JSON.stringify(p);
    if (seen.has(key)) continue;
    seen.add(key);
    result.push(p);
  }
  return result;
}

/** Lower is better; samples without a location never win. */
function distancePenalty(sample, nowEpochMinute) {
  if (!sample.location) return Infinity;
  const ageMinutes = Math.max(0, nowEpochMinute - sample.timestampEpochMinute);
  return sample.location.accuracyMeters + ageMinutes * AGE_PENALTY_METERS_PER_MINUTE;
}

/**
 * Picks the best current estimate among one session's samples. Returns null if none of
 * the samples has a location (TEST_PLAN.md: "location unavailable sample doesn't replace
 * valid sample" — an unavailable sample simply never has a lower penalty than a real one).
 */
export function pickBestEstimate(samples, nowEpochMinute) {
  let best = null;
  let bestPenalty = Infinity;
  for (const sample of samples) {
    const penalty = distancePenalty(sample, nowEpochMinute);
    if (penalty < bestPenalty) {
      bestPenalty = penalty;
      best = sample;
    }
  }
  return best;
}

/**
 * Flags samples whose implied speed from the previous chronological fix is implausible.
 * Returns a Map from sample -> explanation string; samples are still shown, never hidden.
 */
export function flagOutliers(samplesSortedByTime) {
  const flags = new Map();
  const withLocation = samplesSortedByTime.filter((s) => s.location);
  for (let i = 1; i < withLocation.length; i++) {
    const prev = withLocation[i - 1];
    const cur = withLocation[i];
    const distanceMeters = haversineMeters(prev.location, cur.location);
    const minutesElapsed = Math.max(1 / 60, cur.timestampEpochMinute - prev.timestampEpochMinute);
    const speedKmh = (distanceMeters / 1000) / (minutesElapsed / 60);
    if (speedKmh > IMPLAUSIBLE_SPEED_KMH) {
      flags.set(cur, `~${Math.round(speedKmh)} km/h implied from the previous sample — possible outlier`);
    }
  }
  return flags;
}

/** True if consecutive samples show a consistent, non-trivial displacement (for UI framing only). */
export function looksLikeMovement(samplesSortedByTime) {
  const withLocation = samplesSortedByTime.filter((s) => s.location);
  if (withLocation.length < 2) return false;
  const first = withLocation[0];
  const last = withLocation[withLocation.length - 1];
  const distanceMeters = haversineMeters(first.location, last.location);
  const combinedAccuracy = first.location.accuracyMeters + last.location.accuracyMeters;
  return distanceMeters > combinedAccuracy;
}
