import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";
import type {
  AccountResponse,
  AccountHoldersResponse,
  BalanceResponse,
  AccountIdentifiersResponse,
} from "@/api/generated/types.gen";
import { useActiveHolderOverrideStore } from "@/account-holder/useActiveHolderOverrideStore";
import { AccountCard } from "./AccountCard";
import { useBalanceVisibility } from "./useBalanceVisibility";

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1AccountHolderAll: vi.fn(),
  getApiV1AccountsByAccountUidBalance: vi.fn(),
  getApiV1AccountsByAccountUidCreditSummary: vi.fn(),
  getApiV1AccountsByAccountUidIdentifiers: vi.fn(),
  getApiV1AccountsByAccountUidTransactions: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getApiV1AccountHolderAllMock =
  sdk.getApiV1AccountHolderAll as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;
const getApiV1AccountsByAccountUidBalanceMock =
  sdk.getApiV1AccountsByAccountUidBalance as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;
const getApiV1AccountsByAccountUidCreditSummaryMock =
  sdk.getApiV1AccountsByAccountUidCreditSummary as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;
const getApiV1AccountsByAccountUidIdentifiersMock =
  sdk.getApiV1AccountsByAccountUidIdentifiers as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;
const getApiV1AccountsByAccountUidTransactionsMock =
  sdk.getApiV1AccountsByAccountUidTransactions as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;

const INDIVIDUAL_UID = "11111111-1111-1111-1111-111111111111";

const HOLDERS: AccountHoldersResponse = {
  accountHolders: [
    {
      accountHolderUid: INDIVIDUAL_UID,
      accountHolderName: "Ada Lovelace",
      accountHolderType: "INDIVIDUAL",
    },
  ],
};

const ACCOUNT: AccountResponse = {
  accountUid: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  accountType: "CURRENT",
  defaultCategory: "PERSONAL",
  currency: "GBP",
  createdAt: "2024-01-01T00:00:00Z",
  name: "Everyday",
};

const BALANCE: BalanceResponse = {
  currency: "GBP",
  amountMinorUnits: 50000,
  availableBalanceMinorUnits: 50000,
  heldAmountMinorUnits: 0,
};

