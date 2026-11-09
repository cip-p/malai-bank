import { describe, expect, it } from "vitest";
import type { AddressV2Response } from "@/api/generated/types.gen";
import { formatAddressLines } from "./formatAddressLines";

const FULL: AddressV2Response = {
  line1: "12 Maple Grove",
  line2: "Flat 4",
  line3: "Kingsbridge",
  postTown: "Bristol",
  postCode: "BS1 4QR",
  countryCode: "GB",
};

describe("formatAddressLines", () => {
  it("orders line1, line2, line3, postTown, postCode", () => {
    expect(formatAddressLines(FULL)).toEqual([
      "12 Maple Grove",
      "Flat 4",
      "Kingsbridge",
      "Bristol",
      "BS1 4QR",
    ]);
  });

  it("omits absent optional lines", () => {
    expect(
      formatAddressLines({
        line1: "1 High Street",
        postTown: "Leeds",
        postCode: "LS1 1AA",
        countryCode: "GB",
      }),
    ).toEqual(["1 High Street", "Leeds", "LS1 1AA"]);
  });

  it("drops blank-only optional lines and trims whitespace", () => {
    expect(
      formatAddressLines({
        line1: "  7 Oak Lane  ",
        line2: "   ",
        line3: "",
        postTown: "York",
        postCode: "YO1 7HH",
        countryCode: "GB",
      }),
    ).toEqual(["7 Oak Lane", "York", "YO1 7HH"]);
  });

  it("never includes the country code", () => {
    expect(formatAddressLines(FULL)).not.toContain("GB");
  });
});
