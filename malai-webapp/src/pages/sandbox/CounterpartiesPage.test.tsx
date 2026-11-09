import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import type { CounterpartyResponse } from "@/api/generated/types.gen";
import type { UseCounterpartiesResult } from "@/sandbox/useCounterparties";

vi.mock("@/sandbox/useCounterparties", () => ({
  useCounterparties: vi.fn(),
}));

const useCounterpartiesHook = await import("@/sandbox/useCounterparties");
const useCounterpartiesMock =
  useCounterpartiesHook.useCounterparties as unknown as ReturnType<
    typeof vi.fn<() => UseCounterpartiesResult>
  >;

import { CounterpartiesPage } from "./CounterpartiesPage";

function wrap(ui: React.ReactNode) {
  return <MemoryRouter>{ui}</MemoryRouter>;
}

const SARAH: CounterpartyResponse = {
  bank: "Stub Bank",
  nameOnRecord: "Sarah Mitchell",
  sortCode: "12-34-56",
  accountNumber: "12345678",
  outboundOutcome: "SETTLES",
};

const RETURNS_ROW: CounterpartyResponse = {
  bank: "Returning Bank",
  nameOnRecord: "Bouncy Bob",
  sortCode: "20-00-00",
  accountNumber: "20000000",
  outboundOutcome: "RETURNS",
  returnReason: "ACCOUNT_NAME_MISMATCH",
};

const STUCK_ROW: CounterpartyResponse = {
  bank: "Slow Bank",
  nameOnRecord: "Patience Pringle",
  sortCode: "30-00-00",
  accountNumber: "30000000",
  outboundOutcome: "STUCK_PENDING",
};

describe("CounterpartiesPage", () => {
  beforeEach(() => {
    useCounterpartiesMock.mockReset();
  });

  it("focuses the heading on mount", () => {
    useCounterpartiesMock.mockReturnValue({
      counterparties: [],
      isPending: false,
      isError: false,
      error: null,
    });
    render(wrap(<CounterpartiesPage />));
    expect(
      screen.getByRole("heading", { name: "Counterparties" }),
    ).toHaveFocus();
  });

  it("renders the locked header sentence", () => {
    useCounterpartiesMock.mockReturnValue({
      counterparties: [],
      isPending: false,
      isError: false,
      error: null,
    });
    render(wrap(<CounterpartiesPage />));
    const sentence = screen.getByTestId("counterparties-header-sentence");
    expect(sentence).toHaveTextContent(
      "Type the name shown to get MATCH. Type a near-miss to get CLOSE_MATCH. Type anything else to get NO_MATCH.",
    );
  });

  it("renders the loading skeleton while pending", () => {
    useCounterpartiesMock.mockReturnValue({
      counterparties: null,
      isPending: true,
      isError: false,
      error: null,
    });
    render(wrap(<CounterpartiesPage />));
    expect(screen.getByTestId("counterparties-skeleton")).toBeInTheDocument();
    expect(
      screen.queryByTestId("counterparties-table"),
    ).not.toBeInTheDocument();
  });

  it("renders the error state with a retry button on failure", () => {
    useCounterpartiesMock.mockReturnValue({
      counterparties: null,
      isPending: false,
      isError: true,
      error: new Error("boom"),
    });
    render(wrap(<CounterpartiesPage />));
    expect(screen.getByTestId("counterparties-error")).toBeInTheDocument();
    expect(screen.getByTestId("counterparties-retry")).toBeInTheDocument();
  });

  it("renders the table with five columns in order", () => {
    useCounterpartiesMock.mockReturnValue({
      counterparties: [SARAH],
      isPending: false,
      isError: false,
      error: null,
    });
    render(wrap(<CounterpartiesPage />));
    const table = screen.getByTestId("counterparties-table");
    const headers = within(table).getAllByRole("columnheader");
    expect(headers.map((h) => h.textContent)).toEqual([
      "Bank",
      "Name on record",
      "Sort code",
      "Account number",
      "Outcome",
      "Actions",
    ]);
  });

  it("renders a row per counterparty with all five values populated", () => {
    useCounterpartiesMock.mockReturnValue({
      counterparties: [SARAH],
      isPending: false,
      isError: false,
      error: null,
    });
    render(wrap(<CounterpartiesPage />));
    const row = screen.getByTestId("counterparty-row");
    expect(within(row).getByTestId("counterparty-bank")).toHaveTextContent(
      "Stub Bank",
    );
    expect(
      within(row).getByTestId("counterparty-name-on-record"),
    ).toHaveTextContent("Sarah Mitchell");
    expect(within(row).getByTestId("counterparty-sort-code")).toHaveTextContent(
      "12-34-56",
    );
    expect(
      within(row).getByTestId("counterparty-account-number"),
    ).toHaveTextContent("12345678");
    expect(within(row).getByTestId("counterparty-outcome")).toHaveTextContent(
      "Settles",
    );
  });

  it("renders Save as payee / Send a payment / Receive a payment link buttons per row with the right hrefs", () => {
    useCounterpartiesMock.mockReturnValue({
      counterparties: [SARAH],
      isPending: false,
      isError: false,
      error: null,
    });
    render(wrap(<CounterpartiesPage />));
    const row = screen.getByTestId("counterparty-row");
    const saveAsPayee = within(row).getByTestId("counterparty-save-as-payee");
    const sendAPayment = within(row).getByTestId("counterparty-send-a-payment");
    const receiveAPayment = within(row).getByTestId(
      "counterparty-receive-a-payment",
    );
    expect(saveAsPayee).toHaveTextContent("Save as payee");
    expect(sendAPayment).toHaveTextContent("Send a payment");
    expect(receiveAPayment).toHaveTextContent("Receive a payment");
    expect(saveAsPayee).toHaveAttribute(
      "href",
      "/move/payees/new?sortCode=12-34-56&accountNumber=12345678&name=Sarah%20Mitchell",
    );
    expect(sendAPayment).toHaveAttribute(
      "href",
      "/move/send?destSortCode=12-34-56&destAccountNumber=12345678&destName=Sarah%20Mitchell",
    );
    expect(receiveAPayment).toHaveAttribute(
      "href",
      "/sandbox/inbound-payments?originatorSortCode=12-34-56&originatorAccountNumber=12345678&originatorName=Sarah%20Mitchell",
    );
  });

  it("formats Outcome per OutboundOutcome value", () => {
    useCounterpartiesMock.mockReturnValue({
      counterparties: [SARAH, RETURNS_ROW, STUCK_ROW],
      isPending: false,
      isError: false,
      error: null,
    });
    render(wrap(<CounterpartiesPage />));
    const outcomeCells = screen.getAllByTestId("counterparty-outcome");
    expect(outcomeCells.map((c) => c.textContent)).toEqual([
      "Settles",
      "Returns (ACCOUNT_NAME_MISMATCH)",
      "Stays in progress",
    ]);
  });
});
