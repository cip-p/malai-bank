import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor, act } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type { AccountHoldersResponse } from "@/api/generated/types.gen";
import { HttpError } from "@/api/http";
import {
  getApiV1AccountHolderAllQueryKey,
  getApiV1AccountsByAccountUidTransactionsQueryKey,
  getApiV1AccountsQueryKey,
} from "@/api/generated/@tanstack/react-query.gen";
import { useActiveHolderOverrideStore } from "@/account-holder/useActiveHolderOverrideStore";
import { useInboundPaymentMutation } from "./useInboundPaymentMutation";

async function waitForHolder(client: QueryClient): Promise<void> {
  await waitFor(() => {
    expect(
      client.getQueryData(getApiV1AccountHolderAllQueryKey()),
    ).toBeDefined();
  });
}

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1AccountHolderAll: vi.fn(),
  postApiV1SandboxInboundPayments: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getApiV1AccountHolderAllMock =
  sdk.getApiV1AccountHolderAll as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;
const postInboundMock =
  sdk.postApiV1SandboxInboundPayments as unknown as ReturnType<
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
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
  Wrapper.displayName = "QueryClientWrapper";
  return Wrapper;
}

const INDIVIDUAL_UID = "11111111-1111-1111-1111-111111111111";
const CREDITED_UID = "22222222-2222-2222-2222-222222222222";

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
  rail: "FPS" as const,
  creditedAccountId: CREDITED_UID,
  originatorSortCode: "12-34-56",
  originatorAccountNumber: "12345678",
  originatorName: "Sarah Mitchell",
  amountMinorUnits: 2500,
  reference: "RENT MARCH",
};

describe("useInboundPaymentMutation", () => {
  beforeEach(() => {
    getApiV1AccountHolderAllMock.mockReset();
    postInboundMock.mockReset();
    toastSuccessMock.mockReset();
    toastErrorMock.mockReset();
    useActiveHolderOverrideStore.setState({ override: null });
  });

  it("on success invalidates accounts and credited-account transactions; toasts; calls onSuccess", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    postInboundMock.mockResolvedValue({ data: undefined });
    const client = makeClient();
    const invalidateSpy = vi.spyOn(client, "invalidateQueries");
    const onSuccess = vi.fn();

    const { result } = renderHook(
      () => useInboundPaymentMutation({ onSuccess }),
      { wrapper: wrapperOf(client) },
    );
    await waitForHolder(client);

    act(() => {
      result.current.submit(REQUEST);
    });

    await waitFor(() => {
      expect(toastSuccessMock).toHaveBeenCalledWith(
        "Inbound payment received.",
      );
    });
    expect(onSuccess).toHaveBeenCalledTimes(1);

    const headers = { "MAL-Account-Holder-Id": INDIVIDUAL_UID };
    const expectedKeys = [
      getApiV1AccountsQueryKey({ headers }),
      getApiV1AccountsByAccountUidTransactionsQueryKey({
        path: { accountUid: CREDITED_UID },
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
    postInboundMock.mockResolvedValue({ data: undefined });
    const client = makeClient();

    const { result } = renderHook(() => useInboundPaymentMutation(), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);
    act(() => {
      result.current.submit(REQUEST);
    });

    await waitFor(() => {
      expect(postInboundMock).toHaveBeenCalledTimes(1);
    });
    const call = postInboundMock.mock.calls[0]?.[0] as {
      body?: Record<string, unknown>;
      headers?: Record<string, string>;
    };
    expect(call.body).toEqual({
      rail: "FPS",
      creditedAccountId: CREDITED_UID,
      originatorSortCode: "12-34-56",
      originatorAccountNumber: "12345678",
      originatorName: "Sarah Mitchell",
      amountMinorUnits: 2500,
      reference: "RENT MARCH",
    });
    expect(call.headers?.["MAL-Account-Holder-Id"]).toBe(INDIVIDUAL_UID);
    expect(call.headers?.["Idempotency-Key"]).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
    );
  });

  it("omits reference from the body when empty or not provided", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    postInboundMock.mockResolvedValue({ data: undefined });
    const client = makeClient();

    const { result } = renderHook(() => useInboundPaymentMutation(), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);
    act(() => {
      result.current.submit({ ...REQUEST, reference: "" });
    });

    await waitFor(() => {
      expect(postInboundMock).toHaveBeenCalledTimes(1);
    });
    const call = postInboundMock.mock.calls[0]?.[0] as {
      body?: Record<string, unknown>;
    };
    expect(call.body).not.toHaveProperty("reference");
  });

  it("on failure fires the generic error toast", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    postInboundMock.mockRejectedValue(new HttpError(400, { message: "9999" }));
    const client = makeClient();

    const { result } = renderHook(() => useInboundPaymentMutation(), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);
    act(() => {
      result.current.submit(REQUEST);
    });

    await waitFor(() => {
      expect(toastErrorMock).toHaveBeenCalledWith(
        "Something went wrong. Please try again.",
      );
    });
    expect(toastSuccessMock).not.toHaveBeenCalled();
  });

  it("on 401 does not toast (handled globally by the auth layer)", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    postInboundMock.mockRejectedValue(new HttpError(401, { message: "0001" }));
    const client = makeClient();

    const { result } = renderHook(() => useInboundPaymentMutation(), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);
    act(() => {
      result.current.submit(REQUEST);
    });

    await waitFor(() => {
      expect(postInboundMock).toHaveBeenCalledTimes(1);
    });
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(toastErrorMock).not.toHaveBeenCalled();
    expect(toastSuccessMock).not.toHaveBeenCalled();
  });

  it("mints a fresh Idempotency-Key on every submit attempt", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    postInboundMock.mockResolvedValue({ data: undefined });
    const client = makeClient();

    const { result } = renderHook(() => useInboundPaymentMutation(), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);

    act(() => {
      result.current.submit(REQUEST);
    });
    await waitFor(() => {
      expect(postInboundMock).toHaveBeenCalledTimes(1);
    });
    act(() => {
      result.current.submit(REQUEST);
    });
    await waitFor(() => {
      expect(postInboundMock).toHaveBeenCalledTimes(2);
    });

    const keyA = (
      postInboundMock.mock.calls[0]?.[0] as {
        headers?: Record<string, string>;
      }
    ).headers?.["Idempotency-Key"];
    const keyB = (
      postInboundMock.mock.calls[1]?.[0] as {
        headers?: Record<string, string>;
      }
    ).headers?.["Idempotency-Key"];
    expect(keyA).toBeDefined();
    expect(keyB).toBeDefined();
    expect(keyA).not.toBe(keyB);
  });

  it("reports isPending=true while the mutation is in-flight", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    let resolveCall: ((value: unknown) => void) | null = null;
    postInboundMock.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveCall = resolve;
        }),
    );
    const client = makeClient();

    const { result } = renderHook(() => useInboundPaymentMutation(), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);

    act(() => {
      result.current.submit(REQUEST);
    });
    await waitFor(() => {
      expect(result.current.isPending).toBe(true);
    });

    act(() => {
      resolveCall?.({ data: undefined });
    });
    await waitFor(() => {
      expect(result.current.isPending).toBe(false);
    });
  });
});
