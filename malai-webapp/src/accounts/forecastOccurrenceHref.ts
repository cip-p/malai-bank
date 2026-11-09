import type { ForecastOccurrence } from "@/api/generated/types.gen";

export function forecastOccurrenceHref(occurrence: ForecastOccurrence): string {
  const base =
    occurrence.type === "STANDING_ORDER"
      ? "/move/standing-orders"
      : "/move/direct-debits";
  return `${base}#${occurrence.instrumentId}`;
}
