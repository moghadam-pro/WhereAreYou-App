# WhereAreYou Decoder

A tiny static, offline-capable web app that decodes the compact SMS status payloads
described in [`docs/SMS_PROTOCOL.md`](../../docs/SMS_PROTOCOL.md). It exists so the person
checking on a protected phone does not need Android — this runs in any modern browser,
including iPhone Safari (PRODUCT_SPEC.md section 3 "Check/Decoder mode").

No backend, no build step, no dependencies, no analytics. Every file here is either HTML,
CSS, or plain ES modules that run as-is.

## Use it

Open `index.html` directly, or serve the directory with any static file server, e.g.:

```sh
python3 -m http.server 8080
# then open http://localhost:8080/
```

Paste one or more status messages (one per line) and press **Decode**. Everything happens
in-page; open your browser's network tab if you want to confirm nothing is sent anywhere.

Once loaded over http(s) once, the service worker caches the app for offline use, and the
browser will offer to install it as a home-screen app.

### URL prefill (for Shortcuts/tooling)

For local automation (e.g. an iOS Shortcut that shares SMS text into a URL), the page also
accepts a payload via the URL fragment or query string:

```
index.html#و1|3174|1|3629714|5912345|18|9|0|1|29784562
index.html?payload=و1|3174|1|3629714|5912345|18|9|0|1|29784562
```

## Files

| File | Purpose |
|---|---|
| `protocol.js` | Status/command wire-format encoder+decoder. Mirrors `core/src/main/kotlin/.../protocol/StatusProtocol.kt` and `CommandProtocol.kt` field-for-field. |
| `ranking.js` | Groups samples by session, picks a best current estimate, flags implausible-movement outliers. Never averages coordinates. |
| `mapping.js` | Builds an OpenStreetMap link and a `geo:` URI locally — no network request, no map link is ever in the SMS itself. |
| `format.js` | Timestamp/age formatting helpers. |
| `app.js` | UI wiring: parses pasted text, renders session cards, PWA install prompt, service-worker registration. |
| `index.html`, `styles.css` | The page itself. |
| `manifest.webmanifest`, `service-worker.js`, `icons/` | PWA installability + offline cache. |

## Tests

```sh
npm test
# or directly:
node --test test/*.test.js
```

`test/protocol.test.js` and `test/ranking.test.js` share their test vectors with the
Kotlin suites in `core/src/test/kotlin/.../protocol/` and `.../rules/` — AGENTS.md requires
the PWA decoder and the Android encoder to agree on the same documented vectors.

## Known gaps

- No PNG app icon yet (`icons/icon.svg` only) — `apple-touch-icon` is intentionally omitted
  until a raster icon set is added; iOS falls back to a page screenshot for "Add to Home
  Screen" until then.
- The compact machine command form (`ک1|CMD|RID|AUTH`, `protocol.js` `encodeCommand`) is
  wired for a future authenticated command generator (SMS_PROTOCOL.md section 9) but has no
  UI yet — the decoder currently only *decodes* status responses.
