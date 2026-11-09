import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type {
  AccountHoldersResponse,
  CardResponse,
} from "@/api/generated/types.gen";
import { HttpError } from "@/api/http";
import {
  getApiV1AccountHolderAllQueryKey,
  getApiV1CardsByCardIdQueryKey,
  getApiV1CardsQueryKey,
} from "@/api/generated/@tanstack/react-query.gen";
import { useActiveHolderOverrideStore } from "@/account-holder/useActiveHolderOverrideStore";
import { useFreezeCard } from "./useFreezeCard";

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1AccountHolderAll: vi.fn(),
  postApiV1CardsByCardIdFreeze: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getApiV1AccountHolderAllMock =
  sdk.getApiV1AccountHolderAll as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;
const freezeMock = sdk.postApiV1CardsByCardIdFreeze as unknown as ReturnType<
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

const FROZEN_CARD: CardResponse = {
  cardId: CARD_ID,
  accountId: "22222222-2222-2222-2222-222222222222",
  maskedPan: "************1234",
  variant: "DEBIT",
  status: "FROZEN",
  expiry: "10/29",
};

describe("useFreezeCard", () => {
  beforeEach(() => {
    getApiV1AccountHolderAllMock.mockReset();
    freezeMock.mockReset();
    toastSuccessMock.mockReset();
    toastErrorMock.mockReset();
    useActiveHolderOverrideStore.setState({ override: null });
  });

  it("posts the freeze with holder header and an Idempotency-Key, invalidates cards, toasts", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    freezeMock.mockResolvedValue({ data: FROZEN_CARD });
    const client = makeClient();
    const invalidateSpy = vi.spyOn(client, "invalidateQueries");

    const { result } = renderHook(() => useFreezeCard(), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);

    act(() => {
      result.current.freeze(CARD_ID);
    });

    await waitFor(() => {
      expect(toastSuccessMock).toHaveBeenCalledWith("Card frozen.");
    });

    const call = freezeMock.mock.calls[0]?.[0] as {
      path?: Record<string, string>;
      headers?: Record<string, string>;
    };
    expect(call.path).toMatchObject({ cardId: CARD_ID });
    expect(call.headers?.["MAL-Account-Holder-Id"]).toBe(INDIVIDUAL_UID);
    expect(call.headers?.["Idempotency-Key"]).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
    );

    const headers = { "MAL-Account-Holder-Id": INDIVIDUAL_UID };
    const expectedKeys = [
      getApiV1CardsQueryKey({ headers }),
      getApiV1CardsByCardIdQueryKey({ path: { cardId: CARD_ID }, headers }),
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

  it("on a 409 resyncs the cards list and fires the generic error toast", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    freezeMock.mockRejectedValue(new HttpError(409, { message: "6993" }));
    const client = makeClient();
    const invalidateSpy = vi.spyOn(client, "invalidateQueries");

    const { result } = renderHook(() => useFreezeCard(), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);

    act(() => {
      result.current.freeze(CARD_ID);
    });

    await waitFor(() => {
      expect(toastErrorMock).toHaveBeenCalledWith(
        "Something went wrong. Please try again.",
      );
    });
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
