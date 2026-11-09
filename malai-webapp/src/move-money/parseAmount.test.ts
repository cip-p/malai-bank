import { describe, expect, it } from "vitest";
import { parseAmount } from "./parseAmount";

describe("parseAmount", () => {
  it("rejects an empty string with reason 'empty'", () => {
    expect(parseAmount("")).toEqual({ ok: false, reason: "empty" });
  });

  it("rejects a whitespace-only string with reason 'empty'", () => {
    expect(parseAmount("   ")).toEqual({ ok: false, reason: "empty" });
  });

  it("rejects '0' with reason 'below-minimum'", () => {
    expect(parseAmount("0")).toEqual({ ok: false, reason: "below-minimum" });
  });

  it("rejects '0.00' with reason 'below-minimum'", () => {
    expect(parseAmount("0.00")).toEqual({ ok: false, reason: "below-minimum" });
  });

  it("rejects negatives with reason 'invalid'", () => {
    expect(parseAmount("-1")).toEqual({ ok: false, reason: "invalid" });
  });

  it("rejects scientific notation with reason 'invalid'", () => {
    expect(parseAmount("1e2")).toEqual({ ok: false, reason: "invalid" });
  });

  it("rejects comma-as-decimal with reason 'invalid'", () => {
    expect(parseAmount("12,45")).toEqual({ ok: false, reason: "invalid" });
  });

  it("rejects more than two decimals with reason 'too-many-decimals'", () => {
    expect(parseAmount("12.456")).toEqual({
      ok: false,
      reason: "too-many-decimals",
    });
  });

  it("accepts an integer pound value", () => {
    expect(parseAmount("12")).toEqual({ ok: true, minorUnits: 1200 });
  });

  it("accepts a single-decimal value", () => {
    expect(parseAmount("12.4")).toEqual({ ok: true, minorUnits: 1240 });
  });

  it("accepts a two-decimal value", () => {
    expect(parseAmount("12.45")).toEqual({ ok: true, minorUnits: 1245 });
  });

  it("accepts surrounding whitespace", () => {
    expect(parseAmount(" 12.45 ")).toEqual({ ok: true, minorUnits: 1245 });
  });

  it("rejects a leading £ symbol", () => {
    expect(parseAmount("£12.45")).toEqual({ ok: false, reason: "invalid" });
  });

  it("accepts the soft-cap boundary exactly", () => {
    expect(parseAmount("999999.99")).toEqual({
      ok: true,
      minorUnits: 99_999_999,
    });
  });

  it("rejects above the soft cap with reason 'above-max'", () => {
    expect(parseAmount("1000000")).toEqual({ ok: false, reason: "above-max" });
  });
});
