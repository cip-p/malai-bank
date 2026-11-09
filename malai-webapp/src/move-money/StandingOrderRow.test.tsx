import { describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import type { StandingOrderResponse } from "@/api/generated/types.gen";
import { StandingOrderRow } from "./StandingOrderRow";

const BASE: StandingOrderResponse = {
  standingOrderId: "so-1",
  accountId: "acc-1",
  payeeName: "Landlord",
  payeeSortCode: "12-34-56",
  payeeAccountNumber: "87654321",
  amountMinorUnits: 150_000,
  currency: "GBP",
  frequency: "MONTHLY",
  status: "ACTIVE",
  startDate: "2026-01-15",
  nextPaymentAt: "2026-05-23T12:00:00Z",
};

const NOW = new Date("2026-05-20T12:00:00Z");

function renderRow(ui: React.ReactNode) {
  return render(
    <MemoryRouter>
      <ul>{ui}</ul>
    </MemoryRouter>,
  );
}

describe("StandingOrderRow", () => {
  it("renders the payeeName as plain text when no payeeHref is provided", () => {
    renderRow(<StandingOrderRow so={BASE} now={NOW} />);
    const primary = screen.getByTestId("so-primary");
    expect(primary).toHaveTextContent("Landlord");
    expect(within(primary).queryByRole("link")).toBeNull();
  });

  it("renders the payeeName as a link when payeeHref is provided", () => {
    renderRow(
      <StandingOrderRow
        so={{ ...BASE, payeeId: "payee-99" }}
        now={NOW}
        payeeHref="/move/payees#payee-99"
      />,
    );
    const link = within(screen.getByTestId("so-primary")).getByRole("link", {
      name: "Landlord",
    });
    expect(link).toHaveAttribute("href", "/move/payees#payee-99");
  });

  it("renders an avatar showing the first initial of the payeeName", () => {
    renderRow(<StandingOrderRow so={BASE} now={NOW} />);
    expect(screen.getByTestId("initial-avatar")).toHaveTextContent("L");
  });

  it("renders the formatted amount via Money", () => {
    renderRow(<StandingOrderRow so={BASE} now={NOW} />);
    expect(screen.getByTestId("so-amount")).toHaveTextContent("£1,500.00");
  });

  it("renders the title-cased frequency", () => {
    renderRow(
      <StandingOrderRow so={{ ...BASE, frequency: "ANNUAL" }} now={NOW} />,
    );
    expect(screen.getByTestId("so-frequency")).toHaveTextContent("Annually");
  });

  it("renders 'Next: in 3 days' for a date 3 days from now", () => {
    renderRow(<StandingOrderRow so={BASE} now={NOW} />);
    expect(screen.getByTestId("so-next")).toHaveTextContent("Next: in 3 days");
  });

  it("renders 'Next: not scheduled' when nextPaymentAt is undefined", () => {
    renderRow(
      <StandingOrderRow so={{ ...BASE, nextPaymentAt: undefined }} now={NOW} />,
    );
    expect(screen.getByTestId("so-next")).toHaveTextContent(
      "Next: not scheduled",
    );
  });

  it("renders the account chip when accountLabel is provided", () => {
    renderRow(<StandingOrderRow so={BASE} now={NOW} accountLabel="Main" />);
    expect(screen.getByTestId("so-account-chip")).toHaveTextContent("Main");
  });

  it("omits the account chip when accountLabel is undefined", () => {
    renderRow(<StandingOrderRow so={BASE} now={NOW} />);
    expect(screen.queryByTestId("so-account-chip")).toBeNull();
  });

  it("renders a cancel button naming the payee when onCancel is provided", async () => {
    const onCancel = vi.fn();
    renderRow(<StandingOrderRow so={BASE} now={NOW} onCancel={onCancel} />);
    const button = screen.getByRole("button", {
      name: "Cancel standing order to Landlord",
    });
    await userEvent.click(button);
    expect(onCancel).toHaveBeenCalledWith(BASE);
  });

  it("omits the cancel button when onCancel is undefined", () => {
    renderRow(<StandingOrderRow so={BASE} now={NOW} />);
    expect(screen.queryByTestId("so-cancel")).toBeNull();
  });

  it("carries the standingOrderId as an id anchor for deep links", () => {
    renderRow(<StandingOrderRow so={BASE} now={NOW} />);
    expect(screen.getByTestId("so-row")).toHaveAttribute("id", "so-1");
  });
});
