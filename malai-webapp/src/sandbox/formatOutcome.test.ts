import { describe, expect, it } from "vitest";
import type { CounterpartyResponse } from "@/api/generated/types.gen";
import { formatOutcome } from "./formatOutcome";

const BASE: CounterpartyResponse = {
  bank: "Stub Bank",
  nameOnRecord: "Sarah Mitchell",
  sortCode: "12-34-56",
  accountNumber: "12345678",
  outboundOutcome: "SETTLES",
};

describe("formatOutcome", () => {
  it("renders SETTLES as 'Settles'", () => {
    expect(formatOutcome({ ...BASE, outboundOutcome: "SETTLES" })).toBe(
      "Settles",
    );
  });

  it("renders RETURNS with the returnReason in parens", () => {
    expect(
      formatOutcome({
        ...BASE,
        outboundOutcome: "RETURNS",
        returnReason: "ACCOUNT_NAME_MISMATCH",
      }),
    ).toBe("Returns (ACCOUNT_NAME_MISMATCH)");
  });

  it("renders STUCK_PENDING as 'Stays in progress'", () => {
    expect(formatOutcome({ ...BASE, outboundOutcome: "STUCK_PENDING" })).toBe(
      "Stays in progress",
    );
  });
});
