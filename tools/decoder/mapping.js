// Builds a standard map link entirely on-device — the SMS payload never carries a URL
// (SMS_PROTOCOL.md section 1 "No map URL is transmitted"). Nothing here makes a network
// request; it only formats a string.

/** @param {{latitude: number, longitude: number}} location */
export function mapUrl(location) {
  const lat = location.latitude.toFixed(5);
  const lon = location.longitude.toFixed(5);
  return `https://www.openstreetmap.org/?mlat=${lat}&mlon=${lon}#map=16/${lat}/${lon}`;
}

/** A `geo:` URI, useful for handing off to any installed native map app. */
export function geoUri(location) {
  const lat = location.latitude.toFixed(5);
  const lon = location.longitude.toFixed(5);
  return `geo:${lat},${lon}?q=${lat},${lon}`;
}
