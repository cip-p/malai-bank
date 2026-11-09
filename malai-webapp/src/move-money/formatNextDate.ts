import { differenceInCalendarDays, format } from "date-fns";

export function formatNextDate(iso: string | null, now: Date): string {
  if (iso === null) return "Next: not scheduled";
  const date = new Date(iso);
  const diff = differenceInCalendarDays(date, now);
  if (diff >= 0 && diff < 7) {
    const noun = diff === 1 ? "day" : "days";
    return `Next: in ${diff} ${noun}`;
  }
  if (date.getFullYear() === now.getFullYear()) {
    return `Next: ${format(date, "d MMM")}`;
  }
  return `Next: ${format(date, "d MMM yyyy")}`;
}
