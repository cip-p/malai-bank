import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type {
  AccountHoldersResponse,
  SpendingBreakdownResponse,
} from "@/api/generated/types.gen";
import { HttpError } from "@/api/http";
import { useActiveHolderOverrideStore } from "@/account-holder/useActiveHolderOverrideStore";
import { useSpendingBreakdown } from "./useSpendingBreakdown";

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1AccountHolderAll: vi.fn(),
  getApiV1AccountsByAccountUidSpending: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getApiV1AccountHolderAllMock =
  sdk.getApiV1AccountHolderAll as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;
const getApiV1AccountsByAccountUidSpendingMock =
  sdk.getApiV1AccountsByAccountUidSpending as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
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

const ACCOUNT_UID = "22222222-2222-2222-2222-222222222222";
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

const BREAKDOWN: SpendingBreakdownResponse = {
  month: "2026-05",
  currency: "GBP",
  totalMinorUnits: 15000,
  categories: [
    { category: "GROCERIES", totalMinorUnits: 10000, transactionCount: 4 },
    { category: "EATING_OUT", totalMinorUnits: 5000, transactionCount: 2 },
  ],
};

describe("useSpendingBreakdown", () => {
  beforeEach(() => {
    getApiV1AccountHolderAllMock.mockReset();
    getApiV1AccountsByAccountUidSpendingMock.mockReset();
    useActiveHolderOverrideStore.setState({ override: null });
  });

  it("requests spending with the account, month and active holder header", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    getApiV1AccountsByAccountUidSpendingMock.mockResolvedValue({
      data: BREAKDOWN,
    });

    const { result } = renderHook(
      () => useSpendingBreakdown(ACCOUNT_UID, "2026-05"),
      { wrapper: wrapperOf(makeClient()) },
    );

    await waitFor(() => expect(result.current.breakdown).toEqual(BREAKDOWN));

    const firstArg = getApiV1AccountsByAccountUidSpendingMock.mock
      .calls[0]?.[0] as {
      path?: Record<string, string>;
      query?: Record<string, string>;
      headers?: Record<string, string>;
    };
    expect(firstArg.path).toMatchObject({ accountUid: ACCOUNT_UID });
    expect(firstArg.query).toMatchObject({ month: "2026-05" });
    expect(firstArg.headers).toMatchObject({
      "MAL-Account-Holder-Id": INDIVIDUAL_UID,
    });
  });

  it("flags a 503 as unavailable", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    getApiV1AccountsByAccountUidSpendingMock.mockRejectedValue(
      new HttpError(503, { message: "insights down" }),
    );

    const { result } = renderHook(
      () => useSpendingBreakdown(ACCOUNT_UID, "2026-05"),
      { wrapper: wrapperOf(makeClient()) },
    );

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.isUnavailable).toBe(true);
    expect(result.current.breakdown).toBeNull();
  });

  it("does not flag other errors as unavailable", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    getApiV1AccountsByAccountUidSpendingMock.mockRejectedValue(
      new HttpError(404, { message: "no account" }),
    );

    const { result } = renderHook(
      () => useSpendingBreakdown(ACCOUNT_UID, "2026-05"),
      { wrapper: wrapperOf(makeClient()) },
    );

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.isUnavailable).toBe(false);
  });
});
