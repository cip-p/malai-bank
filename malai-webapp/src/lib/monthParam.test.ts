import { describe, expect, it } from "vitest";
import {
  currentMonthParam,
  formatMonthLabel,
  isFutureMonth,
  shiftMonthParam,
  toMonthParam,
} from "./monthParam";

describe("monthParam", () => {
  it("formats a date as yyyy-MM", () => {
    expect(toMonthParam(new Date(2026, 6, 8))).toBe("2026-07");
  });

  it("derives the current month from a supplied now", () => {
    expect(currentMonthParam(new Date(2026, 0, 31))).toBe("2026-01");
  });

  it("shifts months backwards across a year boundary", () => {
    expect(shiftMonthParam("2026-01", -1)).toBe("2025-12");
  });

  it("shifts months forwards across a year boundary", () => {
    expect(shiftMonthParam("2026-12", 1)).toBe("2027-01");
  });

  it("renders a human month label", () => {
    expect(formatMonthLabel("2026-07")).toBe("July 2026");
  });

  it("recognises the current and future months", () => {
    const now = new Date(2026, 6, 8);
    expect(isFutureMonth("2026-07", now)).toBe(true);
    expect(isFutureMonth("2026-08", now)).toBe(true);
    expect(isFutureMonth("2026-06", now)).toBe(false);
  });
});
