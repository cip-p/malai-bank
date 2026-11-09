import { addMonths, format, parse } from "date-fns";

const MONTH_PARAM_FORMAT = "yyyy-MM";

export function toMonthParam(date: Date): string {
  return format(date, MONTH_PARAM_FORMAT);
}

export function currentMonthParam(now: Date = new Date()): string {
  return toMonthParam(now);
}

function parseMonthParam(month: string): Date {
  return parse(month, MONTH_PARAM_FORMAT, new Date(2000, 0, 1));
}

export function shiftMonthParam(month: string, delta: number): string {
  return toMonthParam(addMonths(parseMonthParam(month), delta));
}

export function formatMonthLabel(month: string): string {
  return format(parseMonthParam(month), "MMMM yyyy");
}

export function isFutureMonth(month: string, now: Date = new Date()): boolean {
  return month >= currentMonthParam(now);
}
