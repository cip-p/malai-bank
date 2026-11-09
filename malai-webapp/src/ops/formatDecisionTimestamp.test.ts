import { describe, expect, it } from "vitest";
import { formatDecisionTimestamp } from "./formatDecisionTimestamp";

describe("formatDecisionTimestamp", () => {
  it("renders the decision moment as a dense date-time naming its offset", () => {
    expect(formatDecisionTimestamp("2026-07-18T09:30:00")).toMatch(
      /^18 Jul 2026, 09:30 GMT([+-]\d{1,2}(:\d{2})?)?$/,
    );
  });

  it("keeps single-digit days unpadded", () => {
    expect(formatDecisionTimestamp("2026-07-03T17:05:00")).toMatch(
      /^3 Jul 2026, 17:05 GMT([+-]\d{1,2}(:\d{2})?)?$/,
    );
  });
});
