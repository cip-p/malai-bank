import { describe, expect, it } from "vitest";
import {
  formatDeclineReason,
  formatSchemeDeclineCode,
} from "./formatDeclineReason";

describe("formatDeclineReason", () => {
  it.each([
    ["CARD_FROZEN", "Card frozen"],
    ["CARD_CANCELLED", "Card cancelled"],
    ["INSUFFICIENT_FUNDS", "Insufficient funds"],
    ["OVER_LIMIT", "Over your credit limit"],
  ])("maps %s to %s", (code, label) => {
    expect(formatDeclineReason(code)).toBe(label);
  });

  it("falls back to Declined for unknown codes", () => {
    expect(formatDeclineReason("SOMETHING_NEW")).toBe("Declined");
  });

  it("falls back to Declined when the reason is missing", () => {
    expect(formatDeclineReason(null)).toBe("Declined");
    expect(formatDeclineReason(undefined)).toBe("Declined");
  });
});

describe("formatSchemeDeclineCode", () => {
  it.each([
    ["62", "Card frozen"],
    ["04", "Card cancelled"],
    ["51", "Insufficient funds"],
    ["61", "Over your credit limit"],
  ])("crosswalks scheme code %s to %s", (code, label) => {
    expect(formatSchemeDeclineCode(code)).toBe(label);
  });

  it("accepts canonical reason names directly", () => {
    expect(formatSchemeDeclineCode("CARD_FROZEN")).toBe("Card frozen");
  });

  it("falls back to Declined for unknown or missing codes", () => {
    expect(formatSchemeDeclineCode("99")).toBe("Declined");
    expect(formatSchemeDeclineCode(null)).toBe("Declined");
  });
});
