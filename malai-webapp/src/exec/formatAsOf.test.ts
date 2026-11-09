import { describe, expect, it } from "vitest";
import { formatAsOf, formatWindowStart } from "./formatAsOf";

describe("formatAsOf", () => {
  it("renders the freshness instant in the bank's London day, not the viewer's zone", () => {
    expect(formatAsOf("2026-07-21T13:30:00Z")).toBe("21 Jul 2026, 14:30");
  });

  it("honours the GMT/BST boundary rather than a fixed offset", () => {
    expect(formatAsOf("2026-01-15T09:05:00Z")).toBe("15 Jan 2026, 09:05");
  });
});

describe("formatWindowStart", () => {
  it("renders a window's first Banking day as a bare London date", () => {
    expect(formatWindowStart("2026-07-15")).toBe("15 Jul 2026");
  });

  it("stays on the named day either side of the clock change", () => {
    expect(formatWindowStart("2026-03-12")).toBe("12 Mar 2026");
    expect(formatWindowStart("2026-04-10")).toBe("10 Apr 2026");
  });
});
