import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type {
  AccountHoldersResponse,
  CreditSummaryResponse,
} from "@/api/generated/types.gen";
import { useActiveHolderOverrideStore } from "@/account-holder/useActiveHolderOverrideStore";
import { useCreditSummary } from "./useCreditSummary";

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1AccountHolderAll: vi.fn(),
  getApiV1AccountsByAccountUidCreditSummary: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getApiV1AccountHolderAllMock =
  sdk.getApiV1AccountHolderAll as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;
const getSummaryMock =
  sdk.getApiV1AccountsByAccountUidCreditSummary as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;

function makeClient(): QueryClient {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
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

const HOLDERS: AccountHoldersResponse = {
  accountHolders: [
    {
      accountHolderUid: INDIVIDUAL_UID,
      accountHolderName: "Ada Lovelace",
      accountHolderType: "INDIVIDUAL",
    },
  ],
};

const SUMMARY: CreditSummaryResponse = {
  currency: "GBP",
  creditLimitMinorUnits: 250000,
  owedMinorUnits: 40000,
  availableCreditMinorUnits: 210000,
  statementBalanceMinorUnits: 32500,
  minimumPaymentMinorUnits: 813,
  dueDate: "2026-08-21",
};

describe("useCreditSummary", () => {
  beforeEach(() => {
    getApiV1AccountHolderAllMock.mockReset();
    getSummaryMock.mockReset();
    useActiveHolderOverrideStore.setState({ override: null });
  });

  it("requests the credit summary with the account and holder header", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    getSummaryMock.mockResolvedValue({ data: SUMMARY });

    const { result } = renderHook(() => useCreditSummary(ACCOUNT_UID), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() => expect(result.current.summary).toEqual(SUMMARY));

    const firstArg = getSummaryMock.mock.calls[0]?.[0] as {
      path?: Record<string, string>;
      headers?: Record<string, string>;
    };
    expect(firstArg.path).toMatchObject({ accountUid: ACCOUNT_UID });
    expect(firstArg.headers).toMatchObject({
      "MAL-Account-Holder-Id": INDIVIDUAL_UID,
    });
  });

  it("does not query when the account uid is empty", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });

    const { result } = renderHook(() => useCreditSummary(""), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() => expect(result.current.isPending).toBe(false));
    expect(result.current.summary).toBeNull();
    expect(getSummaryMock).not.toHaveBeenCalled();
  });
});
