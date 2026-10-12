// Money and dates the way an Indian bank customer expects them: ₹1,23,456.00 and bank-time dates.

const rupees = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", minimumFractionDigits: 2, maximumFractionDigits: 2 });
const rupeesNoPaise = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", minimumFractionDigits: 0, maximumFractionDigits: 0 });

export function money(amount: number, options: { sign?: "auto" | "always" | "never"; paise?: boolean } = {}): string {
  const formatter = options.paise === false ? rupeesNoPaise : rupees;
  const text = formatter.format(Math.abs(amount));
  if (options.sign === "never") return text;
  if (amount < 0) return `-${text}`;
  return options.sign === "always" && amount > 0 ? `+${text}` : text;
}

/** The pieces of a large figure, so the paise can be set smaller than the rupees. */
export function moneyParts(amount: number): { sign: string; whole: string; fraction: string } {
  const text = rupees.format(Math.abs(amount));
  const dot = text.lastIndexOf(".");
  return { sign: amount < 0 ? "-" : "", whole: text.slice(0, dot), fraction: text.slice(dot + 1) };
}

/** Axis ticks and tight spaces: ₹850, ₹12K, ₹1.2L, ₹3.4Cr. */
export function compactMoney(amount: number): string {
  const abs = Math.abs(amount);
  const sign = amount < 0 ? "-" : "";
  const trim = (value: number) => (Number.isInteger(value) ? value.toString() : value.toFixed(1).replace(/\.0$/, ""));
  if (abs >= 1e7) return `${sign}₹${trim(abs / 1e7)}Cr`;
  if (abs >= 1e5) return `${sign}₹${trim(abs / 1e5)}L`;
  if (abs >= 1e3) return `${sign}₹${trim(abs / 1e3)}K`;
  return `${sign}₹${Math.round(abs)}`;
}

/** "5177 0081 5254": account numbers are read in groups of four. */
export function groupDigits(accountNumber: string): string {
  return accountNumber.replace(/(\d{4})(?=\d)/g, "$1 ");
}

export function last4(accountNumber: string): string {
  return accountNumber.slice(-4);
}

/** "MS" for "Meera (sis)", "KR" for "Kabir · Rent": punctuation-only words and symbols are skipped. */
export function initials(name: string): string {
  const words = name.split(/\s+/).map((word) => word.replace(/[^\p{L}\p{N}]/gu, "")).filter(Boolean);
  const first = words[0]?.[0] ?? "";
  const last = words.length > 1 ? words[words.length - 1][0] : "";
  return (first + last).toUpperCase();
}

const dayFormat = new Intl.DateTimeFormat("en-IN", { weekday: "short", day: "numeric", month: "short" });
const dayYearFormat = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", year: "numeric" });
const timeFormat = new Intl.DateTimeFormat("en-IN", { hour: "numeric", minute: "2-digit" });
const fullFormat = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "long", year: "numeric", hour: "numeric", minute: "2-digit" });
const monthFormat = new Intl.DateTimeFormat("en-IN", { month: "long" });

function startOfDay(date: Date): number {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
}

/** "Today", "Yesterday", "Mon, 5 Oct" or "5 Oct 2025" once the year changes. */
export function dayLabel(iso: string): string {
  const date = new Date(iso);
  const today = startOfDay(new Date());
  const day = startOfDay(date);
  const diff = Math.round((today - day) / 86_400_000);
  if (diff === 0) return "Today";
  if (diff === 1) return "Yesterday";
  return date.getFullYear() === new Date().getFullYear() ? dayFormat.format(date) : dayYearFormat.format(date);
}

export function timeLabel(iso: string): string {
  return timeFormat.format(new Date(iso));
}

export function dateTimeLabel(iso: string): string {
  return fullFormat.format(new Date(iso));
}

export function dateLabel(iso: string): string {
  return dayYearFormat.format(new Date(iso));
}

export function monthLabel(date = new Date()): string {
  return monthFormat.format(date);
}

/** Dates for the API's date-only filters, in the browser's calendar: "2026-10-12". */
export function isoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${date.getFullYear()}-${month}-${day}`;
}

export function daysAgo(days: number): Date {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return date;
}

export function relativeTime(iso: string): string {
  const seconds = Math.round((Date.now() - new Date(iso).getTime()) / 1000);
  const units: [number, Intl.RelativeTimeFormatUnit][] = [[60, "second"], [60, "minute"], [24, "hour"], [7, "day"], [4.345, "week"], [12, "month"]];
  let value = seconds;
  let unit: Intl.RelativeTimeFormatUnit = "second";
  for (const [size, next] of units) {
    if (Math.abs(value) < size) break;
    value /= size;
    unit = next === "second" ? "minute" : next;
  }
  return new Intl.RelativeTimeFormat("en", { numeric: "auto" }).format(-Math.round(value), unit);
}

/** "Chrome on macOS" from a user-agent string, for the signed-in devices list. */
export function deviceLabel(userAgent: string | null): string {
  if (!userAgent) return "Unknown device";
  const ua = userAgent;
  const browser = /Edg\//.test(ua) ? "Edge" : /OPR\//.test(ua) ? "Opera" : /Firefox\//.test(ua) ? "Firefox"
    : /Chrome\//.test(ua) ? "Chrome" : /Safari\//.test(ua) ? "Safari" : /curl|Postman|newman/i.test(ua) ? "API client" : "Browser";
  const os = /iPhone|iPad/.test(ua) ? "iOS" : /Android/.test(ua) ? "Android" : /Macintosh/.test(ua) ? "macOS"
    : /Windows/.test(ua) ? "Windows" : /Linux/.test(ua) ? "Linux" : null;
  return os ? `${browser} on ${os}` : browser;
}

export function newIdempotencyKey(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) return `web-${crypto.randomUUID()}`;
  return `web-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
}
