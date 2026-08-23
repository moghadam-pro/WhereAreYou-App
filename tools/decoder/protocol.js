// Compact SMS wire protocol codec — JavaScript mirror of
// core/src/main/kotlin/com/whereareyou/core/protocol/StatusProtocol.kt and
// CommandProtocol.kt. See /docs/SMS_PROTOCOL.md for the format spec.
//
// This file is loaded directly by the browser (index.html, no build step) and by the
// Node test suite (test/protocol.test.js) against the same vectors as the Kotlin
// StatusProtocolTest/CommandProtocolTest, per AGENTS.md "The PWA decoder and Android
// encoder must share the same documented test vectors."

export const STATUS_VERSION = 1;
export const COMMAND_VERSION = 1;

const STATUS_MARKER_PREFIX = "و";
const COMMAND_MARKER_PREFIX = "ک";
const FIELD_SEPARATOR = "|";
const MISSING = "-";
const E5_SCALE = 100000;
const STATUS_FIELD_COUNT = 10;
const COMMAND_FIELD_COUNT = 4;
const MAX_ACCURACY_METERS = 9999;

const SUPPORTED_STATUS_VERSIONS = new Set([1]);
const SUPPORTED_COMMAND_VERSIONS = new Set([1]);

export const InternetState = Object.freeze({
  UNAVAILABLE: "UNAVAILABLE",
  VALIDATED: "VALIDATED",
  UNKNOWN: "UNKNOWN",
});

export const CommandCode = Object.freeze({
  STATUS_REQUEST: 1,
  EMERGENCY_CALLBACK_REQUEST: 2,
});

function fail(reason) {
  return { ok: false, reason };
}

function ok(payload) {
  return { ok: true, payload };
}

/** Strict integer parse: rejects anything parseInt would silently truncate (e.g. "12abc"). */
function parseStrictInt(field) {
  if (!/^-?\d+$/.test(field)) return null;
  const value = Number.parseInt(field, 10);
  return Number.isSafeInteger(value) ? value : null;
}

function clampAccuracy(meters) {
  return Math.min(Math.max(meters, 0), MAX_ACCURACY_METERS);
}

function encodeCoordinate(value) {
  return String(Math.round(value * E5_SCALE));
}

function decodeCoordinate(field) {
  const raw = parseStrictInt(field);
  return raw === null ? null : raw / E5_SCALE;
}

function encodeInternetState(state) {
  switch (state) {
    case InternetState.UNAVAILABLE: return "0";
    case InternetState.VALIDATED: return "1";
    case InternetState.UNKNOWN: return "2";
    default: throw new Error(`unknown internet state: ${state}`);
  }
}

function decodeInternetState(field) {
  switch (field) {
    case "0": return InternetState.UNAVAILABLE;
    case "1": return InternetState.VALIDATED;
    case "2": return InternetState.UNKNOWN;
    default: return null;
  }
}

/**
 * @param {{version?: number, sessionId: string, sequence: number,
 *   location: {latitude: number, longitude: number, accuracyMeters: number} | null,
 *   batteryPercent: number, charging: boolean, internetState: string, timestampEpochMinute: number}} payload
 * @returns {string}
 */
export function encodeStatus(payload) {
  const version = payload.version ?? STATUS_VERSION;
  const loc = payload.location;
  const fields = [
    `${STATUS_MARKER_PREFIX}${version}`,
    String(payload.sessionId),
    String(payload.sequence),
    loc ? encodeCoordinate(loc.latitude) : MISSING,
    loc ? encodeCoordinate(loc.longitude) : MISSING,
    loc ? String(clampAccuracy(loc.accuracyMeters)) : MISSING,
    String(payload.batteryPercent),
    payload.charging ? "1" : "0",
    encodeInternetState(payload.internetState),
    String(payload.timestampEpochMinute),
  ];
  return fields.join(FIELD_SEPARATOR);
}

