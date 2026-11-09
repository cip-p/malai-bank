import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type {
  AccountHoldersResponse,
  CardsResponse,
} from "@/api/generated/types.gen";
import { HttpError } from "@/api/http";
import { useActiveHolderOverrideStore } from "@/account-holder/useActiveHolderOverrideStore";
import { useCards } from "./useCards";

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1AccountHolderAll: vi.fn(),
  getApiV1Cards: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getApiV1AccountHolderAllMock =
  sdk.getApiV1AccountHolderAll as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;
const getApiV1CardsMock = sdk.getApiV1Cards as unknown as ReturnType<
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

const HOLDERS: AccountHoldersResponse = {
  accountHolders: [
    {
      accountHolderUid: INDIVIDUAL_UID,
      accountHolderName: "Ada Lovelace",
      accountHolderType: "INDIVIDUAL",
    },
  ],
};

const CARDS: CardsResponse = {
  cards: [
    {
      cardId: "33333333-3333-3333-3333-333333333333",
      accountId: "22222222-2222-2222-2222-222222222222",
      maskedPan: "************1234",
      variant: "DEBIT",
      status: "ACTIVE",
      expiry: "10/29",
    },
  ],
};

describe("useCards", () => {
  beforeEach(() => {
    getApiV1AccountHolderAllMock.mockReset();
    getApiV1CardsMock.mockReset();
    useActiveHolderOverrideStore.setState({ override: null });
  });

  it("requests the cards list with the active holder header", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    getApiV1CardsMock.mockResolvedValue({ data: CARDS });

    const { result } = renderHook(() => useCards(), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() => expect(result.current.cards).toEqual(CARDS.cards));

    const firstArg = getApiV1CardsMock.mock.calls[0]?.[0] as {
      headers?: Record<string, string>;
    };
    expect(firstArg.headers).toMatchObject({
      "MAL-Account-Holder-Id": INDIVIDUAL_UID,
    });
  });

  it("does not query until the active holder is ready", () => {
    getApiV1AccountHolderAllMock.mockImplementation(
      () => new Promise(() => {}),
    );

    const { result } = renderHook(() => useCards(), {
      wrapper: wrapperOf(makeClient()),
    });

    expect(result.current.isPending).toBe(true);
    expect(getApiV1CardsMock).not.toHaveBeenCalled();
  });

  it("surfaces errors", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    getApiV1CardsMock.mockRejectedValue(
      new HttpError(500, { message: "boom" }),
    );

    const { result } = renderHook(() => useCards(), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.cards).toBeNull();
  });
});
