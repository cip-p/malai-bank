import { describe, expect, it } from "vitest";
import { formatDecisionAge } from "./formatDecisionAge";

const NOW = new Date("2026-07-24T12:00:00");

describe("formatDecisionAge", () => {
  it("calls anything under a minute just now", () => {
    expect(formatDecisionAge("2026-07-24T11:59:30", NOW)).toBe("just now");
  });

  it("counts minutes under an hour", () => {
    expect(formatDecisionAge("2026-07-24T11:12:00", NOW)).toBe("48m ago");
  });

  it("counts hours under a day", () => {
    expect(formatDecisionAge("2026-07-24T04:00:00", NOW)).toBe("8h ago");
  });

  it("counts days beyond that", () => {
    expect(formatDecisionAge("2026-07-21T12:00:00", NOW)).toBe("3d ago");
  });

  it("treats a future moment as just now rather than a negative age", () => {
    expect(formatDecisionAge("2026-07-24T12:00:30", NOW)).toBe("just now");
  });
});
