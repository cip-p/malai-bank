import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { DirectDebitResponse } from "@/api/generated/types.gen";
import { CancelDirectDebitDialog } from "./CancelDirectDebitDialog";

const ENERGY: DirectDebitResponse = {
  directDebitId: "dd-1",
  accountId: "acc-1",
  creditorName: "Octopus Energy",
  creditorSortCode: "12-34-56",
  creditorAccountNumber: "12345678",
  amountMinorUnits: 7_800,
  currency: "GBP",
  frequency: "MONTHLY",
  status: "ACTIVE",
  startDate: "2026-01-15",
  nextCollectionAt: "2026-06-15T08:00:00Z",
};

describe("CancelDirectDebitDialog", () => {
  it("names the creditor and warns that the agreement with the company survives", () => {
    render(
      <CancelDirectDebitDialog
        open
        directDebit={ENERGY}
        onCancel={() => {}}
        onConfirm={() => {}}
      />,
    );
    const description = screen.getByTestId("cancel-dd-description");
    expect(description.textContent).toContain(
      "Cancel the direct debit to Octopus Energy? Future collections will stop, and past collections stay in your transaction history.",
    );
    expect(description.textContent).toContain(
      "Cancelling with the bank doesn’t cancel your agreement with Octopus Energy — tell them too.",
    );
  });

  it("renders a keep button and a destructive confirm button", () => {
    render(
      <CancelDirectDebitDialog
        open
        directDebit={ENERGY}
        onCancel={() => {}}
        onConfirm={() => {}}
      />,
    );
    expect(screen.getByTestId("cancel-dd-keep")).toHaveTextContent(
      "Keep direct debit",
    );
    expect(screen.getByTestId("cancel-dd-confirm")).toHaveTextContent(
      "Cancel direct debit",
    );
  });

  it("fires onCancel when the keep button is clicked", async () => {
    const onCancel = vi.fn();
    render(
      <CancelDirectDebitDialog
        open
        directDebit={ENERGY}
        onCancel={onCancel}
        onConfirm={() => {}}
      />,
    );
    await userEvent.click(screen.getByTestId("cancel-dd-keep"));
    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it("fires onConfirm and not onCancel when the confirm button is clicked", async () => {
    const onCancel = vi.fn();
    const onConfirm = vi.fn();
    render(
      <CancelDirectDebitDialog
        open
        directDebit={ENERGY}
        onCancel={onCancel}
        onConfirm={onConfirm}
      />,
    );
    await userEvent.click(screen.getByTestId("cancel-dd-confirm"));
    expect(onConfirm).toHaveBeenCalledTimes(1);
    expect(onCancel).not.toHaveBeenCalled();
  });

  it("does not render anything when open is false", () => {
    render(
      <CancelDirectDebitDialog
        open={false}
        directDebit={ENERGY}
        onCancel={() => {}}
        onConfirm={() => {}}
      />,
    );
    expect(screen.queryByTestId("cancel-dd-description")).toBeNull();
  });
});
