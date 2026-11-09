import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type {
  AccountHoldersResponse,
  CardReplacementResponse,
} from "@/api/generated/types.gen";
import {
  getApiV1AccountHolderAllQueryKey,
  getApiV1CardsQueryKey,
} from "@/api/generated/@tanstack/react-query.gen";
import { useActiveHolderOverrideStore } from "@/account-holder/useActiveHolderOverrideStore";
import { useReplaceCard } from "./useReplaceCard";

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1AccountHolderAll: vi.fn(),
  postApiV1CardsByCardIdReportLostOrStolen: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getApiV1AccountHolderAllMock =
  sdk.getApiV1AccountHolderAll as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;
const replaceMock =
  sdk.postApiV1CardsByCardIdReportLostOrStolen as unknown as ReturnType<
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
const CARD_ID = "33333333-3333-3333-3333-333333333333";

const HOLDERS: AccountHoldersResponse = {
  accountHolders: [
    {
      accountHolderUid: INDIVIDUAL_UID,
      accountHolderName: "Ada Lovelace",
      accountHolderType: "INDIVIDUAL",
    },
  ],
};

const REPLACEMENT: CardReplacementResponse = {
  cancelledCardId: CARD_ID,
  replacementCard: {
    cardId: "44444444-4444-4444-4444-444444444444",
    accountId: "22222222-2222-2222-2222-222222222222",
    maskedPan: "************9876",
    variant: "DEBIT",
    status: "ACTIVE",
    expiry: "11/30",
  },
};

describe("useReplaceCard", () => {
  beforeEach(() => {
    getApiV1AccountHolderAllMock.mockReset();
    replaceMock.mockReset();
    toastSuccessMock.mockReset();
    toastErrorMock.mockReset();
    useActiveHolderOverrideStore.setState({ override: null });
  });

  it("posts the report, invalidates the cards list, toasts, and hands back the replacement", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    replaceMock.mockResolvedValue({ data: REPLACEMENT });
    const client = makeClient();
    const invalidateSpy = vi.spyOn(client, "invalidateQueries");
    const onSuccess = vi.fn();

    const { result } = renderHook(() => useReplaceCard({ onSuccess }), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);

    act(() => {
      result.current.replace(CARD_ID);
    });

    await waitFor(() => {
      expect(toastSuccessMock).toHaveBeenCalledWith("Replacement card issued.");
    });
    expect(onSuccess).toHaveBeenCalledWith(REPLACEMENT);

    const call = replaceMock.mock.calls[0]?.[0] as {
      path?: Record<string, string>;
      headers?: Record<string, string>;
    };
    expect(call.path).toMatchObject({ cardId: CARD_ID });
    expect(call.headers?.["Idempotency-Key"]).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
    );

    const headers = { "MAL-Account-Holder-Id": INDIVIDUAL_UID };
    const cardsKey = getApiV1CardsQueryKey({ headers });
    expect(
      invalidateSpy.mock.calls.some(([opts]) => {
        const k = (opts as { queryKey?: unknown } | undefined)?.queryKey;
        return JSON.stringify(k) === JSON.stringify(cardsKey);
      }),
    ).toBe(true);
  });
});
