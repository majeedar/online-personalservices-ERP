/** 480 -> "8:00 h", -95 -> "-1:35 h" */
export function formatMinutes(minutes: number): string {
  const sign = minutes < 0 ? '-' : '';
  const abs = Math.abs(minutes);
  const h = Math.floor(abs / 60);
  const m = abs % 60;
  return `${sign}${h}:${m.toString().padStart(2, '0')} h`;
}

/** Signed balance: 30 -> "+0:30 h" */
export function formatBalance(minutes: number): string {
  return (minutes > 0 ? '+' : '') + formatMinutes(minutes);
}

/** "STUDENT_ASSISTANT" -> "Student assistant" */
export function humanize(code: string | null | undefined): string {
  if (!code) {
    return '';
  }
  const text = code.replace(/_/g, ' ').toLowerCase();
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/** Local calendar date as ISO "YYYY-MM-DD" (no timezone shift, unlike toISOString). */
export function isoDate(date: Date): string {
  const y = date.getFullYear();
  const m = (date.getMonth() + 1).toString().padStart(2, '0');
  const d = date.getDate().toString().padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/** Parses "YYYY-MM-DD" as a local date. */
export function parseIsoDate(value: string): Date {
  const [y, m, d] = value.split('-').map(Number);
  return new Date(y, m - 1, d);
}

export function formatDays(days: number | null | undefined): string {
  if (days === null || days === undefined) {
    return '—';
  }
  const rounded = Math.round(days * 10) / 10;
  return `${rounded} ${Math.abs(rounded) === 1 ? 'day' : 'days'}`;
}
