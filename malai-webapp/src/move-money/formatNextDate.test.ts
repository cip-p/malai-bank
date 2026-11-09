import { describe, expect, it } from "vitest";
import { formatNextDate } from "./formatNextDate";

describe("formatNextDate", () => {
  it("returns 'Next: not scheduled' for null", () => {
    const now = new Date("2026-05-20T12:00:00Z");
    expect(formatNextDate(null, now)).toBe("Next: not scheduled");
  });

  it("returns 'Next: in 0 days' for today", () => {
    const now = new Date("2026-05-20T09:00:00Z");
    const today = "2026-05-20T15:00:00Z";
    expect(formatNextDate(today, now)).toBe("Next: in 0 days");
  });

  it("returns 'Next: in 1 day' (singular) for one day from now", () => {
    const now = new Date("2026-05-20T12:00:00Z");
    const tomorrow = "2026-05-21T12:00:00Z";
    expect(formatNextDate(tomorrow, now)).toBe("Next: in 1 day");
  });

  it("returns 'Next: in 6 days' for six days from now", () => {
    const now = new Date("2026-05-20T12:00:00Z");
    const sixDays = "2026-05-26T12:00:00Z";
    expect(formatNextDate(sixDays, now)).toBe("Next: in 6 days");
  });

  it("falls back to absolute current-year format on the 7-day boundary", () => {
    const now = new Date("2026-05-20T12:00:00Z");
    const sevenDays = "2026-05-27T12:00:00Z";
    expect(formatNextDate(sevenDays, now)).toBe("Next: 27 May");
  });

  it("returns absolute current-year format for a 30-day future date", () => {
    const now = new Date("2026-05-20T12:00:00Z");
    const thirty = "2026-06-19T12:00:00Z";
    expect(formatNextDate(thirty, now)).toBe("Next: 19 Jun");
  });

  it("includes the year when the date is in a prior calendar year", () => {
    const now = new Date("2026-05-20T12:00:00Z");
    const prior = "2025-05-20T12:00:00Z";
    expect(formatNextDate(prior, now)).toBe("Next: 20 May 2025");
  });

  it("includes the year when the date is in a future calendar year", () => {
    const now = new Date("2026-05-20T12:00:00Z");
    const future = "2027-05-20T12:00:00Z";
    expect(formatNextDate(future, now)).toBe("Next: 20 May 2027");
  });
});
