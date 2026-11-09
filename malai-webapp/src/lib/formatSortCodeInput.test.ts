import { describe, expect, it } from "vitest";
import { formatSortCodeInput } from "./formatSortCodeInput";

describe("formatSortCodeInput", () => {
  it("returns empty string for empty input", () => {
    expect(formatSortCodeInput("")).toBe("");
  });

  it("returns '1' for '1'", () => {
    expect(formatSortCodeInput("1")).toBe("1");
  });

  it("returns '12' for '12'", () => {
    expect(formatSortCodeInput("12")).toBe("12");
  });

  it("returns '12-3' for '123'", () => {
    expect(formatSortCodeInput("123")).toBe("12-3");
  });

  it("returns '12-34' for '1234'", () => {
    expect(formatSortCodeInput("1234")).toBe("12-34");
  });

  it("returns '12-34-5' for '12345'", () => {
    expect(formatSortCodeInput("12345")).toBe("12-34-5");
  });

  it("returns '12-34-56' for '123456'", () => {
    expect(formatSortCodeInput("123456")).toBe("12-34-56");
  });

  it("idempotently formats pre-dashed input", () => {
    expect(formatSortCodeInput("12-34-56")).toBe("12-34-56");
  });

  it("handles paste with spaces between groups", () => {
    expect(formatSortCodeInput("12 34 56")).toBe("12-34-56");
  });

  it("re-inserts the dash after a backspace across the boundary", () => {
    expect(formatSortCodeInput("1234-56")).toBe("12-34-56");
  });

  it("clips input beyond 6 digits", () => {
    expect(formatSortCodeInput("12345678")).toBe("12-34-56");
  });

  it("strips any non-digit, non-dash, non-space characters", () => {
    expect(formatSortCodeInput("12abc34_56")).toBe("12-34-56");
  });
});
