import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { Query } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type { AccountHoldersResponse } from "@/api/generated/types.gen";
import {
  getApiV1AccountHolderAllQueryKey,
  getApiV1AccountsByAccountUidSpendingQueryKey,
  getApiV1AccountsByAccountUidTransactionsQueryKey,
} from "@/api/generated/@tanstack/react-query.gen";
import { useActiveHolderOverrideStore } from "@/account-holder/useActiveHolderOverrideStore";
import { useSetTransactionCategory } from "./useSetTransactionCategory";

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1AccountHolderAll: vi.fn(),
  putApiV1AccountsByAccountUidTransactionsByEntryIdCategory: vi.fn(),
  deleteApiV1AccountsByAccountUidTransactionsByEntryIdCategory: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getApiV1AccountHolderAllMock =
  sdk.getApiV1AccountHolderAll as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;
const putCategoryMock =
  sdk.putApiV1AccountsByAccountUidTransactionsByEntryIdCategory as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;
const deleteCategoryMock =
  sdk.deleteApiV1AccountsByAccountUidTransactionsByEntryIdCategory as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;

const toastSuccessMock = vi.fn();
vi.mock("sonner", () => ({
  toast: { success: (...args: unknown[]) => toastSuccessMock(...args) },
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

const ACCOUNT_UID = "22222222-2222-2222-2222-222222222222";
const ENTRY_ID = "99999999-9999-9999-9999-999999999999";
const INDIVIDUAL_UID = "11111111-1111-1111-1111-111111111111";
const HEADERS = { "MAL-Account-Holder-Id": INDIVIDUAL_UID };

const HOLDERS: AccountHoldersResponse = {
  accountHolders: [
    {
      accountHolderUid: INDIVIDUAL_UID,
      accountHolderName: "Ada Lovelace",
      accountHolderType: "INDIVIDUAL",
    },
  ],
};

async function waitForHolder(client: QueryClient): Promise<void> {
  await waitFor(() => {
    expect(
      client.getQueryData(getApiV1AccountHolderAllQueryKey()),
    ).toBeDefined();
  });
}

describe("useSetTransactionCategory", () => {
  beforeEach(() => {
    getApiV1AccountHolderAllMock.mockReset();
    putCategoryMock.mockReset();
    deleteCategoryMock.mockReset();
    toastSuccessMock.mockReset();
    useActiveHolderOverrideStore.setState({ override: null });
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
  });

  it("pins a category via PUT with no Idempotency-Key, then toasts and invalidates feed + breakdown", async () => {
    putCategoryMock.mockResolvedValue({ data: undefined });
    const client = makeClient();
    const invalidateSpy = vi.spyOn(client, "invalidateQueries");

    const { result } = renderHook(
      () => useSetTransactionCategory(ACCOUNT_UID),
      {
        wrapper: wrapperOf(client),
      },
    );
    await waitForHolder(client);

    result.current.setCategory(ENTRY_ID, "GROCERIES");

    await waitFor(() => {
      expect(toastSuccessMock).toHaveBeenCalledWith("Category updated.");
    });

    const call = putCategoryMock.mock.calls[0]?.[0] as {
      path?: Record<string, string>;
      body?: Record<string, string>;
      headers?: Record<string, string>;
    };
    expect(call.path).toMatchObject({
      accountUid: ACCOUNT_UID,
      entryId: ENTRY_ID,
    });
    expect(call.body).toEqual({ category: "GROCERIES" });
    expect(call.headers?.["MAL-Account-Holder-Id"]).toBe(INDIVIDUAL_UID);
    expect(call.headers?.["Idempotency-Key"]).toBeUndefined();

    const txKey = getApiV1AccountsByAccountUidTransactionsQueryKey({
      path: { accountUid: ACCOUNT_UID },
      headers: HEADERS,
    });
    expect(
      invalidateSpy.mock.calls.some(
        ([opts]) =>
          JSON.stringify(
            (opts as { queryKey?: unknown } | undefined)?.queryKey,
          ) === JSON.stringify(txKey),
      ),
    ).toBe(true);

    const predicateCall = invalidateSpy.mock.calls.find(
      ([opts]) =>
        typeof (opts as { predicate?: unknown } | undefined)?.predicate ===
        "function",
    );
    expect(predicateCall).toBeDefined();
    const predicate = (
      predicateCall?.[0] as {
        predicate: (query: Query) => boolean;
      }
    ).predicate;
    const spendingKey = getApiV1AccountsByAccountUidSpendingQueryKey({
      path: { accountUid: ACCOUNT_UID },
      query: { month: "2026-05" },
      headers: HEADERS,
    });
    expect(predicate({ queryKey: spendingKey } as unknown as Query)).toBe(true);
    const otherKey = getApiV1AccountsByAccountUidSpendingQueryKey({
      path: { accountUid: "different-account" },
      query: { month: "2026-05" },
      headers: HEADERS,
    });
    expect(predicate({ queryKey: otherKey } as unknown as Query)).toBe(false);
  });

  it("reverts to automatic via DELETE with no body or Idempotency-Key, then toasts", async () => {
    deleteCategoryMock.mockResolvedValue({ data: undefined });
    const client = makeClient();

    const { result } = renderHook(
      () => useSetTransactionCategory(ACCOUNT_UID),
      {
        wrapper: wrapperOf(client),
      },
    );
    await waitForHolder(client);

    result.current.clearCategory(ENTRY_ID);

    await waitFor(() => {
      expect(toastSuccessMock).toHaveBeenCalledWith(
        "Reverted to automatic categorisation.",
      );
    });

    const call = deleteCategoryMock.mock.calls[0]?.[0] as {
      path?: Record<string, string>;
      body?: unknown;
      headers?: Record<string, string>;
    };
    expect(call.path).toMatchObject({
      accountUid: ACCOUNT_UID,
      entryId: ENTRY_ID,
    });
    expect(call.body).toBeUndefined();
    expect(call.headers?.["MAL-Account-Holder-Id"]).toBe(INDIVIDUAL_UID);
    expect(call.headers?.["Idempotency-Key"]).toBeUndefined();
  });
});
