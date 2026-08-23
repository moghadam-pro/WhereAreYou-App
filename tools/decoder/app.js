import { decodeStatus, InternetState } from "./protocol.js";
import { groupBySession, dedupeByRawPayload, pickBestEstimate, flagOutliers, looksLikeMovement } from "./ranking.js";
import { mapUrl, geoUri } from "./mapping.js";
import { epochMinuteToDate, formatLocalTime, formatAge } from "./format.js";

const input = document.getElementById("payload-input");
const decodeButton = document.getElementById("decode-button");
const clearButton = document.getElementById("clear-button");
const status = document.getElementById("input-status");
const results = document.getElementById("results");
const sessionTemplate = document.getElementById("session-card-template");
const errorTemplate = document.getElementById("parse-error-template");

function internetLabel(state) {
  switch (state) {
    case InternetState.VALIDATED: return "Internet: available";
    case InternetState.UNAVAILABLE: return "Internet: unavailable";
    default: return "Internet: unknown";
  }
}

function renderSample(payload, { isBest, outlierNote }) {
  const li = document.createElement("li");
  li.className = "sample" + (isBest ? " is-best" : "");

  const date = epochMinuteToDate(payload.timestampEpochMinute);
  const ageMs = Date.now() - date.getTime();

  const row1 = document.createElement("div");
  row1.className = "row";
  row1.innerHTML = `
    <span>#${payload.sequence}</span>
    <span>${formatLocalTime(date)}</span>
    <span>${formatAge(ageMs)}</span>
  `;
  li.appendChild(row1);

  const row2 = document.createElement("div");
  row2.className = "row";
  if (payload.location) {
    row2.innerHTML = `
      <span>±${payload.location.accuracyMeters} m</span>
      <span>${payload.batteryPercent}% battery${payload.charging ? " (charging)" : ""}</span>
      <span>${internetLabel(payload.internetState)}</span>
    `;
  } else {
    row2.innerHTML = `
      <span>Location unavailable</span>
      <span>${payload.batteryPercent}% battery${payload.charging ? " (charging)" : ""}</span>
      <span>${internetLabel(payload.internetState)}</span>
    `;
  }
  li.appendChild(row2);

  if (payload.location) {
    const row3 = document.createElement("div");
    row3.className = "row";
    const mapLink = document.createElement("a");
    mapLink.href = mapUrl(payload.location);
    mapLink.target = "_blank";
    mapLink.rel = "noopener noreferrer";
    mapLink.textContent = "Open map";
    row3.appendChild(mapLink);

    const geoLink = document.createElement("a");
    geoLink.href = geoUri(payload.location);
    geoLink.textContent = "geo: link";
    row3.appendChild(geoLink);
    li.appendChild(row3);
  }

  if (outlierNote) {
    const note = document.createElement("div");
    note.className = "outlier-note";
    note.textContent = outlierNote;
    li.appendChild(note);
  }

  const raw = document.createElement("div");
  raw.className = "raw";
  raw.dir = "ltr";
  raw.textContent = payload.raw;
  li.appendChild(raw);

  return li;
}

function renderSession(sessionId, samples) {
  const fragment = sessionTemplate.content.cloneNode(true);
  const card = fragment.querySelector(".session-card");
  card.querySelector("h3").textContent = `Session ${sessionId}`;

  const nowEpochMinute = Math.floor(Date.now() / 60000);
  const best = pickBestEstimate(samples, nowEpochMinute);
  const outlierFlags = flagOutliers(samples);

  if (looksLikeMovement(samples)) {
    card.querySelector(".movement-badge").hidden = false;
  }

  const bestBox = card.querySelector(".best-estimate");
  if (best && best.location) {
    const ageMs = Date.now() - epochMinuteToDate(best.timestampEpochMinute).getTime();
    bestBox.innerHTML = `
      <div class="label">Best current estimate</div>
      <div>#${best.sequence} · ±${best.location.accuracyMeters} m · ${formatAge(ageMs)}</div>
    `;
  } else {
    bestBox.innerHTML = `<div class="label">Best current estimate</div><div>No sample in this session has a location.</div>`;
  }

  const list = card.querySelector(".sample-list");
  for (const sample of samples) {
    list.appendChild(
      renderSample(sample, {
        isBest: sample === best,
        outlierNote: outlierFlags.get(sample),
      }),
    );
  }

  return fragment;
}

function renderErrors(errors) {
  if (errors.length === 0) return null;
  const container = document.createElement("div");
  container.className = "session-card";
  const heading = document.createElement("h3");
  heading.textContent = `${errors.length} line(s) could not be decoded`;
  container.appendChild(heading);
  const list = document.createElement("ul");
  list.className = "sample-list";
  for (const err of errors) {
    const fragment = errorTemplate.content.cloneNode(true);
    const li = fragment.querySelector(".parse-error");
    li.dir = "ltr";
    li.textContent = `${err.reason} — "${err.line}"`;
    list.appendChild(li);
  }
  container.appendChild(list);
  return container;
}

function decodeAll() {
  results.innerHTML = "";
  const rawText = input.value;
  const lines = rawText.split("\n").map((l) => l.trim()).filter((l) => l.length > 0);

  if (lines.length === 0) {
    status.textContent = "Paste at least one message.";
    return;
  }

  const decoded = [];
  const errors = [];
  for (const line of lines) {
    const result = decodeStatus(line);
    if (result.ok) {
      decoded.push(result.payload);
    } else {
      errors.push({ line, reason: result.reason });
    }
  }

  const deduped = dedupeByRawPayload(decoded);
  const groups = groupBySession(deduped);

  status.textContent = `${deduped.length} sample(s) decoded across ${groups.size} session(s)` +
    (errors.length > 0 ? `, ${errors.length} line(s) failed` : "") + ".";

  for (const [sessionId, samples] of groups) {
    results.appendChild(renderSession(sessionId, samples));
  }

  const errorCard = renderErrors(errors);
  if (errorCard) results.appendChild(errorCard);
}

decodeButton.addEventListener("click", decodeAll);

clearButton.addEventListener("click", () => {
  input.value = "";
  results.innerHTML = "";
  status.textContent = "";
  input.focus();
});

/** Prefill from a URL fragment/query for local tooling/Shortcuts integration (AGENTS.md "Decoder/PWA"). */
function prefillFromUrl() {
  const hashPayload = decodeURIComponent(window.location.hash.replace(/^#/, ""));
  const params = new URLSearchParams(window.location.search);
  const queryPayload = params.get("payload") ?? params.get("p");

  const candidate = hashPayload.trim().length > 0 ? hashPayload : queryPayload;
  if (candidate && candidate.trim().length > 0) {
    input.value = candidate;
    decodeAll();
  }
}

prefillFromUrl();

// --- PWA install + offline support -----------------------------------------------------

if ("serviceWorker" in navigator) {
  window.addEventListener("load", () => {
    navigator.serviceWorker.register("service-worker.js").catch(() => {
      // Offline install is a nice-to-have; the decoder still works fully without it.
    });
  });
}

const installPanel = document.getElementById("install-panel");
const installButton = document.getElementById("install-button");
let deferredInstallPrompt = null;

window.addEventListener("beforeinstallprompt", (event) => {
  event.preventDefault();
  deferredInstallPrompt = event;
  installPanel.hidden = false;
});

installButton?.addEventListener("click", async () => {
  if (!deferredInstallPrompt) return;
  deferredInstallPrompt.prompt();
  await deferredInstallPrompt.userChoice;
  deferredInstallPrompt = null;
  installPanel.hidden = true;
});

window.addEventListener("appinstalled", () => {
  installPanel.hidden = true;
});
