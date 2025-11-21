import { format, formatDistanceToNowStrict, parseISO } from 'date-fns';

/**
 * Display helpers.
 *
 * Currency and date formatting are centralised so a table cell, a KPI card and a chart
 * tooltip never disagree about how $1.2M is written.
 */

const currencyFormatter = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  maximumFractionDigits: 0,
});

const compactCurrencyFormatter = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  notation: 'compact',
  maximumFractionDigits: 1,
});

export function formatCurrency(value: number | null | undefined): string {
  return value == null ? '—' : currencyFormatter.format(value);
}

/** Compact form for axis labels and dense cards: $1.2M rather than $1,200,000. */
export function formatCompactCurrency(value: number | null | undefined): string {
  return value == null ? '—' : compactCurrencyFormatter.format(value);
}

export function formatNumber(value: number | null | undefined): string {
  return value == null ? '—' : new Intl.NumberFormat('en-US').format(value);
}

export function formatDate(iso: string | null | undefined): string {
  return iso ? format(parseISO(iso), 'd MMM yyyy') : '—';
}

export function formatDateTime(iso: string | null | undefined): string {
  return iso ? format(parseISO(iso), 'd MMM yyyy, HH:mm') : '—';
}

export function formatRelative(iso: string | null | undefined): string {
  return iso ? `${formatDistanceToNowStrict(parseISO(iso))} ago` : '—';
}

/** Turns SCREAMING_SNAKE enum values into readable labels. */
export function humanise(value: string | null | undefined): string {
  if (!value) return '—';
  return value
    .toLowerCase()
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');
}

export function initialsOf(first: string, last: string): string {
  return `${first.charAt(0)}${last.charAt(0)}`.toUpperCase();
}
