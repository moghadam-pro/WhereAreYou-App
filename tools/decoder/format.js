// Human-readable time/age formatting. Pure functions, no I/O.

/** @param {number} epochMinute */
export function epochMinuteToDate(epochMinute) {
  return new Date(epochMinute * 60 * 1000);
}

export function formatLocalTime(date) {
  return date.toLocaleString(undefined, {
    year: "numeric",
    month: "short",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

/** @param {number} ms non-negative milliseconds */
export function formatAge(ms) {
  const totalSeconds = Math.max(0, Math.round(ms / 1000));
  const days = Math.floor(totalSeconds / 86400);
  const hours = Math.floor((totalSeconds % 86400) / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;

  if (days > 0) return `${days}d ${hours}h ago`;
  if (hours > 0) return `${hours}h ${minutes}m ago`;
  if (minutes > 0) return `${minutes}m ${seconds}s ago`;
  return `${seconds}s ago`;
}
