import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type {
  AccountResponse,
  AccountHoldersResponse,
} from "@/api/generated/types.gen";
import { getApiV1AccountsQueryKey } from "@/api/generated/@tanstack/react-query.gen";
import { useActiveHolderOverrideStore } from "@/account-holder/useActiveHolderOverrideStore";
import { useAccount } from "./useAccount";

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1AccountHolderAll: vi.fn(),
  getApiV1Accounts: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getApiV1AccountHolderAllMock =
  sdk.getApiV1AccountHolderAll as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;
const getApiV1AccountsMock = sdk.getApiV1Accounts as unknown as ReturnType<
  typeof vi.fn<(...args: { headers?: Record<string, string> }[]) => unknown>
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

const HOLDERS: AccountHoldersResponse = {
  accountHolders: [
    {
      accountHolderUid: INDIVIDUAL_UID,
      accountHolderName: "Ada Lovelace",
      accountHolderType: "INDIVIDUAL",
    },
  ],
};

const MAIN: AccountResponse = {
  accountUid: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  accountType: "CURRENT",
  defaultCategory: "PERSONAL",
  currency: "GBP",
  createdAt: "2024-01-01T00:00:00Z",
  name: "Main",
};

const SAVINGS: AccountResponse = {
  accountUid: "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
  accountType: "SAVINGS",
  defaultCategory: "PERSONAL",
  currency: "GBP",
  createdAt: "2024-01-02T00:00:00Z",
  name: "Savings",
};

const UNKNOWN_UID = "00000000-0000-0000-0000-000000000000";

function primeAccountsCache(client: QueryClient, accounts: AccountResponse[]) {
  const queryKey = getApiV1AccountsQueryKey({
    headers: { "MAL-Account-Holder-Id": INDIVIDUAL_UID },
  });
  client.setQueryData(queryKey, { accounts });
}

describe("useAccount", () => {
  beforeEach(() => {
    getApiV1AccountHolderAllMock.mockReset();
    getApiV1AccountsMock.mockReset();
    useActiveHolderOverrideStore.setState({ override: null });
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
  });

  it("returns status 'cache-hit' synchronously when the accounts list is primed", async () => {
    const client = makeClient();
    client.setQueryData(
      [
        {
          _id: "getApiV1AccountHolderAll",
          baseUrl: "http://localhost:8080",
        },
      ],
      HOLDERS,
    );
    primeAccountsCache(client, [MAIN, SAVINGS]);

    const { result } = renderHook(() => useAccount(MAIN.accountUid), {
      wrapper: wrapperOf(client),
    });

    await waitFor(() => expect(result.current.status).toBe("cache-hit"));
    expect(result.current.account).toEqual(MAIN);
    expect(getApiV1AccountsMock).not.toHaveBeenCalled();
  });

  it("falls back to a fresh query and resolves with status 'ready' when no cache", async () => {
    getApiV1AccountsMock.mockResolvedValue({
      data: { accounts: [MAIN, SAVINGS] },
    });

    const { result } = renderHook(() => useAccount(MAIN.accountUid), {
      wrapper: wrapperOf(makeClient()),
    });

    expect(result.current.status).toBe("fetching");
    expect(result.current.account).toBeNull();

    await waitFor(() => expect(result.current.status).toBe("ready"));
    expect(result.current.account).toEqual(MAIN);
    expect(getApiV1AccountsMock).toHaveBeenCalledTimes(1);
  });

  it("resolves with status 'not-found' when the fresh list omits the uid", async () => {
    getApiV1AccountsMock.mockResolvedValue({
      data: { accounts: [MAIN] },
    });

    const { result } = renderHook(() => useAccount(UNKNOWN_UID), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() => expect(result.current.status).toBe("not-found"));
    expect(result.current.account).toBeNull();
  });

  it("injects MAL-Account-Holder-Id when firing the fresh query", async () => {
    getApiV1AccountsMock.mockResolvedValue({
      data: { accounts: [MAIN] },
    });

    const { result } = renderHook(() => useAccount(MAIN.accountUid), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() => expect(result.current.status).toBe("ready"));
    const firstArg = getApiV1AccountsMock.mock.calls[0][0] as {
      headers?: Record<string, string>;
    };
    expect(firstArg.headers).toMatchObject({
      "MAL-Account-Holder-Id": INDIVIDUAL_UID,
    });
  });
});
