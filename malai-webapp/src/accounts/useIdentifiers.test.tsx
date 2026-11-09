import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type {
  AccountIdentifiersResponse,
  AccountHoldersResponse,
} from "@/api/generated/types.gen";
import { useActiveHolderOverrideStore } from "@/account-holder/useActiveHolderOverrideStore";
import { useIdentifiers } from "./useIdentifiers";

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1AccountHolderAll: vi.fn(),
  getApiV1AccountsByAccountUidIdentifiers: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getApiV1AccountHolderAllMock =
  sdk.getApiV1AccountHolderAll as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;
const getApiV1AccountsByAccountUidIdentifiersMock =
  sdk.getApiV1AccountsByAccountUidIdentifiers as unknown as ReturnType<
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

const IDENTIFIERS: AccountIdentifiersResponse = {
  accountIdentifier: "12345678",
  bankIdentifier: "12-34-56",
  accountIdentifiers: [
    {
      identifierType: "UK_DOMESTIC",
      bankIdentifier: "12-34-56",
      accountIdentifier: "12345678",
    },
  ],
};

describe("useIdentifiers", () => {
  beforeEach(() => {
    getApiV1AccountHolderAllMock.mockReset();
    getApiV1AccountsByAccountUidIdentifiersMock.mockReset();
    useActiveHolderOverrideStore.setState({ override: null });
  });

  it("calls /api/v1/accounts/{uid}/identifiers with the active holder header", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    getApiV1AccountsByAccountUidIdentifiersMock.mockResolvedValue({
      data: IDENTIFIERS,
    });

    const { result } = renderHook(() => useIdentifiers(ACCOUNT_UID), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() =>
      expect(result.current.identifiers).toEqual(IDENTIFIERS),
    );

    expect(getApiV1AccountsByAccountUidIdentifiersMock).toHaveBeenCalledTimes(
      1,
    );
    const firstArg = getApiV1AccountsByAccountUidIdentifiersMock.mock
      .calls[0][0] as {
      path?: Record<string, string>;
      headers?: Record<string, string>;
    };
    expect(firstArg.path).toMatchObject({ accountUid: ACCOUNT_UID });
    expect(firstArg.headers).toMatchObject({
      "MAL-Account-Holder-Id": INDIVIDUAL_UID,
    });
  });

  it("waits for the active holder before firing the identifiers query", () => {
    getApiV1AccountHolderAllMock.mockImplementation(
      () => new Promise(() => {}),
    );

    const { result } = renderHook(() => useIdentifiers(ACCOUNT_UID), {
      wrapper: wrapperOf(makeClient()),
    });

    expect(result.current.isPending).toBe(true);
    expect(result.current.identifiers).toBeNull();
    expect(getApiV1AccountsByAccountUidIdentifiersMock).not.toHaveBeenCalled();
  });

  it("does not fire the identifiers query when accountUid is empty", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });

    const { result } = renderHook(() => useIdentifiers(""), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() => expect(result.current.isPending).toBe(false));
    expect(result.current.identifiers).toBeNull();
    expect(result.current.isError).toBe(false);
    expect(getApiV1AccountsByAccountUidIdentifiersMock).not.toHaveBeenCalled();
  });
});
