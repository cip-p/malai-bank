import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { StandingOrderResponse } from "@/api/generated/types.gen";
import { CancelStandingOrderDialog } from "./CancelStandingOrderDialog";

const RENT: StandingOrderResponse = {
  standingOrderId: "so-1",
  accountId: "acc-1",
  payeeName: "Landlord",
  payeeSortCode: "12-34-56",
  payeeAccountNumber: "12345678",
  amountMinorUnits: 95_000,
  currency: "GBP",
  frequency: "MONTHLY",
  status: "ACTIVE",
  startDate: "2026-01-15",
  nextPaymentAt: "2026-06-15T08:00:00Z",
};

describe("CancelStandingOrderDialog", () => {
  it("names the payee and explains that future payments stop but history stays", () => {
    render(
      <CancelStandingOrderDialog
        open
        standingOrder={RENT}
        onCancel={() => {}}
        onConfirm={() => {}}
      />,
    );
    const description = screen.getByTestId("cancel-so-description");
    expect(description.textContent).toContain(
      "Cancel the standing order to Landlord? Future payments will stop. Payments already made stay in your transaction history.",
    );
  });

  it("renders a keep button and a destructive confirm button", () => {
    render(
      <CancelStandingOrderDialog
        open
        standingOrder={RENT}
        onCancel={() => {}}
        onConfirm={() => {}}
      />,
    );
    expect(screen.getByTestId("cancel-so-keep")).toHaveTextContent(
      "Keep standing order",
    );
    expect(screen.getByTestId("cancel-so-confirm")).toHaveTextContent(
      "Cancel standing order",
    );
  });

  it("fires onCancel when the keep button is clicked", async () => {
    const onCancel = vi.fn();
    render(
      <CancelStandingOrderDialog
        open
        standingOrder={RENT}
        onCancel={onCancel}
        onConfirm={() => {}}
      />,
    );
    await userEvent.click(screen.getByTestId("cancel-so-keep"));
    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it("fires onConfirm and not onCancel when the confirm button is clicked", async () => {
    const onCancel = vi.fn();
    const onConfirm = vi.fn();
    render(
      <CancelStandingOrderDialog
        open
        standingOrder={RENT}
        onCancel={onCancel}
        onConfirm={onConfirm}
      />,
    );
    await userEvent.click(screen.getByTestId("cancel-so-confirm"));
    expect(onConfirm).toHaveBeenCalledTimes(1);
    expect(onCancel).not.toHaveBeenCalled();
  });

  it("does not render anything when open is false", () => {
    render(
      <CancelStandingOrderDialog
        open={false}
        standingOrder={RENT}
        onCancel={() => {}}
        onConfirm={() => {}}
      />,
    );
    expect(screen.queryByTestId("cancel-so-description")).toBeNull();
  });
});
