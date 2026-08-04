/**
 * Cheque age = days between the bill date and the cheque date — how long the customer
 * is taking to actually part with the money. Shared by the payments list, bill detail
 * and the enter-payment form so all three read the same way.
 */

export type ChequeAgeBand = 'ok' | 'warn' | 'late';

/** Bands: 0–45 days ok, 45–60 warn, over 60 late. */
export function chequeAgeBand(days: number): ChequeAgeBand {
  if (days > 60) return 'late';
  if (days > 45) return 'warn';
  return 'ok';
}

/** Whole days from billDate to chequeDate. Null when either date is missing. */
export function chequeAgeDays(
  billDate: string | null | undefined,
  chequeDate: string | null | undefined,
): number | null {
  if (!billDate || !chequeDate) return null;
  const from = parseLocal(billDate);
  const to = parseLocal(chequeDate);
  if (from === null || to === null) return null;
  return Math.round((to - from) / 86400000);
}

/** Short label for a chip, e.g. "52d" — or "-3d" for a cheque dated before the bill. */
export function chequeAgeLabel(days: number): string {
  return `${days}d`;
}

export function chequeAgeTooltip(days: number): string {
  if (days < 0) return `Cheque dated ${Math.abs(days)} days before the bill`;
  const band = chequeAgeBand(days);
  const suffix = band === 'late' ? ' — over 60 days'
    : band === 'warn' ? ' — over 45 days'
    : '';
  return `${days} days from bill date to cheque date${suffix}`;
}

/**
 * Parses a yyyy-MM-dd (or full ISO) string at local midnight. Using `new Date(s)` on a
 * bare date string parses as UTC, which shifts the day in timezones ahead of UTC.
 */
function parseLocal(value: string): number | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value);
  if (!match) return null;
  const [, year, month, day] = match;
  return new Date(Number(year), Number(month) - 1, Number(day)).getTime();
}
