import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type {
  AccountHoldersResponse,
  PayeeResponse,
} from "@/api/generated/types.gen";
import { useActiveHolderOverrideStore } from "@/account-holder/useActiveHolderOverrideStore";
import { usePayees } from "./usePayees";

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1AccountHolderAll: vi.fn(),
  getApiV1Payees: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getApiV1AccountHolderAllMock =
  sdk.getApiV1AccountHolderAll as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;
const getApiV1PayeesMock = sdk.getApiV1Payees as unknown as ReturnType<
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

const PAYEE: PayeeResponse = {
  payeeId: "11111111-1111-1111-1111-111111111aaa",
  name: "Sarah Mitchell",
  sortCode: "12-34-56",
  accountNumber: "12345678",
  createdAt: "2026-05-19T10:00:00Z",
};

describe("usePayees", () => {
  beforeEach(() => {
    getApiV1AccountHolderAllMock.mockReset();
    getApiV1PayeesMock.mockReset();
    useActiveHolderOverrideStore.setState({ override: null });
  });

  it("calls /api/v1/payees with the active holder's UID in MAL-Account-Holder-Id", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    getApiV1PayeesMock.mockResolvedValue({ data: { payees: [PAYEE] } });

    const { result } = renderHook(() => usePayees(), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() => expect(result.current.payees).toEqual([PAYEE]));

    expect(getApiV1PayeesMock).toHaveBeenCalledTimes(1);
    const firstArg = getApiV1PayeesMock.mock.calls[0][0] as {
      headers?: Record<string, string>;
    };
    expect(firstArg.headers).toMatchObject({
      "MAL-Account-Holder-Id": INDIVIDUAL_UID,
    });
  });

  it("waits for the active holder before firing the payees query", () => {
    getApiV1AccountHolderAllMock.mockImplementation(
      () => new Promise(() => {}),
    );

    const { result } = renderHook(() => usePayees(), {
      wrapper: wrapperOf(makeClient()),
    });

    expect(result.current.isPending).toBe(true);
    expect(result.current.payees).toBeNull();
    expect(getApiV1PayeesMock).not.toHaveBeenCalled();
  });

  it("returns isError when the payees request fails", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    getApiV1PayeesMock.mockRejectedValue(new Error("boom"));

    const { result } = renderHook(() => usePayees(), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.payees).toBeNull();
  });
});
