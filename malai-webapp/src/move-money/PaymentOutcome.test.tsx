import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import type { PaymentResponse } from "@/api/generated/types.gen";

let paymentState: PaymentResponse | null = null;

vi.mock("./usePaymentStatus", () => ({
  usePaymentStatus: (initial: PaymentResponse) => ({
    payment: paymentState ?? initial,
  }),
}));

import { PaymentOutcome } from "./PaymentOutcome";

const BASE: PaymentResponse = {
  paymentId: "44444444-4444-4444-4444-444444444444",
  state: "PENDING",
  rail: "FPS",
  direction: "OUTBOUND",
  createdAt: "2026-05-20T10:00:00Z",
  endToEndId: "55555555-5555-5555-5555-555555555555",
  fromAccountId: "33333333-3333-3333-3333-333333333333",
  amountMinorUnits: 1245,
  currency: "GBP",
};

function renderOutcome(initial: PaymentResponse) {
  return render(
    <MemoryRouter>
      <PaymentOutcome
        initial={initial}
        sourceUid="33333333-3333-3333-3333-333333333333"
        sourceAccountName="Main"
        destinationLabel="Jane Doe"
      />
    </MemoryRouter>,
  );
}

describe("PaymentOutcome", () => {
  beforeEach(() => {
    paymentState = null;
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("renders the pending screen for a PENDING payment, without the slow note", () => {
    renderOutcome(BASE);
    const pending = screen.getByTestId("payment-pending");
    expect(pending).toBeInTheDocument();
    expect(pending).toHaveTextContent("Sending your money…");
    expect(pending).toHaveTextContent("£12.45 to Jane Doe");
    expect(screen.queryByTestId("payment-pending-slow")).toBeNull();
    expect(screen.getByTestId("payment-pending-back")).toHaveAttribute(
      "href",
      "/accounts/33333333-3333-3333-3333-333333333333",
    );
  });

  it("shows the taking-longer note once a payment has been pending for a while", () => {
    vi.useFakeTimers();
    renderOutcome(BASE);
    expect(screen.queryByTestId("payment-pending-slow")).toBeNull();
    act(() => {
      vi.advanceTimersByTime(15_000);
    });
    expect(screen.getByTestId("payment-pending-slow")).toHaveTextContent(
      "This is taking longer than usual.",
    );
  });

  it("renders the settled screen with the success tick and focused heading", () => {
    paymentState = { ...BASE, state: "SETTLED" };
    renderOutcome(BASE);
    expect(screen.getByTestId("transfer-success")).toBeInTheDocument();
    expect(screen.getByTestId("success-tick")).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { level: 1, name: "Transfer sent" }),
    ).toHaveFocus();
    expect(screen.getByTestId("transfer-success-back")).toHaveAttribute(
      "href",
      "/accounts/33333333-3333-3333-3333-333333333333",
    );
  });

  it("renders the returned screen with the mapped return reason", () => {
    paymentState = {
      ...BASE,
      state: "RETURNED",
      returnReason: "ACCOUNT_NAME_MISMATCH",
    };
    renderOutcome(BASE);
    const returned = screen.getByTestId("payment-returned");
    expect(returned).toBeInTheDocument();
    expect(returned).toHaveTextContent(
      "£12.45 to Jane Doe couldn’t be delivered.",
    );
    expect(screen.getByTestId("payment-returned-reason")).toHaveTextContent(
      "The name you entered doesn't match the account.",
    );
    expect(returned).toHaveTextContent("The money is back in Main.");
    expect(screen.getByTestId("payment-returned-back")).toHaveAttribute(
      "href",
      "/accounts/33333333-3333-3333-3333-333333333333",
    );
  });

  it("renders the referred screen while the bank holds the payment for review", () => {
    paymentState = { ...BASE, state: "REFERRED" };
    renderOutcome(BASE);
    const referred = screen.getByTestId("payment-referred");
    expect(referred).toBeInTheDocument();
    expect(referred).toHaveTextContent("We’re checking this payment");
    expect(referred).toHaveTextContent("£12.45 to Jane Doe");
    expect(referred).toHaveTextContent(
      "The money is set aside and hasn’t left Main yet.",
    );
    expect(screen.queryByTestId("payment-pending")).toBeNull();
    expect(screen.getByTestId("payment-referred-back")).toHaveAttribute(
      "href",
      "/accounts/33333333-3333-3333-3333-333333333333",
    );
  });

  it("renders the declined screen with the money left where it started", () => {
    paymentState = { ...BASE, state: "DECLINED" };
    renderOutcome(BASE);
    const declined = screen.getByTestId("payment-declined");
    expect(declined).toBeInTheDocument();
    expect(declined).toHaveTextContent("We didn’t make this payment");
    expect(declined).toHaveTextContent("£12.45 to Jane Doe");
    expect(declined).toHaveTextContent("The money is still in Main.");
    expect(declined).not.toHaveTextContent(/couldn’t|unable|failed|error/i);
    expect(screen.queryByTestId("payment-pending")).toBeNull();
    expect(screen.getByTestId("payment-declined-back")).toHaveAttribute(
      "href",
      "/accounts/33333333-3333-3333-3333-333333333333",
    );
  });

  it.each(["REFERRED", "DECLINED"] as const)(
    "never explains why a %s payment was stopped",
    (state) => {
      paymentState = { ...BASE, state };
      const { container } = renderOutcome(BASE);
      expect(container.textContent).not.toMatch(
        /fraud|suspicious|suspect|flag|watchlist|sanction|screen|flagged|blocked|compliance|risk/i,
      );
      expect(screen.queryByTestId("payment-returned-reason")).toBeNull();
    },
  );
});
