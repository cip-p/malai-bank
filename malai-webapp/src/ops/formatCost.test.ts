import { describe, expect, it } from "vitest";
import { formatCostMicros } from "./formatCost";

describe("formatCostMicros", () => {
  it("renders sub-cent costs from micros without rounding them away", () => {
    expect(formatCostMicros(1250, "USD")).toBe("US$0.00125");
  });

  it("renders whole-major-unit costs with the usual two decimals", () => {
    expect(formatCostMicros(12_500_000, "GBP")).toBe("£12.50");
  });

  it("renders a zero cost", () => {
    expect(formatCostMicros(0, "USD")).toBe("US$0.00");
  });

  it("falls back to amount-and-code when Intl rejects the currency", () => {
    expect(formatCostMicros(1250, "CREDITS")).toBe("0.00125 CREDITS");
  });
});
