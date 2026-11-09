import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type {
  TransactionsResponse,
  AccountHoldersResponse,
} from "@/api/generated/types.gen";
import { useActiveHolderOverrideStore } from "@/account-holder/useActiveHolderOverrideStore";
import { useTransactions } from "./useTransactions";

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
    typeof vi.fn<
      (
        ...args: {
          path?: Record<string, string>;
          headers?: Record<string, string>;
        }[]
      ) => unknown
    >
  >;

function makeClient(): QueryClient {
  return new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
}

function wrapperOf(client: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
  Wrapper.displayName = "QueryClientWrapper";
  return Wrapper;
}

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
  ],
};

describe("useTransactions", () => {
  beforeEach(() => {
    getApiV1AccountHolderAllMock.mockReset();
    getApiV1AccountsByAccountUidTransactionsMock.mockReset();
    useActiveHolderOverrideStore.setState({ override: null });
  });

  it("calls /api/v1/accounts/{uid}/transactions with the active holder header", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    getApiV1AccountsByAccountUidTransactionsMock.mockResolvedValue({
      data: TRANSACTIONS,
    });

    const { result } = renderHook(() => useTransactions(ACCOUNT_UID), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() =>
      expect(result.current.transactions).toEqual(TRANSACTIONS.transactions),
    );

    expect(getApiV1AccountsByAccountUidTransactionsMock).toHaveBeenCalledTimes(
      1,
    );
    const firstArg = getApiV1AccountsByAccountUidTransactionsMock.mock
      .calls[0][0] as {
      path?: Record<string, string>;
      headers?: Record<string, string>;
    };
    expect(firstArg.path).toMatchObject({ accountUid: ACCOUNT_UID });
    expect(firstArg.headers).toMatchObject({
      "MAL-Account-Holder-Id": INDIVIDUAL_UID,
    });
  });

  it("waits for the active holder before firing the transactions query", () => {
    getApiV1AccountHolderAllMock.mockImplementation(
      () => new Promise(() => {}),
    );

    const { result } = renderHook(() => useTransactions(ACCOUNT_UID), {
      wrapper: wrapperOf(makeClient()),
    });

    expect(result.current.isPending).toBe(true);
    expect(result.current.transactions).toBeNull();
    expect(getApiV1AccountsByAccountUidTransactionsMock).not.toHaveBeenCalled();
  });
});