/** @param {string} raw @returns {{ok: true, payload: object} | {ok: false, reason: string}} */
export function decodeStatus(raw) {
  const trimmed = String(raw).trim();
  const fields = trimmed.split(FIELD_SEPARATOR);
  if (fields.length !== STATUS_FIELD_COUNT) {
    return fail(`expected ${STATUS_FIELD_COUNT} fields, got ${fields.length}`);
  }
  const [markerField, sessionId, seqField, latField, lonField, accField, batField, chgField, netField, timeField] = fields;

  if (!markerField.startsWith(STATUS_MARKER_PREFIX)) return fail(`malformed version marker: '${markerField}'`);
  const version = parseStrictInt(markerField.slice(STATUS_MARKER_PREFIX.length));
  if (version === null) return fail(`malformed version marker: '${markerField}'`);
  if (!SUPPORTED_STATUS_VERSIONS.has(version)) return fail(`unsupported protocol version: ${version}`);

  if (sessionId.trim() === "") return fail("missing session id");

  const sequence = parseStrictInt(seqField);
  if (sequence === null) return fail(`malformed sequence: '${seqField}'`);

  let location = null;
  const missingCount = [latField, lonField, accField].filter((f) => f === MISSING).length;
  if (missingCount === 3) {
    location = null;
  } else if (missingCount > 0) {
    return fail("partial location fields");
  } else {
    const latitude = decodeCoordinate(latField);
    if (latitude === null) return fail(`malformed latitude: '${latField}'`);
    const longitude = decodeCoordinate(lonField);
    if (longitude === null) return fail(`malformed longitude: '${lonField}'`);
    const acc = parseStrictInt(accField);
    if (acc === null) return fail(`malformed accuracy: '${accField}'`);
    location = { latitude, longitude, accuracyMeters: clampAccuracy(acc) };
  }

  const battery = parseStrictInt(batField);
  if (battery === null) return fail(`malformed battery: '${batField}'`);
  if (battery < 0 || battery > 100) return fail(`battery out of range: ${battery}`);

  let charging;
  if (chgField === "0") charging = false;
  else if (chgField === "1") charging = true;
  else return fail(`malformed charging flag: '${chgField}'`);

  const internetState = decodeInternetState(netField);
  if (internetState === null) return fail(`malformed network state: '${netField}'`);

  const timestampEpochMinute = parseStrictInt(timeField);
  if (timestampEpochMinute === null) return fail(`malformed timestamp: '${timeField}'`);

  return ok({
    version,
    sessionId,
    sequence,
    location,
    batteryPercent: battery,
    charging,
    internetState,
    timestampEpochMinute,
    raw: trimmed,
  });
}

/** @param {{version?: number, command: number, requestId: string, authCode: string}} payload */
export function encodeCommand(payload) {
  const version = payload.version ?? COMMAND_VERSION;
  return [
    `${COMMAND_MARKER_PREFIX}${version}`,
    String(payload.command),
    String(payload.requestId),
    String(payload.authCode),
  ].join(FIELD_SEPARATOR);
}

export function decodeCommand(raw) {
  const fields = String(raw).trim().split(FIELD_SEPARATOR);
  if (fields.length !== COMMAND_FIELD_COUNT) {
    return fail(`expected ${COMMAND_FIELD_COUNT} fields, got ${fields.length}`);
  }
  const [markerField, cmdField, requestId, authCode] = fields;

  if (!markerField.startsWith(COMMAND_MARKER_PREFIX)) return fail(`malformed version marker: '${markerField}'`);
  const version = parseStrictInt(markerField.slice(COMMAND_MARKER_PREFIX.length));
  if (version === null) return fail(`malformed version marker: '${markerField}'`);
  if (!SUPPORTED_COMMAND_VERSIONS.has(version)) return fail(`unsupported protocol version: ${version}`);

  const command = parseStrictInt(cmdField);
  if (command === null || !Object.values(CommandCode).includes(command)) {
    return fail(`unknown command code: '${cmdField}'`);
  }

  if (requestId.trim() === "") return fail("missing request id");
  if (authCode.trim() === "") return fail("missing auth code");

  return ok({ version, command, requestId, authCode });
}
