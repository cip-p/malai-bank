import { describe, expect, it } from "vitest";
import type { CounterpartyResponse } from "@/api/generated/types.gen";
import { counterpartyDeepLink } from "./counterpartyDeepLink";

const ROW: CounterpartyResponse = {
  bank: "Stub Bank",
  nameOnRecord: "Sarah Mitchell",
  sortCode: "12-34-56",
  accountNumber: "12345678",
  outboundOutcome: "SETTLES",
};

describe("counterpartyDeepLink", () => {
  it("builds the Save as payee URL targeting /move/payees/new", () => {
    expect(counterpartyDeepLink(ROW, "save-as-payee")).toBe(
      "/move/payees/new?sortCode=12-34-56&accountNumber=12345678&name=Sarah%20Mitchell",
    );
  });

  it("builds the Send a payment URL targeting /move/send New payee tab", () => {
    expect(counterpartyDeepLink(ROW, "send-a-payment")).toBe(
      "/move/send?destSortCode=12-34-56&destAccountNumber=12345678&destName=Sarah%20Mitchell",
    );
  });

  it("builds the Receive a payment URL targeting /sandbox/inbound-payments", () => {
    expect(counterpartyDeepLink(ROW, "receive-a-payment")).toBe(
      "/sandbox/inbound-payments?originatorSortCode=12-34-56&originatorAccountNumber=12345678&originatorName=Sarah%20Mitchell",
    );
  });

  it("URL-encodes the name on the Save as payee URL (space and apostrophe)", () => {
    const url = counterpartyDeepLink(
      { ...ROW, nameOnRecord: "O'Connell Trust Ltd" },
      "save-as-payee",
    );
    expect(url).toContain("name=O%27Connell%20Trust%20Ltd");
  });

  it("URL-encodes the name on the Send a payment URL (space and apostrophe)", () => {
    const url = counterpartyDeepLink(
      { ...ROW, nameOnRecord: "O'Connell Trust Ltd" },
      "send-a-payment",
    );
    expect(url).toContain("destName=O%27Connell%20Trust%20Ltd");
  });

  it("URL-encodes the name on the Receive a payment URL (space and apostrophe)", () => {
    const url = counterpartyDeepLink(
      { ...ROW, nameOnRecord: "O'Connell Trust Ltd" },
      "receive-a-payment",
    );
    expect(url).toContain("originatorName=O%27Connell%20Trust%20Ltd");
  });
});
