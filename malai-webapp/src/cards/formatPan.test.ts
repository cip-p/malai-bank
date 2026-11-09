import { describe, expect, it } from "vitest";
import { formatPanGroups, lastFourOf } from "./formatPan";

describe("formatPanGroups", () => {
  it("splits a full 16-digit PAN into four groups", () => {
    expect(formatPanGroups("9904320000012341")).toEqual([
      "9904",
      "3200",
      "0001",
      "2341",
    ]);
  });

  it("renders masked asterisks as dots, keeping the last four", () => {
    expect(formatPanGroups("************1234")).toEqual([
      "••••",
      "••••",
      "••••",
      "1234",
    ]);
  });
});

describe("lastFourOf", () => {
  it("returns the last four characters of a masked PAN", () => {
    expect(lastFourOf("************1234")).toBe("1234");
  });
});
