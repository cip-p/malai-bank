import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import type {
  AccountResponse,
  DirectDebitResponse,
} from "@/api/generated/types.gen";
import { DirectDebitsPage } from "./DirectDebitsPage";

const useDirectDebitsMock = vi.fn();
const useAccountsMock = vi.fn();
const cancelDirectDebitMock = vi.fn();
vi.mock("@/move-money/useDirectDebits", () => ({
  useDirectDebits: () => useDirectDebitsMock(),
}));
vi.mock("@/accounts/useAccounts", () => ({
  useAccounts: () => useAccountsMock(),
}));
vi.mock("@/move-money/useCancelDirectDebit", () => ({
  useCancelDirectDebit: () => ({
    cancelDirectDebit: cancelDirectDebitMock,
    isPending: false,
  }),
}));

function makeAccount(over: Partial<AccountResponse> = {}): AccountResponse {
  return {
    accountUid: "acc-1",
    accountType: "CURRENT",
    defaultCategory: "PERSONAL",
    currency: "GBP",
    createdAt: "2024-01-01T00:00:00Z",
    name: "Main",
    ...over,
  };
}

function makeDd(over: Partial<DirectDebitResponse>): DirectDebitResponse {
  return {
    directDebitId: "dd-1",
    accountId: "acc-1",
    creditorName: "Thames Water",
    creditorSortCode: "12-34-56",
    creditorAccountNumber: "12345678",
    amountMinorUnits: 4500,
    currency: "GBP",
    frequency: "MONTHLY",
    status: "ACTIVE",
    startDate: "2026-01-15",
    nextCollectionAt: "2026-05-23T12:00:00Z",
    ...over,
  };
}

function renderPage() {
  return render(
    <MemoryRouter>
      <DirectDebitsPage now={new Date("2026-05-20T12:00:00Z")} />
    </MemoryRouter>,
  );
}

