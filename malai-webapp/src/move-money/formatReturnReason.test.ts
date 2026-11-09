import { describe, expect, it } from "vitest";
import { formatReturnReason } from "./formatReturnReason";

describe("formatReturnReason", () => {
  it("maps ACCOUNT_CLOSED", () => {
    expect(formatReturnReason("ACCOUNT_CLOSED")).toBe(
      "The recipient's account has been closed.",
    );
  });

  it("maps ACCOUNT_NAME_MISMATCH", () => {
    expect(formatReturnReason("ACCOUNT_NAME_MISMATCH")).toBe(
      "The name you entered doesn't match the account.",
    );
  });

  it("maps RECIPIENT_BANK_UNAVAILABLE", () => {
    expect(formatReturnReason("RECIPIENT_BANK_UNAVAILABLE")).toBe(
      "The recipient's bank isn't available right now.",
    );
  });

  it("maps BENEFICIARY_DECEASED", () => {
    expect(formatReturnReason("BENEFICIARY_DECEASED")).toBe(
      "The recipient's bank told us this account can no longer receive payments.",
    );
  });

  it("maps INVALID_ACCOUNT", () => {
    expect(formatReturnReason("INVALID_ACCOUNT")).toBe(
      "These account details don't match a real account.",
    );
  });

  it("falls back to a generic message when the reason is missing", () => {
    expect(formatReturnReason(null)).toBe(
      "The recipient's bank returned this payment.",
    );
    expect(formatReturnReason(undefined)).toBe(
      "The recipient's bank returned this payment.",
    );
  });
});
