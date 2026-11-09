import { describe, expect, it } from "vitest";
import { formatRelativeDateGroup } from "./formatRelativeDateGroup";

describe("formatRelativeDateGroup", () => {
  it("returns 'Today' when the date matches now", () => {
    const now = new Date("2026-05-19T14:30:00Z");
    const same = new Date("2026-05-19T08:00:00Z");
    expect(formatRelativeDateGroup(same, now)).toBe("Today");
  });

  it("returns 'Yesterday' when the date is one calendar day before now", () => {
    const now = new Date("2026-05-19T12:00:00Z");
    const yesterday = new Date("2026-05-18T12:00:00Z");
    expect(formatRelativeDateGroup(yesterday, now)).toBe("Yesterday");
  });

  it("returns 'D MMM' for an earlier date in the current year", () => {
    const now = new Date("2026-05-19T12:00:00Z");
    const earlier = new Date("2026-05-14T12:00:00Z");
    expect(formatRelativeDateGroup(earlier, now)).toBe("14 May");
  });

  it("returns 'D MMM YYYY' for a date in a prior year", () => {
    const now = new Date("2026-05-19T12:00:00Z");
    const priorYear = new Date("2024-05-14T12:00:00Z");
    expect(formatRelativeDateGroup(priorYear, now)).toBe("14 May 2024");
  });

  it("treats yesterday across a month boundary correctly", () => {
    const now = new Date("2026-06-01T12:00:00Z");
    const yesterday = new Date("2026-05-31T12:00:00Z");
    expect(formatRelativeDateGroup(yesterday, now)).toBe("Yesterday");
  });

  it("treats a date earlier in the current year that is not yesterday as 'D MMM'", () => {
    const now = new Date("2026-06-01T12:00:00Z");
    const earlier = new Date("2026-05-30T12:00:00Z");
    expect(formatRelativeDateGroup(earlier, now)).toBe("30 May");
  });
});
