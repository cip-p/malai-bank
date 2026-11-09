import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type {
  AccountHoldersResponse,
  CardResponse,
} from "@/api/generated/types.gen";
import { getApiV1AccountHolderAllQueryKey } from "@/api/generated/@tanstack/react-query.gen";
import { useActiveHolderOverrideStore } from "@/account-holder/useActiveHolderOverrideStore";
import { useUnfreezeCard } from "./useUnfreezeCard";

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1AccountHolderAll: vi.fn(),
  postApiV1CardsByCardIdUnfreeze: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getApiV1AccountHolderAllMock =
  sdk.getApiV1AccountHolderAll as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;
const unfreezeMock =
  sdk.postApiV1CardsByCardIdUnfreeze as unknown as ReturnType<
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

const ACTIVE_CARD: CardResponse = {
  cardId: CARD_ID,
  accountId: "22222222-2222-2222-2222-222222222222",
  maskedPan: "************1234",
  variant: "DEBIT",
  status: "ACTIVE",
  expiry: "10/29",
};

describe("useUnfreezeCard", () => {
  beforeEach(() => {
    getApiV1AccountHolderAllMock.mockReset();
    unfreezeMock.mockReset();
    toastSuccessMock.mockReset();
    toastErrorMock.mockReset();
    useActiveHolderOverrideStore.setState({ override: null });
  });

  it("posts the unfreeze with holder header and an Idempotency-Key and toasts", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    unfreezeMock.mockResolvedValue({ data: ACTIVE_CARD });
    const client = makeClient();

    const { result } = renderHook(() => useUnfreezeCard(), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);

    act(() => {
      result.current.unfreeze(CARD_ID);
    });

    await waitFor(() => {
      expect(toastSuccessMock).toHaveBeenCalledWith("Card unfrozen.");
    });

    const call = unfreezeMock.mock.calls[0]?.[0] as {
      path?: Record<string, string>;
      headers?: Record<string, string>;
    };
    expect(call.path).toMatchObject({ cardId: CARD_ID });
    expect(call.headers?.["MAL-Account-Holder-Id"]).toBe(INDIVIDUAL_UID);
    expect(call.headers?.["Idempotency-Key"]).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
    );
  });
});
