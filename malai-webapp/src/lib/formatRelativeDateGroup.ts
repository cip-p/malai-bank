import { format, isSameDay, subDays } from "date-fns";

export function formatRelativeDateGroup(date: Date, now: Date): string {
  if (isSameDay(date, now)) return "Today";
  if (isSameDay(date, subDays(now, 1))) return "Yesterday";
  if (date.getFullYear() === now.getFullYear()) return format(date, "d MMM");
  return format(date, "d MMM yyyy");
}
