import { describe, expect, it } from "vitest";
import { formatFrequency } from "./formatFrequency";

describe("formatFrequency", () => {
  it("maps WEEKLY to 'Weekly'", () => {
    expect(formatFrequency("WEEKLY")).toBe("Weekly");
  });

  it("maps MONTHLY to 'Monthly'", () => {
    expect(formatFrequency("MONTHLY")).toBe("Monthly");
  });

  it("maps QUARTERLY to 'Quarterly'", () => {
    expect(formatFrequency("QUARTERLY")).toBe("Quarterly");
  });

  it("maps ANNUAL to 'Annually'", () => {
    expect(formatFrequency("ANNUAL")).toBe("Annually");
  });
});
