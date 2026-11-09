import { describe, expect, it } from "vitest";
import type { ForecastOccurrence } from "@/api/generated/types.gen";
import { forecastOccurrenceHref } from "./forecastOccurrenceHref";

function occurrence(
  overrides: Partial<ForecastOccurrence> = {},
): ForecastOccurrence {
  return {
    date: "2026-07-15",
    counterpartyName: "Energy Co",
    amountMinorUnits: -5000,
    type: "STANDING_ORDER",
    instrumentId: "11111111-1111-1111-1111-111111111111",
    ...overrides,
  };
}

describe("forecastOccurrenceHref", () => {
  it("links a STANDING_ORDER to the standing orders list anchored to the instrument", () => {
    expect(
      forecastOccurrenceHref(
        occurrence({ type: "STANDING_ORDER", instrumentId: "so-42" }),
      ),
    ).toBe("/move/standing-orders#so-42");
  });

  it("links a DIRECT_DEBIT to the direct debits list anchored to the instrument", () => {
    expect(
      forecastOccurrenceHref(
        occurrence({ type: "DIRECT_DEBIT", instrumentId: "dd-99" }),
      ),
    ).toBe("/move/direct-debits#dd-99");
  });
});
