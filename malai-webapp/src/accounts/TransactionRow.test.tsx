import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { TransactionItemResponse } from "@/api/generated/types.gen";
import { TransactionRow } from "./TransactionRow";

function tx(
  overrides: Partial<TransactionItemResponse> = {},
): TransactionItemResponse {
  return {
    entryId: "e1",
    transactionId: "t1",
    postedAt: "2026-05-19T10:00:00Z",
    currency: "GBP",
    amountMinorUnits: -1250,
    runningBalanceMinorUnits: 123456,
    ...overrides,
  };
}

describe("TransactionRow", () => {
  it("renders merchantName as the primary label when present", () => {
    render(
      <TransactionRow
        transaction={tx({ merchantName: "Pret" })}
        onOpen={() => {}}
      />,
    );
    expect(screen.getByTestId("transaction-primary")).toHaveTextContent("Pret");
  });

  it("falls back to description when merchantName is absent", () => {
    render(
      <TransactionRow
        transaction={tx({
          merchantName: undefined,
          description: "Direct debit",
        })}
        onOpen={() => {}}
      />,
    );
    expect(screen.getByTestId("transaction-primary")).toHaveTextContent(
      "Direct debit",
    );
  });

  it("falls back to 'Transaction' when both merchantName and description are absent", () => {
    render(
      <TransactionRow
        transaction={tx({ merchantName: undefined, description: undefined })}
        onOpen={() => {}}
      />,
    );
    expect(screen.getByTestId("transaction-primary")).toHaveTextContent(
      "Transaction",
    );
  });

  it("renders description as a muted secondary line when both merchant and description are present", () => {
    render(
      <TransactionRow
        transaction={tx({ merchantName: "Pret", description: "Coffee" })}
        onOpen={() => {}}
      />,
    );
    expect(screen.getByTestId("transaction-secondary")).toHaveTextContent(
      "Coffee",
    );
  });

  it("does not render a secondary line when only the primary label is available", () => {
    render(
      <TransactionRow
        transaction={tx({ merchantName: "Pret", description: undefined })}
        onOpen={() => {}}
      />,
    );
    expect(screen.queryByTestId("transaction-secondary")).toBeNull();
  });

  it("renders a category pill with the human label when the transaction is categorised", () => {
    render(
      <TransactionRow
        transaction={tx({ merchantName: "Tesco", category: "GROCERIES" })}
        onOpen={() => {}}
      />,
    );
    const pill = screen.getByTestId("transaction-category-pill");
    expect(pill).toHaveTextContent("Groceries");
    expect(pill).toHaveAttribute("data-category", "GROCERIES");
  });

  it("renders no category pill when the transaction is uncategorised (insights unavailable)", () => {
    render(
      <TransactionRow
        transaction={tx({ merchantName: "Tesco", category: undefined })}
        onOpen={() => {}}
      />,
    );
    expect(screen.queryByTestId("transaction-category-pill")).toBeNull();
  });

  it("renders a debit amount with the Unicode minus prefix and the negative tone", () => {
    render(
      <TransactionRow
        transaction={tx({ amountMinorUnits: -1250 })}
        onOpen={() => {}}
      />,
    );
    expect(screen.getByTestId("transaction-amount")).toHaveTextContent(
      "−£12.50",
    );
  });

  it("renders a credit amount with the '+' prefix", () => {
    render(
      <TransactionRow
        transaction={tx({ amountMinorUnits: 200000 })}
        onOpen={() => {}}
      />,
    );
    expect(screen.getByTestId("transaction-amount")).toHaveTextContent(
      "+£2,000.00",
    );
  });

  it("does NOT render a running balance on the row (running balance now lives in the detail sheet only)", () => {
    render(
      <TransactionRow
        transaction={tx({ runningBalanceMinorUnits: 123456 })}
        onOpen={() => {}}
      />,
    );
    expect(screen.queryByTestId("transaction-running-balance")).toBeNull();
  });

  it("invokes onOpen with the transaction when the row is clicked", async () => {
    const onOpen = vi.fn();
    const user = userEvent.setup();
    const data = tx({ merchantName: "Pret" });
    render(<TransactionRow transaction={data} onOpen={onOpen} />);
    await user.click(screen.getByRole("button", { name: /Pret/ }));
    expect(onOpen).toHaveBeenCalledWith(data);
  });
});

describe("TransactionRow card rows", () => {
  it("renders a Pending badge and a muted amount on a PENDING card row", () => {
    render(
      <TransactionRow
        transaction={tx({
          merchantName: "Fern & Forage",
          status: "PENDING",
          cardId: "c1",
        })}
        onOpen={() => {}}
      />,
    );
    expect(screen.getByTestId("transaction-pending-badge")).toHaveTextContent(
      "Pending",
    );
    expect(screen.getByTestId("transaction-amount")).toHaveClass(
      "text-muted-foreground",
    );
    expect(screen.getByTestId("transaction-amount")).toHaveTextContent(
      "−£12.50",
    );
  });

  it("renders a Declined badge, the reason, and a struck-through unsigned amount on a DECLINED card row", () => {
    render(
      <TransactionRow
        transaction={tx({
          merchantName: "Fern & Forage",
          status: "DECLINED",
          declineReason: "CARD_FROZEN",
          cardId: "c1",
        })}
        onOpen={() => {}}
      />,
    );
    expect(screen.getByTestId("transaction-declined-badge")).toHaveTextContent(
      "Declined",
    );
    expect(screen.getByTestId("transaction-secondary")).toHaveTextContent(
      "Card frozen",
    );
    const amount = screen.getByTestId("transaction-amount");
    expect(amount).toHaveClass("line-through");
    expect(amount).toHaveTextContent("£12.50");
    expect(amount).not.toHaveTextContent("−");
  });

  it("does not render a category pill on a DECLINED row", () => {
    render(
      <TransactionRow
        transaction={tx({
          merchantName: "Fern & Forage",
          status: "DECLINED",
          declineReason: "OVER_LIMIT",
          category: "GROCERIES",
        })}
        onOpen={() => {}}
      />,
    );
    expect(screen.queryByTestId("transaction-category-pill")).toBeNull();
  });

  it("renders no badge on a plain settled ledger row", () => {
    render(
      <TransactionRow
        transaction={tx({ merchantName: "Pret" })}
        onOpen={() => {}}
      />,
    );
    expect(screen.queryByTestId("transaction-pending-badge")).toBeNull();
    expect(screen.queryByTestId("transaction-declined-badge")).toBeNull();
  });
});
