/**
 * Returns a YYYY-MM-DD string using LOCAL date parts.
 * `toISOString()` converts to UTC first, which shifts the date backward in
 * timezones ahead of UTC (e.g. UTC+5:30) — so never use it for date strings.
 */
export function localDateStr(d: Date = new Date()): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