describe("DirectDebitsPage", () => {
  beforeEach(() => {
    useDirectDebitsMock.mockReset();
    useAccountsMock.mockReset();
    cancelDirectDebitMock.mockReset();
    useAccountsMock.mockReturnValue({ accounts: [makeAccount()] });
  });

  it("renders the h1 'Direct Debits' and focuses it on mount", () => {
    useDirectDebitsMock.mockReturnValue({
      directDebits: [],
      isPending: false,
      isError: false,
    });
    renderPage();
    const h1 = screen.getByRole("heading", { level: 1 });
    expect(h1).toHaveTextContent("Direct Debits");
    expect(h1).toHaveFocus();
  });

  it("renders a loading skeleton while pending", () => {
    useDirectDebitsMock.mockReturnValue({
      directDebits: null,
      isPending: true,
      isError: false,
    });
    renderPage();
    expect(screen.getByTestId("dd-skeleton")).toBeInTheDocument();
  });

  it("renders an error caption when the request fails", () => {
    useDirectDebitsMock.mockReturnValue({
      directDebits: null,
      isPending: false,
      isError: true,
    });
    renderPage();
    expect(screen.getByTestId("dd-error")).toHaveTextContent(
      "Couldn’t load direct debits",
    );
  });

  it("renders the designed empty state with a headline and body, no Create CTA", () => {
    useDirectDebitsMock.mockReturnValue({
      directDebits: [],
      isPending: false,
      isError: false,
    });
    renderPage();
    const empty = screen.getByTestId("dd-empty");
    expect(empty).toHaveTextContent("No active direct debits");
    expect(empty).toHaveTextContent(
      "A direct debit is a recurring pull of money initiated by the company being paid — set up via the company, not the bank.",
    );
    expect(
      screen.queryByRole("button", { name: /create|add|new/i }),
    ).toBeNull();
    expect(screen.queryByRole("link", { name: /create|add|new/i })).toBeNull();
  });

  it("renders direct debits sorted by nextCollectionAt ascending with nulls bucketed", () => {
    useDirectDebitsMock.mockReturnValue({
      directDebits: [
        makeDd({
          directDebitId: "dd-2",
          creditorName: "Vodafone",
          nextCollectionAt: "2026-06-01T12:00:00Z",
        }),
        makeDd({
          directDebitId: "dd-3",
          creditorName: "Council Tax",
          nextCollectionAt: undefined,
        }),
        makeDd({
          directDebitId: "dd-1",
          creditorName: "Thames Water",
          nextCollectionAt: "2026-05-23T12:00:00Z",
        }),
      ],
      isPending: false,
      isError: false,
    });
    renderPage();
    const list = screen.getByTestId("dd-list");
    const rows = within(list).getAllByTestId("dd-row");
    expect(rows).toHaveLength(3);
    expect(within(rows[0]).getByTestId("dd-primary")).toHaveTextContent(
      "Thames Water",
    );
    expect(within(rows[1]).getByTestId("dd-primary")).toHaveTextContent(
      "Vodafone",
    );
    expect(within(rows[2]).getByTestId("dd-primary")).toHaveTextContent(
      "Council Tax",
    );
    expect(within(rows[2]).getByTestId("dd-next")).toHaveTextContent(
      "Awaiting first collection",
    );
  });

  it("renders variable-amount DDs as the literal word 'Variable'", () => {
    useDirectDebitsMock.mockReturnValue({
      directDebits: [makeDd({ amountMinorUnits: undefined })],
      isPending: false,
      isError: false,
    });
    renderPage();
    expect(screen.getByTestId("dd-amount")).toHaveTextContent("Variable");
  });

  it("keeps CANCELLED mandates listed, greyed with a Cancelled badge, sorted after active ones", () => {
    useDirectDebitsMock.mockReturnValue({
      directDebits: [
        makeDd({
          directDebitId: "dd-gone",
          creditorName: "Old Gym",
          status: "CANCELLED",
          cancelledAt: "2026-05-01T09:00:00Z",
          nextCollectionAt: "2026-05-21T12:00:00Z",
        }),
        makeDd({
          directDebitId: "dd-live",
          creditorName: "Thames Water",
          nextCollectionAt: "2026-05-23T12:00:00Z",
        }),
      ],
      isPending: false,
      isError: false,
    });
    renderPage();
    const rows = within(screen.getByTestId("dd-list")).getAllByTestId("dd-row");
    expect(rows).toHaveLength(2);
    expect(within(rows[0]).getByTestId("dd-primary")).toHaveTextContent(
      "Thames Water",
    );
    expect(within(rows[1]).getByTestId("dd-primary")).toHaveTextContent(
      "Old Gym",
    );
    expect(within(rows[1]).getByTestId("dd-cancelled-badge")).toHaveTextContent(
      "Cancelled",
    );
    expect(within(rows[1]).queryByTestId("dd-cancel")).toBeNull();
  });

  it("opens the confirmation dialog from a row's cancel button and cancels on confirm", async () => {
    useDirectDebitsMock.mockReturnValue({
      directDebits: [makeDd({ directDebitId: "dd-9" })],
      isPending: false,
      isError: false,
    });
    renderPage();

    expect(screen.queryByTestId("cancel-dd-dialog")).toBeNull();
    await userEvent.click(
      screen.getByRole("button", {
        name: "Cancel direct debit to Thames Water",
      }),
    );
    expect(screen.getByTestId("cancel-dd-dialog")).toBeInTheDocument();

    await userEvent.click(screen.getByTestId("cancel-dd-confirm"));
    expect(cancelDirectDebitMock).toHaveBeenCalledWith("dd-9");
    expect(screen.queryByTestId("cancel-dd-dialog")).toBeNull();
  });

  it("keeps the direct debit when the dialog is dismissed", async () => {
    useDirectDebitsMock.mockReturnValue({
      directDebits: [makeDd({})],
      isPending: false,
      isError: false,
    });
    renderPage();

    await userEvent.click(
      screen.getByRole("button", {
        name: "Cancel direct debit to Thames Water",
      }),
    );
    await userEvent.click(screen.getByTestId("cancel-dd-keep"));
    expect(cancelDirectDebitMock).not.toHaveBeenCalled();
    expect(screen.queryByTestId("cancel-dd-dialog")).toBeNull();
  });

  it("omits the account chip when the holder has a single Account", () => {
    useAccountsMock.mockReturnValue({ accounts: [makeAccount()] });
    useDirectDebitsMock.mockReturnValue({
      directDebits: [makeDd({})],
      isPending: false,
      isError: false,
    });
    renderPage();
    expect(screen.queryByTestId("dd-account-chip")).toBeNull();
  });

  it("renders the account chip with the account name when the holder has ≥2 Accounts", () => {
    useAccountsMock.mockReturnValue({
      accounts: [
        makeAccount({ accountUid: "acc-1", name: "Main" }),
        makeAccount({ accountUid: "acc-2", name: "Savings" }),
      ],
    });
    useDirectDebitsMock.mockReturnValue({
      directDebits: [makeDd({ accountId: "acc-2" })],
      isPending: false,
      isError: false,
    });
    renderPage();
    expect(screen.getByTestId("dd-account-chip")).toHaveTextContent("Savings");
  });
});
