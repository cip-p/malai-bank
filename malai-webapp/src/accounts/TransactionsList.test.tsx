import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type {
  TransactionsResponse,
  TransactionItemResponse,
  AccountHoldersResponse,
} from "@/api/generated/types.gen";
import { useActiveHolderOverrideStore } from "@/account-holder/useActiveHolderOverrideStore";
import { TransactionsList } from "./TransactionsList";

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1AccountHolderAll: vi.fn(),
  getApiV1AccountsByAccountUidTransactions: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getApiV1AccountHolderAllMock =
  sdk.getApiV1AccountHolderAll as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;
const getApiV1AccountsByAccountUidTransactionsMock =
  sdk.getApiV1AccountsByAccountUidTransactions as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;

const INDIVIDUAL_UID = "11111111-1111-1111-1111-111111111111";
const ACCOUNT_UID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

const HOLDERS: AccountHoldersResponse = {
  accountHolders: [
    {
      accountHolderUid: INDIVIDUAL_UID,
      accountHolderName: "Ada Lovelace",
      accountHolderType: "INDIVIDUAL",
    },
  ],
};

const NOW = new Date("2026-05-19T12:00:00Z");

const TRANSACTIONS: TransactionsResponse = {
  accountUid: ACCOUNT_UID,
  transactions: [
    {
      entryId: "e1",
      transactionId: "t1",
      postedAt: "2026-05-19T10:00:00Z",
      currency: "GBP",
      amountMinorUnits: -1250,
      runningBalanceMinorUnits: 123456,
      merchantName: "Pret",
      category: "Food",
    },
    {
      entryId: "e2",
      transactionId: "t2",
      postedAt: "2026-05-18T09:00:00Z",
      currency: "GBP",
      amountMinorUnits: 200000,
      runningBalanceMinorUnits: 124706,
      description: "Salary",
    },
  ],
};

function makeClient(): QueryClient {
  return new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
}

function Wrapper({
  children,
  client,
}: {
  children: ReactNode;
  client: QueryClient;
}) {
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

function buildBulk(count: number): TransactionItemResponse[] {
  return Array.from({ length: count }).map((_, idx) => ({
    entryId: `e-${idx}`,
    transactionId: `t-${idx}`,
    postedAt: "2026-05-19T10:00:00Z",
    currency: "GBP" as const,
    amountMinorUnits: -100 - idx,
    runningBalanceMinorUnits: 100000 - idx,
    merchantName: `Merchant ${idx}`,
  }));
}

describe("TransactionsList", () => {
  beforeEach(() => {
    getApiV1AccountHolderAllMock.mockReset();
    getApiV1AccountsByAccountUidTransactionsMock.mockReset();
    useActiveHolderOverrideStore.setState({ override: null });
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
  });

  it("renders a skeleton while the query is pending", () => {
    getApiV1AccountsByAccountUidTransactionsMock.mockImplementation(
      () => new Promise(() => {}),
    );
    render(
      <Wrapper client={makeClient()}>
        <TransactionsList accountUid={ACCOUNT_UID} now={NOW} />
      </Wrapper>,
    );
    expect(screen.getByTestId("transactions-skeleton")).toHaveAttribute(
      "aria-busy",
      "true",
    );
  });

  it("renders the designed empty state when the API returns zero transactions", async () => {
    getApiV1AccountsByAccountUidTransactionsMock.mockResolvedValue({
      data: { accountUid: ACCOUNT_UID, transactions: [] },
    });

    render(
      <Wrapper client={makeClient()}>
        <TransactionsList accountUid={ACCOUNT_UID} now={NOW} />
      </Wrapper>,
    );

    const empty = await screen.findByTestId("transactions-empty");
    expect(empty).toHaveTextContent(/no transactions/i);
    expect(
      within(empty).getByTestId("transactions-empty-icon"),
    ).toBeInTheDocument();
  });

  it("renders the inline error line when the query errors", async () => {
    getApiV1AccountsByAccountUidTransactionsMock.mockRejectedValue(
      new Error("boom"),
    );
    render(
      <Wrapper client={makeClient()}>
        <TransactionsList accountUid={ACCOUNT_UID} now={NOW} />
      </Wrapper>,
    );
    await waitFor(() =>
      expect(screen.getByTestId("transactions-error")).toHaveTextContent(
        "Couldn’t load transactions",
      ),
    );
  });

  it("renders date-grouped rows with sticky headers when transactions resolve", async () => {
    getApiV1AccountsByAccountUidTransactionsMock.mockResolvedValue({
      data: TRANSACTIONS,
    });
    render(
      <Wrapper client={makeClient()}>
        <TransactionsList accountUid={ACCOUNT_UID} now={NOW} />
      </Wrapper>,
    );
    await waitFor(() =>
      expect(screen.getAllByTestId("transaction-primary")).toHaveLength(2),
    );
    const groupLabels = screen
      .getAllByTestId("transaction-group-label")
      .map((el) => el.textContent?.trim());
    expect(groupLabels).toEqual(["Today", "Yesterday"]);
    const stickyHeaders = screen.getAllByTestId("transaction-group-label");
    stickyHeaders.forEach((h) => expect(h.className).toMatch(/sticky/));
  });

  it("does NOT show the 100-row footer note for smaller histories", async () => {
    getApiV1AccountsByAccountUidTransactionsMock.mockResolvedValue({
      data: { accountUid: ACCOUNT_UID, transactions: buildBulk(50) },
    });
    render(
      <Wrapper client={makeClient()}>
        <TransactionsList accountUid={ACCOUNT_UID} now={NOW} />
      </Wrapper>,
    );
    await waitFor(() =>
      expect(screen.getAllByTestId("transaction-primary")).toHaveLength(50),
    );
    expect(screen.queryByTestId("transactions-100-note")).toBeNull();
  });

  it("shows the 100-row footer note when exactly 100 transactions are returned", async () => {
    getApiV1AccountsByAccountUidTransactionsMock.mockResolvedValue({
      data: { accountUid: ACCOUNT_UID, transactions: buildBulk(100) },
    });
    render(
      <Wrapper client={makeClient()}>
        <TransactionsList accountUid={ACCOUNT_UID} now={NOW} />
      </Wrapper>,
    );
    await waitFor(() =>
      expect(screen.getAllByTestId("transaction-primary")).toHaveLength(100),
    );
    expect(screen.getByTestId("transactions-100-note")).toHaveTextContent(
      /Showing the 100 most recent/,
    );
  });

  it("opens the detail sheet when a row is clicked and returns focus on close", async () => {
    const user = userEvent.setup();
    getApiV1AccountsByAccountUidTransactionsMock.mockResolvedValue({
      data: TRANSACTIONS,
    });
    render(
      <Wrapper client={makeClient()}>
        <TransactionsList accountUid={ACCOUNT_UID} now={NOW} />
      </Wrapper>,
    );

    const rows = await screen.findAllByRole("button", { name: /Pret|Salary/ });
    await user.click(rows[0]);

    const dialog = await screen.findByRole("dialog");
    expect(dialog).toBeInTheDocument();
    expect(
      screen.getByTestId("transaction-sheet-running-balance"),
    ).toHaveTextContent("£1,234.56");

    await user.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    await waitFor(() => expect(document.activeElement).toBe(rows[0]));
  });
});
