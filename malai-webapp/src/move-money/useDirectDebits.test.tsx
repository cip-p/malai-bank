import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type {
  AccountHoldersResponse,
  DirectDebitResponse,
} from "@/api/generated/types.gen";
import { useActiveHolderOverrideStore } from "@/account-holder/useActiveHolderOverrideStore";
import { useDirectDebits } from "./useDirectDebits";

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1AccountHolderAll: vi.fn(),
  getApiV1DirectDebits: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getApiV1AccountHolderAllMock =
  sdk.getApiV1AccountHolderAll as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;
const getApiV1DirectDebitsMock =
  sdk.getApiV1DirectDebits as unknown as ReturnType<
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

const DD: DirectDebitResponse = {
  directDebitId: "dd-1",
  accountId: "acc-1",
  creditorName: "Thames Water",
  creditorSortCode: "12-34-56",
  creditorAccountNumber: "12345678",
  currency: "GBP",
  frequency: "MONTHLY",
  status: "ACTIVE",
  startDate: "2026-01-15",
};

describe("useDirectDebits", () => {
  beforeEach(() => {
    getApiV1AccountHolderAllMock.mockReset();
    getApiV1DirectDebitsMock.mockReset();
    useActiveHolderOverrideStore.setState({ override: null });
  });

  it("calls /api/v1/direct-debits with the active holder's UID in MAL-Account-Holder-Id", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    getApiV1DirectDebitsMock.mockResolvedValue({
      data: { directDebits: [DD] },
    });

    const { result } = renderHook(() => useDirectDebits(), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() => expect(result.current.directDebits).toEqual([DD]));

    expect(getApiV1DirectDebitsMock).toHaveBeenCalledTimes(1);
    const firstArg = getApiV1DirectDebitsMock.mock.calls[0][0] as {
      headers?: Record<string, string>;
    };
    expect(firstArg.headers).toMatchObject({
      "MAL-Account-Holder-Id": INDIVIDUAL_UID,
    });
  });

  it("waits for the active holder before firing the direct-debits query", () => {
    getApiV1AccountHolderAllMock.mockImplementation(
      () => new Promise(() => {}),
    );

    const { result } = renderHook(() => useDirectDebits(), {
      wrapper: wrapperOf(makeClient()),
    });

    expect(result.current.isPending).toBe(true);
    expect(result.current.directDebits).toBeNull();
    expect(getApiV1DirectDebitsMock).not.toHaveBeenCalled();
  });

  it("returns isError when the direct-debits request fails", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    getApiV1DirectDebitsMock.mockRejectedValue(new Error("boom"));

    const { result } = renderHook(() => useDirectDebits(), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.directDebits).toBeNull();
  });
});
