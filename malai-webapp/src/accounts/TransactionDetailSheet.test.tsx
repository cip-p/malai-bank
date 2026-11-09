import { describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import type { TransactionItemResponse } from "@/api/generated/types.gen";
import { TransactionDetailSheet } from "./TransactionDetailSheet";

vi.mock("./TransactionCategoryEditor", () => ({
  TransactionCategoryEditor: ({ category }: { category?: string }) => (
    <div data-testid="transaction-sheet-category">{category}</div>
  ),
}));

const ACCOUNT_UID = "acc-1";

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
    merchantName: "Pret",
    description: "Coffee and croissant",
    counterpartyId: "cp-pret-1",
    category: "Food",
    ...overrides,
  };
}

describe("TransactionDetailSheet", () => {
  it("renders nothing when closed", () => {
    render(
      <TransactionDetailSheet
        transaction={tx()}
        accountUid={ACCOUNT_UID}
        open={false}
        onOpenChange={() => {}}
      />,
    );
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("renders the dialog with the running balance when open", () => {
    render(
      <TransactionDetailSheet
        transaction={tx({ runningBalanceMinorUnits: 123456 })}
        accountUid={ACCOUNT_UID}
        open
        onOpenChange={() => {}}
      />,
    );
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(
      screen.getByTestId("transaction-sheet-running-balance"),
    ).toHaveTextContent("£1,234.56");
  });

  it("renders the signed amount with the new-design tones", () => {
    render(
      <TransactionDetailSheet
        transaction={tx({ amountMinorUnits: -1250 })}
        accountUid={ACCOUNT_UID}
        open
        onOpenChange={() => {}}
      />,
    );
    expect(screen.getByTestId("transaction-sheet-amount")).toHaveTextContent(
      "−£12.50",
    );
  });

  it("renders the counterparty / merchant / description / category / posted timestamp", () => {
    render(
      <TransactionDetailSheet
        transaction={tx({
          merchantName: "Pret",
          description: "Coffee and croissant",
          counterpartyId: "cp-pret-1",
          category: "Food",
          postedAt: "2026-05-19T10:00:00Z",
        })}
        accountUid={ACCOUNT_UID}
        open
        onOpenChange={() => {}}
      />,
    );
    expect(screen.getByTestId("transaction-sheet-merchant")).toHaveTextContent(
      "Pret",
    );
    expect(
      screen.getByTestId("transaction-sheet-description"),
    ).toHaveTextContent("Coffee and croissant");
    expect(
      screen.getByTestId("transaction-sheet-counterparty"),
    ).toHaveTextContent("cp-pret-1");
    expect(screen.getByTestId("transaction-sheet-category")).toHaveTextContent(
      "Food",
    );
    expect(screen.getByTestId("transaction-sheet-posted")).toHaveTextContent(
      /19 May 2026/,
    );
  });

  it("dismisses on Escape", async () => {
    const onOpenChange = vi.fn();
    const user = userEvent.setup();
    render(
      <TransactionDetailSheet
        transaction={tx()}
        accountUid={ACCOUNT_UID}
        open
        onOpenChange={onOpenChange}
      />,
    );
    await user.keyboard("{Escape}");
    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false));
  });

  it("falls back to description when merchantName is absent", () => {
    render(
      <TransactionDetailSheet
        transaction={tx({ merchantName: undefined, description: "Salary" })}
        accountUid={ACCOUNT_UID}
        open
        onOpenChange={() => {}}
      />,
    );
    expect(screen.getByTestId("transaction-sheet-title")).toHaveTextContent(
      "Salary",
    );
  });
});

describe("TransactionDetailSheet card rows", () => {
  function renderCardRow(overrides: Partial<TransactionItemResponse>) {
    return render(
      <MemoryRouter>
        <TransactionDetailSheet
          transaction={tx({
            category: undefined,
            counterpartyId: undefined,
            description: undefined,
            cardId: "33333333-3333-3333-3333-333333333333",
            ...overrides,
          })}
          accountUid={ACCOUNT_UID}
          open
          onOpenChange={() => {}}
        />
      </MemoryRouter>,
    );
  }

  it("shows a pending note instead of the running balance on a PENDING row", () => {
    renderCardRow({ status: "PENDING" });
    expect(
      screen.getByTestId("transaction-sheet-pending-note"),
    ).toBeInTheDocument();
    expect(
      screen.queryByTestId("transaction-sheet-running-balance"),
    ).toBeNull();
    expect(screen.getByTestId("transaction-sheet-status")).toHaveTextContent(
      "Pending",
    );
  });

  it("shows the decline reason and an unsigned struck-through amount on a DECLINED row", () => {
    renderCardRow({ status: "DECLINED", declineReason: "CARD_FROZEN" });
    expect(
      screen.getByTestId("transaction-sheet-declined-note"),
    ).toBeInTheDocument();
    expect(
      screen.getByTestId("transaction-sheet-decline-reason"),
    ).toHaveTextContent("Card frozen");
    const amount = screen.getByTestId("transaction-sheet-amount");
    expect(amount).toHaveClass("line-through");
    expect(amount).toHaveTextContent("£12.50");
  });

  it("links through to the card and hides the category editor on card rows", () => {
    renderCardRow({ status: "PENDING" });
    expect(screen.getByTestId("transaction-sheet-card-link")).toHaveAttribute(
      "href",
      "/cards/33333333-3333-3333-3333-333333333333",
    );
    expect(screen.queryByTestId("transaction-sheet-category")).toBeNull();
  });

  it("keeps the category editor and running balance on plain ledger rows", () => {
    render(
      <TransactionDetailSheet
        transaction={tx()}
        accountUid={ACCOUNT_UID}
        open
        onOpenChange={() => {}}
      />,
    );
    expect(
      screen.getByTestId("transaction-sheet-running-balance"),
    ).toBeInTheDocument();
    expect(
      screen.getByTestId("transaction-sheet-category"),
    ).toBeInTheDocument();
    expect(screen.queryByTestId("transaction-sheet-status")).toBeNull();
  });
});
