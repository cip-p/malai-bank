import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type {
  AccountHoldersResponse,
  CardDetailResponse,
} from "@/api/generated/types.gen";
import { HttpError } from "@/api/http";
import { useActiveHolderOverrideStore } from "@/account-holder/useActiveHolderOverrideStore";
import { useCardReveal } from "./useCardReveal";

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1AccountHolderAll: vi.fn(),
  getApiV1CardsByCardId: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getApiV1AccountHolderAllMock =
  sdk.getApiV1AccountHolderAll as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;
const getCardMock = sdk.getApiV1CardsByCardId as unknown as ReturnType<
  typeof vi.fn<(...args: unknown[]) => unknown>
>;

function makeClient(): QueryClient {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
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

const DETAIL: CardDetailResponse = {
  cardId: CARD_ID,
  accountId: "22222222-2222-2222-2222-222222222222",
  cardholderName: "Ada Lovelace",
  pan: "9904320000012341",
  expiry: "10/29",
  cvv: "123",
  variant: "DEBIT",
  status: "ACTIVE",
};

describe("useCardReveal", () => {
  beforeEach(() => {
    getApiV1AccountHolderAllMock.mockReset();
    getCardMock.mockReset();
    useActiveHolderOverrideStore.setState({ override: null });
  });

  it("does not fetch while the reveal is off", () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });

    const { result } = renderHook(() => useCardReveal(CARD_ID, false), {
      wrapper: wrapperOf(makeClient()),
    });

    expect(result.current.detail).toBeNull();
    expect(result.current.isPending).toBe(false);
    expect(getCardMock).not.toHaveBeenCalled();
  });

  it("fetches the full card detail once revealed", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    getCardMock.mockResolvedValue({ data: DETAIL });

    const { result } = renderHook(() => useCardReveal(CARD_ID, true), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() => expect(result.current.detail).toEqual(DETAIL));

    const firstArg = getCardMock.mock.calls[0]?.[0] as {
      path?: Record<string, string>;
      headers?: Record<string, string>;
    };
    expect(firstArg.path).toMatchObject({ cardId: CARD_ID });
    expect(firstArg.headers).toMatchObject({
      "MAL-Account-Holder-Id": INDIVIDUAL_UID,
    });
  });

  it("surfaces reveal errors", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    getCardMock.mockRejectedValue(new HttpError(404, { message: "8993" }));

    const { result } = renderHook(() => useCardReveal(CARD_ID, true), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.detail).toBeNull();
  });
});
