import { format, parseISO } from "date-fns";

export function formatDecisionTimestamp(ts: string): string {
  return format(parseISO(ts), "d MMM yyyy, HH:mm O");
}
