import { describe, expect, it } from "vitest";
import { formatCurrency } from "./formatCurrency";

describe("formatCurrency", () => {
  it("formats a positive GBP minor-unit amount", () => {
    expect(formatCurrency(1250, "GBP")).toBe("£12.50");
  });

  it("formats a negative GBP amount with the Unicode minus", () => {
    expect(formatCurrency(-1250, "GBP")).toBe("−£12.50");
  });

  it("formats zero", () => {
    expect(formatCurrency(0, "GBP")).toBe("£0.00");
  });

  it("formats negative zero without a sign", () => {
    expect(formatCurrency(-0, "GBP")).toBe("£0.00");
  });

  it("inserts the thousands separator for large amounts", () => {
    expect(formatCurrency(200000, "GBP")).toBe("£2,000.00");
  });

  it("formats large negative amounts with both separator and Unicode minus", () => {
    expect(formatCurrency(-123456, "GBP")).toBe("−£1,234.56");
  });
});
