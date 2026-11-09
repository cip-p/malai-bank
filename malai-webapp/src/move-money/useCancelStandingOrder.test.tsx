import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type {
  AccountHoldersResponse,
  StandingOrderResponse,
  StandingOrdersResponse,
} from "@/api/generated/types.gen";
import { HttpError } from "@/api/http";
import {
  getApiV1AccountHolderAllQueryKey,
  getApiV1StandingOrdersQueryKey,
} from "@/api/generated/@tanstack/react-query.gen";
import { useActiveHolderOverrideStore } from "@/account-holder/useActiveHolderOverrideStore";
import { useCancelStandingOrder } from "./useCancelStandingOrder";

async function waitForHolder(client: QueryClient): Promise<void> {
  await waitFor(() => {
    expect(
      client.getQueryData(getApiV1AccountHolderAllQueryKey()),
    ).toBeDefined();
  });
}

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1AccountHolderAll: vi.fn(),
  deleteApiV1StandingOrdersByStandingOrderId: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getApiV1AccountHolderAllMock =
  sdk.getApiV1AccountHolderAll as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;
const deleteStandingOrderMock =
  sdk.deleteApiV1StandingOrdersByStandingOrderId as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;

const toastSuccessMock = vi.fn();
const toastErrorMock = vi.fn();
vi.mock("sonner", () => ({
  toast: {
    success: (...args: unknown[]) => toastSuccessMock(...args),
    error: (...args: unknown[]) => toastErrorMock(...args),
  },
}));

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

const RENT: StandingOrderResponse = {
  standingOrderId: "22222222-2222-2222-2222-222222222222",
  accountId: "acc-1",
  payeeName: "Landlord",
  payeeSortCode: "12-34-56",
  payeeAccountNumber: "12345678",
  amountMinorUnits: 95_000,
  currency: "GBP",
  frequency: "MONTHLY",
  status: "ACTIVE",
  startDate: "2026-01-15",
  nextPaymentAt: "2026-06-15T08:00:00Z",
};

const GYM: StandingOrderResponse = {
  standingOrderId: "33333333-3333-3333-3333-333333333333",
  accountId: "acc-1",
  payeeName: "Gym",
  payeeSortCode: "98-76-54",
  payeeAccountNumber: "87654321",
  amountMinorUnits: 3_000,
  currency: "GBP",
  frequency: "MONTHLY",
  status: "ACTIVE",
  startDate: "2026-02-01",
  nextPaymentAt: "2026-06-01T08:00:00Z",
};

function standingOrdersKey() {
  return getApiV1StandingOrdersQueryKey({
    headers: { "MAL-Account-Holder-Id": INDIVIDUAL_UID },
  });
}

function seedStandingOrders(
  client: QueryClient,
  standingOrders: StandingOrderResponse[],
): void {
  client.setQueryData<StandingOrdersResponse>(standingOrdersKey(), {
    standingOrders,
  });
}

function readStatuses(client: QueryClient): string[] | undefined {
  const data = client.getQueryData<StandingOrdersResponse>(standingOrdersKey());
  return data?.standingOrders.map((so) => so.status);
}

describe("useCancelStandingOrder", () => {
  beforeEach(() => {
    getApiV1AccountHolderAllMock.mockReset();
    deleteStandingOrderMock.mockReset();
    toastSuccessMock.mockReset();
    toastErrorMock.mockReset();
    useActiveHolderOverrideStore.setState({ override: null });
  });

  it("on 204 success optimistically marks the order CANCELLED, toasts, and invalidates", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    deleteStandingOrderMock.mockResolvedValue({ data: undefined });
    const client = makeClient();
    seedStandingOrders(client, [RENT, GYM]);
    const invalidateSpy = vi.spyOn(client, "invalidateQueries");

    const { result } = renderHook(() => useCancelStandingOrder(), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);

    result.current.cancelStandingOrder(RENT.standingOrderId);

    expect(readStatuses(client)).toEqual(["CANCELLED", "ACTIVE"]);

    await waitFor(() => {
      expect(toastSuccessMock).toHaveBeenCalledWith(
        "Standing order cancelled.",
      );
    });
    expect(
      invalidateSpy.mock.calls.some(([opts]) => {
        const k = (opts as { queryKey?: unknown } | undefined)?.queryKey;
        return JSON.stringify(k) === JSON.stringify(standingOrdersKey());
      }),
    ).toBe(true);
  });

  it("sends MAL-Account-Holder-Id and the standingOrderId in the path", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    deleteStandingOrderMock.mockResolvedValue({ data: undefined });
    const client = makeClient();
    seedStandingOrders(client, [RENT]);

    const { result } = renderHook(() => useCancelStandingOrder(), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);

    result.current.cancelStandingOrder(RENT.standingOrderId);
    await waitFor(() => {
      expect(deleteStandingOrderMock).toHaveBeenCalledTimes(1);
    });
    const call = deleteStandingOrderMock.mock.calls[0]?.[0] as {
      path?: { standingOrderId?: string };
      headers?: Record<string, string>;
    };
    expect(call.path?.standingOrderId).toBe(RENT.standingOrderId);
    expect(call.headers?.["MAL-Account-Holder-Id"]).toBe(INDIVIDUAL_UID);
  });

  it("treats 404 as success — no error toast, optimistic cancel stands", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    deleteStandingOrderMock.mockRejectedValue(
      new HttpError(404, { message: "Standing order not found" }),
    );
    const client = makeClient();
    seedStandingOrders(client, [RENT, GYM]);

    const { result } = renderHook(() => useCancelStandingOrder(), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);

    result.current.cancelStandingOrder(RENT.standingOrderId);
    await waitFor(() => {
      expect(toastSuccessMock).toHaveBeenCalledWith(
        "Standing order cancelled.",
      );
    });
    expect(toastErrorMock).not.toHaveBeenCalled();
    expect(readStatuses(client)).toEqual(["CANCELLED", "ACTIVE"]);
  });

  it("on other 4xx reverts the optimistic cancel and shows the generic toast", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    deleteStandingOrderMock.mockRejectedValue(
      new HttpError(409, { message: "Conflict" }),
    );
    const client = makeClient();
    seedStandingOrders(client, [RENT, GYM]);

    const { result } = renderHook(() => useCancelStandingOrder(), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);

    result.current.cancelStandingOrder(RENT.standingOrderId);
    expect(readStatuses(client)).toEqual(["CANCELLED", "ACTIVE"]);

    await waitFor(() => {
      expect(result.current.isPending).toBe(false);
    });
    expect(readStatuses(client)).toEqual(["ACTIVE", "ACTIVE"]);
    expect(toastSuccessMock).not.toHaveBeenCalled();
    expect(toastErrorMock).toHaveBeenCalledWith(
      "Something went wrong. Please try again.",
    );
  });
});
