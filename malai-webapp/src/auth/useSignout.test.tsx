import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import {
  getApiV1AccountHolderAllQueryKey,
  getApiV1AccountsQueryKey,
  getApiV1UserQueryKey,
} from "@/api/generated/@tanstack/react-query.gen";
import { HttpError } from "@/api/http";
import { useBalanceVisibility } from "@/accounts/useBalanceVisibility";
import { useSignout } from "./useSignout";

vi.mock("@/api/generated/sdk.gen", () => ({
  postApiV1AuthSignout: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const postApiV1AuthSignoutMock =
  sdk.postApiV1AuthSignout as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;

const navigateMock = vi.fn();
vi.mock("react-router-dom", async () => {
  const actual =
    await vi.importActual<typeof import("react-router-dom")>(
      "react-router-dom",
    );
  return { ...actual, useNavigate: () => navigateMock };
});

function makeClient(): QueryClient {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
}

function wrapperOf(client: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
  Wrapper.displayName = "QueryClientRouterWrapper";
  return Wrapper;
}

const ACCOUNTS_HEADERS = {
  headers: { "MAL-Account-Holder-Id": "holder-uid" },
} as const;

describe("useSignout", () => {
  beforeEach(() => {
    postApiV1AuthSignoutMock.mockReset();
    navigateMock.mockReset();
    useBalanceVisibility.setState({ hidden: false });
  });

  it("clears the user, account-holder, and accounts queries on success", async () => {
    postApiV1AuthSignoutMock.mockResolvedValue({ data: undefined });
    const client = makeClient();

    client.setQueryData(getApiV1UserQueryKey(), { userId: "u" });
    client.setQueryData(getApiV1AccountHolderAllQueryKey(), {
      accountHolders: [],
    });
    client.setQueryData(getApiV1AccountsQueryKey(ACCOUNTS_HEADERS), {
      accounts: [],
    });

    const { result } = renderHook(() => useSignout(), {
      wrapper: wrapperOf(client),
    });

    result.current.signout();

    await waitFor(() => {
      expect(client.getQueryData(getApiV1UserQueryKey())).toBeUndefined();
    });
    expect(
      client.getQueryData(getApiV1AccountHolderAllQueryKey()),
    ).toBeUndefined();
    expect(
      client.getQueryData(getApiV1AccountsQueryKey(ACCOUNTS_HEADERS)),
    ).toBeUndefined();
  });

  it("resets the balance-visibility store to hidden = false on success", async () => {
    postApiV1AuthSignoutMock.mockResolvedValue({ data: undefined });
    useBalanceVisibility.setState({ hidden: true });
    const client = makeClient();

    const { result } = renderHook(() => useSignout(), {
      wrapper: wrapperOf(client),
    });

    result.current.signout();

    await waitFor(() => {
      expect(useBalanceVisibility.getState().hidden).toBe(false);
    });
  });

  it("navigates to /login after a successful signout", async () => {
    postApiV1AuthSignoutMock.mockResolvedValue({ data: undefined });
    const client = makeClient();

    const { result } = renderHook(() => useSignout(), {
      wrapper: wrapperOf(client),
    });

    result.current.signout();

    await waitFor(() => {
      expect(navigateMock).toHaveBeenCalledWith("/login", { replace: true });
    });
  });

  it("does not clear cached queries when the signout call fails", async () => {
    postApiV1AuthSignoutMock.mockRejectedValue(
      new HttpError(500, { errorCode: "INTERNAL_ERROR" }),
    );
    const client = makeClient();
    const userPayload = { userId: "u" };
    client.setQueryData(getApiV1UserQueryKey(), userPayload);

    const { result } = renderHook(() => useSignout(), {
      wrapper: wrapperOf(client),
    });

    result.current.signout();

    await waitFor(() => {
      expect(result.current.isPending).toBe(false);
    });
    expect(client.getQueryData(getApiV1UserQueryKey())).toEqual(userPayload);
    expect(navigateMock).not.toHaveBeenCalled();
  });
});
