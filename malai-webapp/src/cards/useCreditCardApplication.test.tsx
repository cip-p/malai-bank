import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type {
  AccountHoldersResponse,
  CreditCardApplicationResponse,
} from "@/api/generated/types.gen";
import { HttpError } from "@/api/http";
import {
  getApiV1AccountHolderAllQueryKey,
  getApiV1AccountsQueryKey,
  getApiV1CardsQueryKey,
} from "@/api/generated/@tanstack/react-query.gen";
import { useActiveHolderOverrideStore } from "@/account-holder/useActiveHolderOverrideStore";
import { useCreditCardApplication } from "./useCreditCardApplication";

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1AccountHolderAll: vi.fn(),
  postApiV1CreditCardApplicationsByAccountHolderId: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getApiV1AccountHolderAllMock =
  sdk.getApiV1AccountHolderAll as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;
const applyMock =
  sdk.postApiV1CreditCardApplicationsByAccountHolderId as unknown as ReturnType<
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

async function waitForHolder(client: QueryClient): Promise<void> {
  await waitFor(() => {
    expect(
      client.getQueryData(getApiV1AccountHolderAllQueryKey()),
    ).toBeDefined();
  });
}

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

const HOLDERS: AccountHoldersResponse = {
  accountHolders: [
    {
      accountHolderUid: INDIVIDUAL_UID,
      accountHolderName: "Ada Lovelace",
      accountHolderType: "INDIVIDUAL",
    },
  ],
};

const APPROVED: CreditCardApplicationResponse = {
  account: {
    accountUid: "55555555-5555-5555-5555-555555555555",
    accountType: "CREDIT_CARD",
    defaultCategory: "66666666-6666-6666-6666-666666666666",
    currency: "GBP",
    createdAt: "2026-07-12T09:00:00Z",
    name: "Credit Card",
  },
  card: {
    cardId: "44444444-4444-4444-4444-444444444444",
    accountId: "55555555-5555-5555-5555-555555555555",
    maskedPan: "************9876",
    variant: "CREDIT",
    status: "ACTIVE",
    expiry: "11/30",
  },
};

describe("useCreditCardApplication", () => {
  beforeEach(() => {
    getApiV1AccountHolderAllMock.mockReset();
    applyMock.mockReset();
    toastSuccessMock.mockReset();
    toastErrorMock.mockReset();
    useActiveHolderOverrideStore.setState({ override: null });
  });

  it("posts to the holder's application path with an Idempotency-Key and invalidates accounts and cards", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    applyMock.mockResolvedValue({ data: APPROVED });
    const client = makeClient();
    const invalidateSpy = vi.spyOn(client, "invalidateQueries");
    const onSuccess = vi.fn();

    const { result } = renderHook(
      () => useCreditCardApplication({ onSuccess }),
      { wrapper: wrapperOf(client) },
    );
    await waitForHolder(client);

    act(() => {
      result.current.apply();
    });

    await waitFor(() => expect(onSuccess).toHaveBeenCalledWith(APPROVED));

    const call = applyMock.mock.calls[0]?.[0] as {
      path?: Record<string, string>;
      headers?: Record<string, string>;
    };
    expect(call.path).toMatchObject({ accountHolderId: INDIVIDUAL_UID });
    expect(call.headers?.["MAL-Account-Holder-Id"]).toBe(INDIVIDUAL_UID);
    expect(call.headers?.["Idempotency-Key"]).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
    );

    const headers = { "MAL-Account-Holder-Id": INDIVIDUAL_UID };
    const expectedKeys = [
      getApiV1AccountsQueryKey({ headers }),
      getApiV1CardsQueryKey({ headers }),
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

  it("flags alreadyExists on a 409 without firing the generic toast", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    applyMock.mockRejectedValue(new HttpError(409, { message: "6992" }));
    const client = makeClient();

    const { result } = renderHook(() => useCreditCardApplication(), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);

    act(() => {
      result.current.apply();
    });

    await waitFor(() => expect(result.current.alreadyExists).toBe(true));
    expect(toastErrorMock).not.toHaveBeenCalled();
  });

  it("fires the generic toast on other failures", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    applyMock.mockRejectedValue(new HttpError(500, { message: "boom" }));
    const client = makeClient();

    const { result } = renderHook(() => useCreditCardApplication(), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);

    act(() => {
      result.current.apply();
    });

    await waitFor(() => {
      expect(toastErrorMock).toHaveBeenCalledWith(
        "Something went wrong. Please try again.",
      );
    });
    expect(result.current.alreadyExists).toBe(false);
  });
});
