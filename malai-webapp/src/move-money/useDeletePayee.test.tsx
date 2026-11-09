import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type {
  AccountHoldersResponse,
  PayeeResponse,
  PayeesResponse,
} from "@/api/generated/types.gen";
import { HttpError } from "@/api/http";
import {
  getApiV1AccountHolderAllQueryKey,
  getApiV1PayeesQueryKey,
} from "@/api/generated/@tanstack/react-query.gen";
import { useActiveHolderOverrideStore } from "@/account-holder/useActiveHolderOverrideStore";
import { useDeletePayee } from "./useDeletePayee";

async function waitForHolder(client: QueryClient): Promise<void> {
  await waitFor(() => {
    expect(
      client.getQueryData(getApiV1AccountHolderAllQueryKey()),
    ).toBeDefined();
  });
}

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1AccountHolderAll: vi.fn(),
  deleteApiV1PayeesByPayeeId: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getApiV1AccountHolderAllMock =
  sdk.getApiV1AccountHolderAll as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;
const deleteApiV1PayeesByPayeeIdMock =
  sdk.deleteApiV1PayeesByPayeeId as unknown as ReturnType<
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

const SARAH: PayeeResponse = {
  payeeId: "22222222-2222-2222-2222-222222222222",
  name: "Sarah Mitchell",
  sortCode: "12-34-56",
  accountNumber: "12345678",
  createdAt: "2026-05-19T10:00:00Z",
};

const THAMES: PayeeResponse = {
  payeeId: "33333333-3333-3333-3333-333333333333",
  name: "Thames Water",
  sortCode: "98-76-54",
  accountNumber: "87654321",
  createdAt: "2026-05-18T10:00:00Z",
};

function seedPayees(client: QueryClient, payees: PayeeResponse[]): void {
  client.setQueryData<PayeesResponse>(
    getApiV1PayeesQueryKey({
      headers: { "MAL-Account-Holder-Id": INDIVIDUAL_UID },
    }),
    { payees },
  );
}

function readPayees(client: QueryClient): PayeeResponse[] | undefined {
  const data = client.getQueryData<PayeesResponse>(
    getApiV1PayeesQueryKey({
      headers: { "MAL-Account-Holder-Id": INDIVIDUAL_UID },
    }),
  );
  return data?.payees;
}

describe("useDeletePayee", () => {
  beforeEach(() => {
    getApiV1AccountHolderAllMock.mockReset();
    deleteApiV1PayeesByPayeeIdMock.mockReset();
    toastSuccessMock.mockReset();
    toastErrorMock.mockReset();
    useActiveHolderOverrideStore.setState({ override: null });
  });

  it("on 204 success removes the payee from the cache, toasts, and invalidates", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    deleteApiV1PayeesByPayeeIdMock.mockResolvedValue({ data: undefined });
    const client = makeClient();
    seedPayees(client, [SARAH, THAMES]);
    const invalidateSpy = vi.spyOn(client, "invalidateQueries");

    const { result } = renderHook(() => useDeletePayee(), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);

    result.current.deletePayee(SARAH.payeeId);

    expect(readPayees(client)?.map((p) => p.payeeId)).toEqual([THAMES.payeeId]);

    await waitFor(() => {
      expect(toastSuccessMock).toHaveBeenCalledWith("Payee removed.");
    });
    const payeesKey = getApiV1PayeesQueryKey({
      headers: { "MAL-Account-Holder-Id": INDIVIDUAL_UID },
    });
    expect(
      invalidateSpy.mock.calls.some(([opts]) => {
        const k = (opts as { queryKey?: unknown } | undefined)?.queryKey;
        return JSON.stringify(k) === JSON.stringify(payeesKey);
      }),
    ).toBe(true);
  });

  it("sends MAL-Account-Holder-Id and the payeeId in the path", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    deleteApiV1PayeesByPayeeIdMock.mockResolvedValue({ data: undefined });
    const client = makeClient();
    seedPayees(client, [SARAH]);

    const { result } = renderHook(() => useDeletePayee(), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);

    result.current.deletePayee(SARAH.payeeId);
    await waitFor(() => {
      expect(deleteApiV1PayeesByPayeeIdMock).toHaveBeenCalledTimes(1);
    });
    const call = deleteApiV1PayeesByPayeeIdMock.mock.calls[0]?.[0] as {
      path?: { payeeId?: string };
      headers?: Record<string, string>;
    };
    expect(call.path?.payeeId).toBe(SARAH.payeeId);
    expect(call.headers?.["MAL-Account-Holder-Id"]).toBe(INDIVIDUAL_UID);
  });

  it("treats 404 as success — no error toast, optimistic remove stands", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    deleteApiV1PayeesByPayeeIdMock.mockRejectedValue(
      new HttpError(404, { message: "Payee not found" }),
    );
    const client = makeClient();
    seedPayees(client, [SARAH, THAMES]);

    const { result } = renderHook(() => useDeletePayee(), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);

    result.current.deletePayee(SARAH.payeeId);
    await waitFor(() => {
      expect(toastSuccessMock).toHaveBeenCalledWith("Payee removed.");
    });
    expect(toastErrorMock).not.toHaveBeenCalled();
    expect(readPayees(client)?.map((p) => p.payeeId)).toEqual([THAMES.payeeId]);
  });

  it("on other 4xx reverts the optimistic remove and falls through to the generic toast", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    deleteApiV1PayeesByPayeeIdMock.mockRejectedValue(
      new HttpError(409, { message: "Conflict" }),
    );
    const client = makeClient();
    seedPayees(client, [SARAH, THAMES]);

    const { result } = renderHook(() => useDeletePayee(), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);

    result.current.deletePayee(SARAH.payeeId);
    expect(readPayees(client)?.map((p) => p.payeeId)).toEqual([THAMES.payeeId]);

    await waitFor(() => {
      expect(result.current.isPending).toBe(false);
    });
    expect(readPayees(client)?.map((p) => p.payeeId)).toEqual([
      SARAH.payeeId,
      THAMES.payeeId,
    ]);
    expect(toastSuccessMock).not.toHaveBeenCalled();
    expect(toastErrorMock).toHaveBeenCalledWith(
      "Something went wrong. Please try again.",
    );
  });

  it("on a 409 PAYEE_HAS_ACTIVE_STANDING_ORDER reverts and shows the cancel-the-standing-order message", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    deleteApiV1PayeesByPayeeIdMock.mockRejectedValue(
      new HttpError(409, { message: "6994" }),
    );
    const client = makeClient();
    seedPayees(client, [SARAH, THAMES]);

    const { result } = renderHook(() => useDeletePayee(), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);

    result.current.deletePayee(SARAH.payeeId);
    expect(readPayees(client)?.map((p) => p.payeeId)).toEqual([THAMES.payeeId]);

    await waitFor(() => {
      expect(result.current.isPending).toBe(false);
    });
    expect(readPayees(client)?.map((p) => p.payeeId)).toEqual([
      SARAH.payeeId,
      THAMES.payeeId,
    ]);
    expect(toastSuccessMock).not.toHaveBeenCalled();
    expect(toastErrorMock).toHaveBeenCalledWith(
      "Cancel the standing order that pays this payee before removing them.",
    );
  });
});
