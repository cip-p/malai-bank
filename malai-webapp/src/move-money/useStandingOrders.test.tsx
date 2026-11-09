import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type {
  AccountHoldersResponse,
  StandingOrderResponse,
} from "@/api/generated/types.gen";
import { useActiveHolderOverrideStore } from "@/account-holder/useActiveHolderOverrideStore";
import { useStandingOrders } from "./useStandingOrders";

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1AccountHolderAll: vi.fn(),
  getApiV1StandingOrders: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getApiV1AccountHolderAllMock =
  sdk.getApiV1AccountHolderAll as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;
const getApiV1StandingOrdersMock =
  sdk.getApiV1StandingOrders as unknown as ReturnType<
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

const INDIVIDUAL_UID = "22222222-2222-2222-2222-222222222222";

const HOLDERS: AccountHoldersResponse = {
  accountHolders: [
    {
      accountHolderUid: INDIVIDUAL_UID,
      accountHolderName: "Ada Lovelace",
      accountHolderType: "INDIVIDUAL",
    },
  ],
};

const SO: StandingOrderResponse = {
  standingOrderId: "so-1",
  accountId: "acc-1",
  payeeName: "Landlord",
  payeeSortCode: "12-34-56",
  payeeAccountNumber: "87654321",
  amountMinorUnits: 150_000,
  currency: "GBP",
  frequency: "MONTHLY",
  status: "ACTIVE",
  startDate: "2026-01-15",
};

describe("useStandingOrders", () => {
  beforeEach(() => {
    getApiV1AccountHolderAllMock.mockReset();
    getApiV1StandingOrdersMock.mockReset();
    useActiveHolderOverrideStore.setState({ override: null });
  });

  it("calls /api/v1/standing-orders with the active holder's UID in MAL-Account-Holder-Id", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    getApiV1StandingOrdersMock.mockResolvedValue({
      data: { standingOrders: [SO] },
    });

    const { result } = renderHook(() => useStandingOrders(), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() => expect(result.current.standingOrders).toEqual([SO]));

    expect(getApiV1StandingOrdersMock).toHaveBeenCalledTimes(1);
    const firstArg = getApiV1StandingOrdersMock.mock.calls[0][0] as {
      headers?: Record<string, string>;
    };
    expect(firstArg.headers).toMatchObject({
      "MAL-Account-Holder-Id": INDIVIDUAL_UID,
    });
  });

  it("waits for the active holder before firing the standing-orders query", () => {
    getApiV1AccountHolderAllMock.mockImplementation(
      () => new Promise(() => {}),
    );

    const { result } = renderHook(() => useStandingOrders(), {
      wrapper: wrapperOf(makeClient()),
    });

    expect(result.current.isPending).toBe(true);
    expect(result.current.standingOrders).toBeNull();
    expect(getApiV1StandingOrdersMock).not.toHaveBeenCalled();
  });

  it("returns isError when the standing-orders request fails", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    getApiV1StandingOrdersMock.mockRejectedValue(new Error("boom"));

    const { result } = renderHook(() => useStandingOrders(), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.standingOrders).toBeNull();
  });
});
