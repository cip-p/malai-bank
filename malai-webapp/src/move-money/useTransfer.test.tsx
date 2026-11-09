import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor, act } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type {
  AccountHoldersResponse,
  PaymentResponse,
} from "@/api/generated/types.gen";
import { HttpError } from "@/api/http";
import {
  getApiV1AccountHolderAllQueryKey,
  getApiV1AccountsByAccountUidBalanceQueryKey,
  getApiV1AccountsByAccountUidTransactionsQueryKey,
  getApiV1AccountsQueryKey,
} from "@/api/generated/@tanstack/react-query.gen";
import { useActiveHolderOverrideStore } from "@/account-holder/useActiveHolderOverrideStore";
import { useTransfer } from "./useTransfer";

async function waitForHolder(client: QueryClient): Promise<void> {
  await waitFor(() => {
    expect(
      client.getQueryData(getApiV1AccountHolderAllQueryKey()),
    ).toBeDefined();
  });
}

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1AccountHolderAll: vi.fn(),
  postApiV1Payments: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getApiV1AccountHolderAllMock =
  sdk.getApiV1AccountHolderAll as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;
const postPaymentMock = sdk.postApiV1Payments as unknown as ReturnType<
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
const SOURCE_UID = "33333333-3333-3333-3333-333333333333";

const HOLDERS: AccountHoldersResponse = {
  accountHolders: [
    {
      accountHolderUid: INDIVIDUAL_UID,
      accountHolderName: "Ada Lovelace",
      accountHolderType: "INDIVIDUAL",
    },
  ],
};

const REQUEST = {
  fromAccountUid: SOURCE_UID,
  toSortCode: "12-34-56",
  toAccountNumber: "12345678",
  amountMinorUnits: 1234,
  description: "Coffee",
} as const;

const RESPONSE: PaymentResponse = {
  paymentId: "44444444-4444-4444-4444-444444444444",
  state: "SETTLED",
  rail: "BOOK_TRANSFER",
  direction: "OUTBOUND",
  createdAt: "2026-05-20T10:00:00Z",
  endToEndId: "e2e-66666666",
  fromAccountId: SOURCE_UID,
  toAccountId: "55555555-5555-5555-5555-555555555555",
  amountMinorUnits: 1234,
  currency: "GBP",
};

