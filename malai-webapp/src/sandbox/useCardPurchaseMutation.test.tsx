import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type {
  AccountHoldersResponse,
  TransactionsResponse,
} from "@/api/generated/types.gen";
import { getApiV1AccountHolderAllQueryKey } from "@/api/generated/@tanstack/react-query.gen";
import { useActiveHolderOverrideStore } from "@/account-holder/useActiveHolderOverrideStore";
import { useRecentCardPurchasesStore } from "./useRecentCardPurchasesStore";
import { useCardPurchaseMutation } from "./useCardPurchaseMutation";

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1AccountHolderAll: vi.fn(),
  getApiV1AccountsByAccountUidTransactions: vi.fn(),
  postApiV1SandboxCardPurchases: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getApiV1AccountHolderAllMock =
  sdk.getApiV1AccountHolderAll as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;
const getTransactionsMock =
  sdk.getApiV1AccountsByAccountUidTransactions as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;
const purchaseMock = sdk.postApiV1SandboxCardPurchases as unknown as ReturnType<
  typeof vi.fn<(...args: unknown[]) => unknown>
>;

const toastSuccessMock = vi.fn();
const toastErrorMock = vi.fn();
vi.mock("sonner", () => ({
  toast: {
    success: (...args: unknown[]) => toastSuccessMock(...args),
    error: (...args: unknown[]) => toastErrorMock(...args),
  },
}));

async function waitForHolder(client: QueryClient): Promise<void> {
  await waitFor(() => {
    expect(
      client.getQueryData(getApiV1AccountHolderAllQueryKey()),
    ).toBeDefined();
  });
}

function makeClient(): QueryClient {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
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
const ACCOUNT_UID = "22222222-2222-2222-2222-222222222222";
const CARD_ID = "33333333-3333-3333-3333-333333333333";
const AUTHORISATION_ID = "77777777-7777-7777-7777-777777777777";

const HOLDERS: AccountHoldersResponse = {
  accountHolders: [
    {
      accountHolderUid: INDIVIDUAL_UID,
      accountHolderName: "Ada Lovelace",
      accountHolderType: "INDIVIDUAL",
    },
  ],
};

const FEED_WITH_PENDING: TransactionsResponse = {
  accountUid: ACCOUNT_UID,
  transactions: [
    {
      entryId: AUTHORISATION_ID,
      transactionId: AUTHORISATION_ID,
      postedAt: "2026-07-12T10:00:00Z",
      currency: "GBP",
      amountMinorUnits: -1250,
      runningBalanceMinorUnits: 0,
      merchantName: "Fern & Forage",
      status: "PENDING",
      cardId: CARD_ID,
    },
  ],
};

const INPUT = {
  cardId: CARD_ID,
  merchantId: "merch-1",
  merchantName: "Fern & Forage",
  amountMinorUnits: 1250,
  accountUid: ACCOUNT_UID,
};

describe("useCardPurchaseMutation", () => {
  beforeEach(() => {
    getApiV1AccountHolderAllMock.mockReset();
    getTransactionsMock.mockReset();
    purchaseMock.mockReset();
    toastSuccessMock.mockReset();
    toastErrorMock.mockReset();
    useActiveHolderOverrideStore.setState({ override: null });
    useRecentCardPurchasesStore.setState({ purchases: [] });
  });

  it("on approval sets the outcome, toasts, and records the pending authorisation from the feed", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    purchaseMock.mockResolvedValue({
      data: { schemeReference: "sr-1", outcome: "APPROVED" },
    });
    getTransactionsMock.mockResolvedValue({ data: FEED_WITH_PENDING });
    const client = makeClient();

    const { result } = renderHook(() => useCardPurchaseMutation(), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);

    act(() => {
      result.current.submit(INPUT);
    });

    await waitFor(() => {
      expect(toastSuccessMock).toHaveBeenCalledWith("Purchase approved.");
    });
    expect(result.current.outcome?.outcome).toBe("APPROVED");

    const call = purchaseMock.mock.calls[0]?.[0] as {
      body?: Record<string, unknown>;
      headers?: Record<string, string>;
    };
    expect(call.body).toEqual({
      cardId: CARD_ID,
      merchantId: "merch-1",
      amountMinorUnits: 1250,
    });
    expect(call.headers?.["MAL-Account-Holder-Id"]).toBe(INDIVIDUAL_UID);

    await waitFor(() => {
      expect(useRecentCardPurchasesStore.getState().purchases).toHaveLength(1);
    });
    expect(useRecentCardPurchasesStore.getState().purchases[0]).toMatchObject({
      authorisationId: AUTHORISATION_ID,
      accountUid: ACCOUNT_UID,
      cardId: CARD_ID,
      merchantName: "Fern & Forage",
    });
  });

  it("on decline sets the outcome, toasts the reason, and records nothing", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    purchaseMock.mockResolvedValue({
      data: {
        schemeReference: "sr-2",
        outcome: "DECLINED",
        declineReasonCode: "62",
      },
    });
    const client = makeClient();

    const { result } = renderHook(() => useCardPurchaseMutation(), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);

    act(() => {
      result.current.submit(INPUT);
    });

    await waitFor(() => {
      expect(toastErrorMock).toHaveBeenCalledWith(
        "Purchase declined — Card frozen.",
      );
    });
    expect(result.current.outcome?.outcome).toBe("DECLINED");
    expect(getTransactionsMock).not.toHaveBeenCalled();
    expect(useRecentCardPurchasesStore.getState().purchases).toHaveLength(0);
  });
});