const IDENTIFIERS: AccountIdentifiersResponse = {
  accountIdentifier: "87654321",
  bankIdentifier: "65-43-21",
  accountIdentifiers: [
    {
      identifierType: "UK_DOMESTIC",
      bankIdentifier: "65-43-21",
      accountIdentifier: "87654321",
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
  return (
    <QueryClientProvider client={client}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
}

const NO_TRANSACTIONS = { accountUid: ACCOUNT.accountUid, transactions: [] };
const SOME_TRANSACTIONS = {
  accountUid: ACCOUNT.accountUid,
  transactions: [
    {
      entryId: "e1",
      transactionId: "t1",
      postedAt: "2026-06-04T10:00:00Z",
      currency: "GBP" as const,
      amountMinorUnits: -350,
      runningBalanceMinorUnits: 49650,
      merchantName: "Pret",
    },
  ],
};

describe("AccountCard", () => {
  beforeEach(() => {
    getApiV1AccountHolderAllMock.mockReset();
    getApiV1AccountsByAccountUidBalanceMock.mockReset();
    getApiV1AccountsByAccountUidCreditSummaryMock.mockReset();
    getApiV1AccountsByAccountUidIdentifiersMock.mockReset();
    getApiV1AccountsByAccountUidTransactionsMock.mockReset();
    useActiveHolderOverrideStore.setState({ override: null });
    useBalanceVisibility.setState({ hidden: false });
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    getApiV1AccountsByAccountUidTransactionsMock.mockResolvedValue({
      data: NO_TRANSACTIONS,
    });
  });

  it("renders the account name, identifiers and balance once queries resolve", async () => {
    getApiV1AccountsByAccountUidBalanceMock.mockResolvedValue({
      data: BALANCE,
    });
    getApiV1AccountsByAccountUidIdentifiersMock.mockResolvedValue({
      data: IDENTIFIERS,
    });

    render(
      <Wrapper client={makeClient()}>
        <AccountCard account={ACCOUNT} />
      </Wrapper>,
    );

    expect(screen.getByText("Everyday")).toBeInTheDocument();

    await waitFor(() =>
      expect(screen.getByTestId("account-card-balance")).toHaveTextContent(
        "£500.00",
      ),
    );

    expect(screen.getByTestId("account-card-identifiers")).toHaveTextContent(
      "65-43-21 · 87654321",
    );
  });

  it("shows the spendable balance, not the ledger balance, when a payment holds funds", async () => {
    getApiV1AccountsByAccountUidBalanceMock.mockResolvedValue({
      data: {
        currency: "GBP",
        amountMinorUnits: 50000,
        availableBalanceMinorUnits: 10000,
        heldAmountMinorUnits: 40000,
      },
    });
    getApiV1AccountsByAccountUidIdentifiersMock.mockResolvedValue({
      data: IDENTIFIERS,
    });

    render(
      <Wrapper client={makeClient()}>
        <AccountCard account={ACCOUNT} />
      </Wrapper>,
    );

    await waitFor(() =>
      expect(screen.getByTestId("account-card-balance")).toHaveTextContent(
        "£100.00",
      ),
    );
    expect(screen.getByTestId("account-card-balance")).not.toHaveTextContent(
      "£500.00",
    );
  });

  it("renders a balance skeleton while the balance query is pending", () => {
    getApiV1AccountsByAccountUidBalanceMock.mockImplementation(
      () => new Promise(() => {}),
    );
    getApiV1AccountsByAccountUidIdentifiersMock.mockImplementation(
      () => new Promise(() => {}),
    );

    render(
      <Wrapper client={makeClient()}>
        <AccountCard account={ACCOUNT} />
      </Wrapper>,
    );

    expect(screen.getByTestId("account-card-balance-skeleton")).toHaveAttribute(
      "aria-busy",
      "true",
    );
    expect(
      screen.getByTestId("account-card-identifiers-skeleton"),
    ).toHaveAttribute("aria-busy", "true");
  });

  it("masks the balance when useBalanceVisibility.hidden is true", async () => {
    useBalanceVisibility.setState({ hidden: true });
    getApiV1AccountsByAccountUidBalanceMock.mockResolvedValue({
      data: BALANCE,
    });
    getApiV1AccountsByAccountUidIdentifiersMock.mockResolvedValue({
      data: IDENTIFIERS,
    });

    render(
      <Wrapper client={makeClient()}>
        <AccountCard account={ACCOUNT} />
      </Wrapper>,
    );

    await waitFor(() =>
      expect(screen.getByTestId("account-card-balance")).toHaveTextContent(
        "£·····",
      ),
    );
  });

  it("renders an inline em-dash when the balance query errors", async () => {
    getApiV1AccountsByAccountUidBalanceMock.mockRejectedValue(
      new Error("boom"),
    );
    getApiV1AccountsByAccountUidIdentifiersMock.mockResolvedValue({
      data: IDENTIFIERS,
    });

    render(
      <Wrapper client={makeClient()}>
        <AccountCard account={ACCOUNT} />
      </Wrapper>,
    );

    await waitFor(() =>
      expect(
        screen.getByTestId("account-card-balance-error"),
      ).toHaveTextContent("—"),
    );
  });

  it("shows the most recent transaction as a single line with counterparty and signed amount", async () => {
    getApiV1AccountsByAccountUidBalanceMock.mockResolvedValue({
      data: BALANCE,
    });
    getApiV1AccountsByAccountUidIdentifiersMock.mockResolvedValue({
      data: IDENTIFIERS,
    });
    getApiV1AccountsByAccountUidTransactionsMock.mockResolvedValue({
      data: SOME_TRANSACTIONS,
    });

    render(
      <Wrapper client={makeClient()}>
        <AccountCard account={ACCOUNT} />
      </Wrapper>,
    );

    await waitFor(() =>
      expect(
        screen.getByTestId("account-card-latest-transaction"),
      ).toBeInTheDocument(),
    );
    const line = screen.getByTestId("account-card-latest-transaction");
    expect(line).toHaveTextContent("Pret");
    expect(line).toHaveTextContent("−£3.50");
  });

  it("omits the latest-transaction line when the account has no transactions", async () => {
    getApiV1AccountsByAccountUidBalanceMock.mockResolvedValue({
      data: BALANCE,
    });
    getApiV1AccountsByAccountUidIdentifiersMock.mockResolvedValue({
      data: IDENTIFIERS,
    });

    render(
      <Wrapper client={makeClient()}>
        <AccountCard account={ACCOUNT} />
      </Wrapper>,
    );

    await waitFor(() =>
      expect(screen.getByTestId("account-card-balance")).toHaveTextContent(
        "£500.00",
      ),
    );

    expect(
      screen.queryByTestId("account-card-latest-transaction"),
    ).not.toBeInTheDocument();
  });
});

describe("AccountCard credit card accounts", () => {
  const CREDIT_ACCOUNT: AccountResponse = {
    ...ACCOUNT,
    accountUid: "cccccccc-cccc-cccc-cccc-cccccccccccc",
    accountType: "CREDIT_CARD",
    name: "Credit Card",
  };

  const SUMMARY = {
    currency: "GBP" as const,
    creditLimitMinorUnits: 250000,
    owedMinorUnits: 40000,
    availableCreditMinorUnits: 210000,
  };

  beforeEach(() => {
    getApiV1AccountHolderAllMock.mockReset();
    getApiV1AccountsByAccountUidBalanceMock.mockReset();
    getApiV1AccountsByAccountUidCreditSummaryMock.mockReset();
    getApiV1AccountsByAccountUidIdentifiersMock.mockReset();
    getApiV1AccountsByAccountUidTransactionsMock.mockReset();
    useActiveHolderOverrideStore.setState({ override: null });
    useBalanceVisibility.setState({ hidden: false });
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    getApiV1AccountsByAccountUidIdentifiersMock.mockResolvedValue({
      data: IDENTIFIERS,
    });
    getApiV1AccountsByAccountUidTransactionsMock.mockResolvedValue({
      data: { accountUid: CREDIT_ACCOUNT.accountUid, transactions: [] },
    });
  });

  it("shows the owed balance as negative and the available credit line instead of a balance", async () => {
    getApiV1AccountsByAccountUidCreditSummaryMock.mockResolvedValue({
      data: SUMMARY,
    });

    render(
      <Wrapper client={makeClient()}>
        <AccountCard account={CREDIT_ACCOUNT} />
      </Wrapper>,
    );

    await waitFor(() =>
      expect(screen.getByTestId("account-card-owed")).toHaveTextContent(
        "−£400.00",
      ),
    );
    expect(screen.queryByText("owed")).toBeNull();
    expect(screen.getByTestId("account-card-credit-line")).toHaveTextContent(
      "Available credit £2,100.00 · Limit £2,500.00",
    );
    expect(screen.queryByTestId("account-card-balance")).toBeNull();
    expect(getApiV1AccountsByAccountUidBalanceMock).not.toHaveBeenCalled();
  });

  it("shows a positive balance when the card is in credit", async () => {
    getApiV1AccountsByAccountUidCreditSummaryMock.mockResolvedValue({
      data: {
        currency: "GBP" as const,
        creditLimitMinorUnits: 250000,
        owedMinorUnits: -15000,
        availableCreditMinorUnits: 265000,
      },
    });

    render(
      <Wrapper client={makeClient()}>
        <AccountCard account={CREDIT_ACCOUNT} />
      </Wrapper>,
    );

    await waitFor(() =>
      expect(screen.getByTestId("account-card-owed")).toHaveTextContent(
        /^£150\.00$/,
      ),
    );
    expect(screen.queryByText("owed")).toBeNull();
  });

  it("masks the owed amount when balances are hidden", async () => {
    useBalanceVisibility.setState({ hidden: true });
    getApiV1AccountsByAccountUidCreditSummaryMock.mockResolvedValue({
      data: SUMMARY,
    });

    render(
      <Wrapper client={makeClient()}>
        <AccountCard account={CREDIT_ACCOUNT} />
      </Wrapper>,
    );

    await waitFor(() =>
      expect(screen.getByTestId("account-card-owed")).toHaveTextContent(
        "£·····",
      ),
    );
  });

  it("skips DECLINED rows when picking the latest transaction line", async () => {
    getApiV1AccountsByAccountUidCreditSummaryMock.mockResolvedValue({
      data: SUMMARY,
    });
    getApiV1AccountsByAccountUidTransactionsMock.mockResolvedValue({
      data: {
        accountUid: CREDIT_ACCOUNT.accountUid,
        transactions: [
          {
            entryId: "d1",
            transactionId: "d1",
            postedAt: "2026-07-12T10:00:00Z",
            currency: "GBP" as const,
            amountMinorUnits: -9900,
            runningBalanceMinorUnits: 0,
            merchantName: "Fern & Forage",
            status: "DECLINED",
            declineReason: "OVER_LIMIT",
          },
          {
            entryId: "e2",
            transactionId: "t2",
            postedAt: "2026-07-11T10:00:00Z",
            currency: "GBP" as const,
            amountMinorUnits: -350,
            runningBalanceMinorUnits: -40000,
            merchantName: "Brindle Coffee",
          },
        ],
      },
    });

    render(
      <Wrapper client={makeClient()}>
        <AccountCard account={CREDIT_ACCOUNT} />
      </Wrapper>,
    );

    await waitFor(() =>
      expect(
        screen.getByTestId("account-card-latest-transaction"),
      ).toHaveTextContent("Brindle Coffee"),
    );
    expect(
      screen.getByTestId("account-card-latest-transaction"),
    ).not.toHaveTextContent("Fern & Forage");
  });
});
