const LONDON_DATE_TIME = new Intl.DateTimeFormat("en-GB", {
  timeZone: "Europe/London",
  day: "numeric",
  month: "short",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
  hour12: false,
});

const LONDON_DATE = new Intl.DateTimeFormat("en-GB", {
  timeZone: "Europe/London",
  day: "numeric",
  month: "short",
  year: "numeric",
});

export function formatAsOf(iso: string): string {
  return LONDON_DATE_TIME.format(new Date(iso));
}

export function formatWindowStart(isoDate: string): string {
  return LONDON_DATE.format(new Date(`${isoDate}T12:00:00Z`));
}