describe("useTransfer", () => {
  beforeEach(() => {
    getApiV1AccountHolderAllMock.mockReset();
    postPaymentMock.mockReset();
    toastSuccessMock.mockReset();
    toastErrorMock.mockReset();
    useActiveHolderOverrideStore.setState({ override: null });
  });

  it("on success invalidates accounts, source balance and source transactions; toasts; calls onSuccess", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    postPaymentMock.mockResolvedValue({ data: RESPONSE });
    const client = makeClient();
    const invalidateSpy = vi.spyOn(client, "invalidateQueries");
    const onSuccess = vi.fn();

    const { result } = renderHook(() => useTransfer({ onSuccess }), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);

    act(() => {
      result.current.transfer(REQUEST);
    });

    await waitFor(() => {
      expect(toastSuccessMock).toHaveBeenCalledWith("Transfer sent.");
    });
    expect(onSuccess).toHaveBeenCalledWith(RESPONSE);

    const headers = { "MAL-Account-Holder-Id": INDIVIDUAL_UID };
    const expectedKeys = [
      getApiV1AccountsQueryKey({ headers }),
      getApiV1AccountsByAccountUidBalanceQueryKey({
        path: { accountUid: SOURCE_UID },
        headers,
      }),
      getApiV1AccountsByAccountUidTransactionsQueryKey({
        path: { accountUid: SOURCE_UID },
        headers,
      }),
    ];
    for (const expected of expectedKeys) {
      expect(
        invalidateSpy.mock.calls.some(([opts]) => {
          const k = (opts as { queryKey?: unknown } | undefined)?.queryKey;
          return JSON.stringify(k) === JSON.stringify(expected);
        }),
      ).toBe(true);
    }
  });

  it("sends MAL-Account-Holder-Id and an Idempotency-Key on the request", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    postPaymentMock.mockResolvedValue({ data: RESPONSE });
    const client = makeClient();

    const { result } = renderHook(() => useTransfer(), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);
    act(() => {
      result.current.transfer(REQUEST);
    });

    await waitFor(() => {
      expect(postPaymentMock).toHaveBeenCalledTimes(1);
    });
    const call = postPaymentMock.mock.calls[0]?.[0] as {
      body?: Record<string, unknown>;
      headers?: Record<string, string>;
    };
    expect(call.body).toEqual({
      fromAccountId: SOURCE_UID,
      toSortCode: "12-34-56",
      toAccountNumber: "12345678",
      amountMinorUnits: 1234,
      currency: "GBP",
      description: "Coffee",
    });
    expect(call.headers?.["MAL-Account-Holder-Id"]).toBe(INDIVIDUAL_UID);
    expect(call.headers?.["Idempotency-Key"]).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
    );
  });

  it("sends toName so pre-dispatch screening sees who the money is going to", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    postPaymentMock.mockResolvedValue({ data: RESPONSE });
    const client = makeClient();

    const { result } = renderHook(() => useTransfer(), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);
    act(() => {
      result.current.transfer({ ...REQUEST, toName: "Carol Singh" });
    });

    await waitFor(() => {
      expect(postPaymentMock).toHaveBeenCalledTimes(1);
    });
    const call = postPaymentMock.mock.calls[0]?.[0] as {
      body?: Record<string, unknown>;
    };
    expect(call.body?.toName).toBe("Carol Singh");
  });

  it("omits toName from the body when there is no name to send", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    postPaymentMock.mockResolvedValue({ data: RESPONSE });
    const client = makeClient();

    const { result } = renderHook(() => useTransfer(), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);
    act(() => {
      result.current.transfer({ ...REQUEST, toName: "   " });
    });

    await waitFor(() => {
      expect(postPaymentMock).toHaveBeenCalledTimes(1);
    });
    const call = postPaymentMock.mock.calls[0]?.[0] as {
      body?: Record<string, unknown>;
    };
    expect(call.body).not.toHaveProperty("toName");
  });

  it("omits description from the body when not provided", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    postPaymentMock.mockResolvedValue({ data: RESPONSE });
    const client = makeClient();

    const { result } = renderHook(() => useTransfer(), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);
    act(() => {
      result.current.transfer({
        fromAccountUid: SOURCE_UID,
        toSortCode: "12-34-56",
        toAccountNumber: "12345678",
        amountMinorUnits: 1234,
      });
    });

    await waitFor(() => {
      expect(postPaymentMock).toHaveBeenCalledTimes(1);
    });
    const call = postPaymentMock.mock.calls[0]?.[0] as {
      body?: Record<string, unknown>;
    };
    expect(call.body).toEqual({
      fromAccountId: SOURCE_UID,
      toSortCode: "12-34-56",
      toAccountNumber: "12345678",
      amountMinorUnits: 1234,
      currency: "GBP",
    });
  });

  it("includes copVerdictOverride in the body when the caller pressed through CoP", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    postPaymentMock.mockResolvedValue({ data: RESPONSE });
    const client = makeClient();

    const { result } = renderHook(() => useTransfer(), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);
    act(() => {
      result.current.transfer({ ...REQUEST, copVerdictOverride: true });
    });

    await waitFor(() => {
      expect(postPaymentMock).toHaveBeenCalledTimes(1);
    });
    const call = postPaymentMock.mock.calls[0]?.[0] as {
      body?: Record<string, unknown>;
    };
    expect(call.body?.copVerdictOverride).toBe(true);
  });

  it("omits copVerdictOverride from the body when not pressed through", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    postPaymentMock.mockResolvedValue({ data: RESPONSE });
    const client = makeClient();

    const { result } = renderHook(() => useTransfer(), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);
    act(() => {
      result.current.transfer({ ...REQUEST, copVerdictOverride: false });
    });

    await waitFor(() => {
      expect(postPaymentMock).toHaveBeenCalledTimes(1);
    });
    const call = postPaymentMock.mock.calls[0]?.[0] as {
      body?: Record<string, unknown>;
    };
    expect(call.body).not.toHaveProperty("copVerdictOverride");
  });

  it("does not toast success for a payment born PENDING; still calls onSuccess", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    const pending: PaymentResponse = {
      ...RESPONSE,
      state: "PENDING",
      rail: "FPS",
    };
    postPaymentMock.mockResolvedValue({ data: pending });
    const client = makeClient();
    const onSuccess = vi.fn();

    const { result } = renderHook(() => useTransfer({ onSuccess }), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);
    act(() => {
      result.current.transfer(REQUEST);
    });

    await waitFor(() => {
      expect(onSuccess).toHaveBeenCalledWith(pending);
    });
    expect(toastSuccessMock).not.toHaveBeenCalled();
  });

  it("on 400 insufficient-funds sets insufficientFunds=true and does NOT toast", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    postPaymentMock.mockRejectedValue(new HttpError(400, { message: "7983" }));
    const client = makeClient();

    const { result } = renderHook(() => useTransfer(), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);
    act(() => {
      result.current.transfer(REQUEST);
    });

    await waitFor(() => {
      expect(result.current.insufficientFunds).toBe(true);
    });
    expect(toastErrorMock).not.toHaveBeenCalled();
    expect(toastSuccessMock).not.toHaveBeenCalled();
  });

  it("on other 400 fires generic toast and leaves insufficientFunds=false", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    postPaymentMock.mockRejectedValue(new HttpError(400, { message: "9999" }));
    const client = makeClient();

    const { result } = renderHook(() => useTransfer(), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);
    act(() => {
      result.current.transfer(REQUEST);
    });

    await waitFor(() => {
      expect(toastErrorMock).toHaveBeenCalledTimes(1);
    });
    expect(result.current.insufficientFunds).toBe(false);
  });

  it("on 403 fires generic toast and leaves insufficientFunds=false", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    postPaymentMock.mockRejectedValue(new HttpError(403, { message: "0001" }));
    const client = makeClient();

    const { result } = renderHook(() => useTransfer(), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);
    act(() => {
      result.current.transfer(REQUEST);
    });

    await waitFor(() => {
      expect(toastErrorMock).toHaveBeenCalledTimes(1);
    });
    expect(result.current.insufficientFunds).toBe(false);
  });

  it("reset() clears insufficientFunds back to false", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    postPaymentMock.mockRejectedValue(new HttpError(400, { message: "7983" }));
    const client = makeClient();

    const { result } = renderHook(() => useTransfer(), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);
    act(() => {
      result.current.transfer(REQUEST);
    });
    await waitFor(() => {
      expect(result.current.insufficientFunds).toBe(true);
    });

    act(() => {
      result.current.reset();
    });
    expect(result.current.insufficientFunds).toBe(false);
  });

  it("mints a fresh Idempotency-Key on every submit attempt", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    postPaymentMock.mockResolvedValue({ data: RESPONSE });
    const client = makeClient();

    const { result } = renderHook(() => useTransfer(), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);

    act(() => {
      result.current.transfer(REQUEST);
    });
    await waitFor(() => {
      expect(postPaymentMock).toHaveBeenCalledTimes(1);
    });
    act(() => {
      result.current.transfer(REQUEST);
    });
    await waitFor(() => {
      expect(postPaymentMock).toHaveBeenCalledTimes(2);
    });

    const keyA = (
      postPaymentMock.mock.calls[0]?.[0] as {
        headers?: Record<string, string>;
      }
    ).headers?.["Idempotency-Key"];
    const keyB = (
      postPaymentMock.mock.calls[1]?.[0] as {
        headers?: Record<string, string>;
      }
    ).headers?.["Idempotency-Key"];
    expect(keyA).toBeDefined();
    expect(keyB).toBeDefined();
    expect(keyA).not.toBe(keyB);
  });
});
