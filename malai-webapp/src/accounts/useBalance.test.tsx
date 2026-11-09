import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type {
  BalanceResponse,
  AccountHoldersResponse,
} from "@/api/generated/types.gen";
import { useActiveHolderOverrideStore } from "@/account-holder/useActiveHolderOverrideStore";
import { useBalance } from "./useBalance";

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1AccountHolderAll: vi.fn(),
  getApiV1AccountsByAccountUidBalance: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getApiV1AccountHolderAllMock =
  sdk.getApiV1AccountHolderAll as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;
const getApiV1AccountsByAccountUidBalanceMock =
  sdk.getApiV1AccountsByAccountUidBalance as unknown as ReturnType<
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

const BALANCE: BalanceResponse = {
  currency: "GBP",
  amountMinorUnits: 123456,
  availableBalanceMinorUnits: 123456,
  heldAmountMinorUnits: 0,
};

describe("useBalance", () => {
  beforeEach(() => {
    getApiV1AccountHolderAllMock.mockReset();
    getApiV1AccountsByAccountUidBalanceMock.mockReset();
    useActiveHolderOverrideStore.setState({ override: null });
  });

  it("calls /api/v1/accounts/{uid}/balance with the active holder header", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    getApiV1AccountsByAccountUidBalanceMock.mockResolvedValue({
      data: BALANCE,
    });

    const { result } = renderHook(() => useBalance(ACCOUNT_UID), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() => expect(result.current.balance).toEqual(BALANCE));

    expect(getApiV1AccountsByAccountUidBalanceMock).toHaveBeenCalledTimes(1);
    const firstArg = getApiV1AccountsByAccountUidBalanceMock.mock
      .calls[0][0] as {
      path?: Record<string, string>;
      headers?: Record<string, string>;
    };
    expect(firstArg.path).toMatchObject({ accountUid: ACCOUNT_UID });
    expect(firstArg.headers).toMatchObject({
      "MAL-Account-Holder-Id": INDIVIDUAL_UID,
    });
  });

  it("waits for the active holder before firing the balance query", () => {
    getApiV1AccountHolderAllMock.mockImplementation(
      () => new Promise(() => {}),
    );

    const { result } = renderHook(() => useBalance(ACCOUNT_UID), {
      wrapper: wrapperOf(makeClient()),
    });

    expect(result.current.isPending).toBe(true);
    expect(result.current.balance).toBeNull();
    expect(getApiV1AccountsByAccountUidBalanceMock).not.toHaveBeenCalled();
  });
});
