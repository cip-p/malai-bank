import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type {
  AccountHoldersResponse,
  StandingOrderCreateRequest,
  StandingOrderResponse,
} from "@/api/generated/types.gen";
import { HttpError } from "@/api/http";
import {
  getApiV1AccountHolderAllQueryKey,
  getApiV1StandingOrdersQueryKey,
} from "@/api/generated/@tanstack/react-query.gen";
import { useActiveHolderOverrideStore } from "@/account-holder/useActiveHolderOverrideStore";
import { useCreateStandingOrder } from "./useCreateStandingOrder";

async function waitForHolder(client: QueryClient): Promise<void> {
  await waitFor(() => {
    expect(
      client.getQueryData(getApiV1AccountHolderAllQueryKey()),
    ).toBeDefined();
  });
}

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1AccountHolderAll: vi.fn(),
  postApiV1StandingOrders: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getApiV1AccountHolderAllMock =
  sdk.getApiV1AccountHolderAll as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;
const postApiV1StandingOrdersMock =
  sdk.postApiV1StandingOrders as unknown as ReturnType<
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

const toastSuccessMock = vi.fn();
vi.mock("sonner", () => ({
  toast: {
    success: (...args: unknown[]) => toastSuccessMock(...args),
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

const REQUEST: StandingOrderCreateRequest = {
  accountId: "33333333-3333-3333-3333-333333333333",
  payeeId: "44444444-4444-4444-4444-444444444444",
  amountMinorUnits: 2500,
  currency: "GBP",
  reference: "Rent",
  frequency: "MONTHLY",
  startDate: "2026-08-01",
};

const RESPONSE: StandingOrderResponse = {
  standingOrderId: "55555555-5555-5555-5555-555555555555",
  accountId: "33333333-3333-3333-3333-333333333333",
  payeeId: "44444444-4444-4444-4444-444444444444",
  payeeName: "Sarah Mitchell",
  payeeSortCode: "12-34-56",
  payeeAccountNumber: "12345678",
  reference: "Rent",
  amountMinorUnits: 2500,
  currency: "GBP",
  frequency: "MONTHLY",
  status: "ACTIVE",
  startDate: "2026-08-01",
  nextPaymentAt: "2026-08-01T00:00:00Z",
};

describe("useCreateStandingOrder", () => {
  beforeEach(() => {
    getApiV1AccountHolderAllMock.mockReset();
    postApiV1StandingOrdersMock.mockReset();
    navigateMock.mockReset();
    toastSuccessMock.mockReset();
    useActiveHolderOverrideStore.setState({ override: null });
  });

  it("on success invalidates the standing-orders query, toasts, and navigates to /move/standing-orders", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    postApiV1StandingOrdersMock.mockResolvedValue({ data: RESPONSE });
    const client = makeClient();
    const invalidateSpy = vi.spyOn(client, "invalidateQueries");

    const { result } = renderHook(() => useCreateStandingOrder(), {
      wrapper: wrapperOf(client),
    });

    await waitForHolder(client);
    result.current.createStandingOrder(REQUEST);

    await waitFor(() => {
      expect(toastSuccessMock).toHaveBeenCalledWith("Standing order created.");
    });
    expect(navigateMock).toHaveBeenCalledWith("/move/standing-orders");
    const soKey = getApiV1StandingOrdersQueryKey({
      headers: { "MAL-Account-Holder-Id": INDIVIDUAL_UID },
    });
    expect(
      invalidateSpy.mock.calls.some(([opts]) => {
        const k = (opts as { queryKey?: unknown } | undefined)?.queryKey;
        return JSON.stringify(k) === JSON.stringify(soKey);
      }),
    ).toBe(true);
  });

  it("sends the body, MAL-Account-Holder-Id and an Idempotency-Key on the request", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    postApiV1StandingOrdersMock.mockResolvedValue({ data: RESPONSE });
    const client = makeClient();

    const { result } = renderHook(() => useCreateStandingOrder(), {
      wrapper: wrapperOf(client),
    });

    await waitForHolder(client);
    result.current.createStandingOrder(REQUEST);

    await waitFor(() => {
      expect(postApiV1StandingOrdersMock).toHaveBeenCalledTimes(1);
    });
    const firstCall = postApiV1StandingOrdersMock.mock.calls[0]?.[0] as {
      body?: StandingOrderCreateRequest;
      headers?: Record<string, string>;
    };
    expect(firstCall.body).toEqual(REQUEST);
    expect(firstCall.headers?.["MAL-Account-Holder-Id"]).toBe(INDIVIDUAL_UID);
    expect(firstCall.headers?.["Idempotency-Key"]).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
    );
  });

  it("mints a fresh Idempotency-Key on every submit attempt", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    postApiV1StandingOrdersMock.mockResolvedValue({ data: RESPONSE });
    const client = makeClient();

    const { result } = renderHook(() => useCreateStandingOrder(), {
      wrapper: wrapperOf(client),
    });

    await waitForHolder(client);

    result.current.createStandingOrder(REQUEST);
    await waitFor(() => {
      expect(postApiV1StandingOrdersMock).toHaveBeenCalledTimes(1);
    });

    result.current.createStandingOrder(REQUEST);
    await waitFor(() => {
      expect(postApiV1StandingOrdersMock).toHaveBeenCalledTimes(2);
    });

    const keyA = (
      postApiV1StandingOrdersMock.mock.calls[0]?.[0] as {
        headers?: Record<string, string>;
      }
    ).headers?.["Idempotency-Key"];
    const keyB = (
      postApiV1StandingOrdersMock.mock.calls[1]?.[0] as {
        headers?: Record<string, string>;
      }
    ).headers?.["Idempotency-Key"];
    expect(keyA).toBeDefined();
    expect(keyB).toBeDefined();
    expect(keyA).not.toBe(keyB);
  });

  it("with navigateOnSuccess: false, invalidates and toasts but does not navigate", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    postApiV1StandingOrdersMock.mockResolvedValue({ data: RESPONSE });
    const client = makeClient();

    const onSuccessSpy = vi.fn();
    const { result } = renderHook(
      () =>
        useCreateStandingOrder({
          navigateOnSuccess: false,
          onSuccess: onSuccessSpy,
        }),
      { wrapper: wrapperOf(client) },
    );

    await waitForHolder(client);
    result.current.createStandingOrder(REQUEST);

    await waitFor(() => {
      expect(onSuccessSpy).toHaveBeenCalledWith(RESPONSE);
    });
    expect(toastSuccessMock).toHaveBeenCalledWith("Standing order created.");
    expect(navigateMock).not.toHaveBeenCalled();
  });

  it("on 4xx does not toast, does not navigate, and does not invalidate", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    postApiV1StandingOrdersMock.mockRejectedValue(
      new HttpError(400, { errorCode: "7972" }),
    );
    const client = makeClient();
    const invalidateSpy = vi.spyOn(client, "invalidateQueries");

    const { result } = renderHook(() => useCreateStandingOrder(), {
      wrapper: wrapperOf(client),
    });

    await waitForHolder(client);
    result.current.createStandingOrder(REQUEST);

    await waitFor(() => {
      expect(result.current.isPending).toBe(false);
    });
    expect(toastSuccessMock).not.toHaveBeenCalled();
    expect(navigateMock).not.toHaveBeenCalled();
    const soKey = getApiV1StandingOrdersQueryKey({
      headers: { "MAL-Account-Holder-Id": INDIVIDUAL_UID },
    });
    expect(
      invalidateSpy.mock.calls.some(([opts]) => {
        const k = (opts as { queryKey?: unknown } | undefined)?.queryKey;
        return JSON.stringify(k) === JSON.stringify(soKey);
      }),
    ).toBe(false);
  });
});
