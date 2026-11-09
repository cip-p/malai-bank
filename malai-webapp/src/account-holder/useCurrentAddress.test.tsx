import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type {
  AddressesResponse,
  AccountHoldersResponse,
} from "@/api/generated/types.gen";
import { useActiveHolderOverrideStore } from "@/account-holder/useActiveHolderOverrideStore";
import { useCurrentAddress } from "./useCurrentAddress";

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1AccountHolderAll: vi.fn(),
  getApiV1Addresses: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getApiV1AccountHolderAllMock =
  sdk.getApiV1AccountHolderAll as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;
const getApiV1AddressesMock = sdk.getApiV1Addresses as unknown as ReturnType<
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

const ADDRESSES: AddressesResponse = {
  current: {
    line1: "12 Maple Grove",
    postTown: "Bristol",
    postCode: "BS1 4QR",
    countryCode: "GB",
  },
  previous: [],
};

describe("useCurrentAddress", () => {
  beforeEach(() => {
    getApiV1AccountHolderAllMock.mockReset();
    getApiV1AddressesMock.mockReset();
    useActiveHolderOverrideStore.setState({ override: null });
  });

  it("calls /api/v1/addresses with the active holder header and returns the current address", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    getApiV1AddressesMock.mockResolvedValue({ data: ADDRESSES });

    const { result } = renderHook(() => useCurrentAddress(), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() =>
      expect(result.current.address).toEqual(ADDRESSES.current),
    );

    expect(getApiV1AddressesMock).toHaveBeenCalledTimes(1);
    const firstArg = getApiV1AddressesMock.mock.calls[0][0] as {
      headers?: Record<string, string>;
    };
    expect(firstArg.headers).toMatchObject({
      "MAL-Account-Holder-Id": INDIVIDUAL_UID,
    });
  });

  it("waits for the active holder before firing the addresses query", () => {
    getApiV1AccountHolderAllMock.mockImplementation(
      () => new Promise(() => {}),
    );

    const { result } = renderHook(() => useCurrentAddress(), {
      wrapper: wrapperOf(makeClient()),
    });

    expect(result.current.isPending).toBe(true);
    expect(result.current.address).toBeNull();
    expect(getApiV1AddressesMock).not.toHaveBeenCalled();
  });

  it("returns a null address when the response has no current address", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    getApiV1AddressesMock.mockResolvedValue({ data: { previous: [] } });

    const { result } = renderHook(() => useCurrentAddress(), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() => expect(result.current.isPending).toBe(false));
    expect(result.current.address).toBeNull();
    expect(result.current.isError).toBe(false);
  });
});
