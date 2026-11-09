import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type {
  AccountHoldersResponse,
  PaymentResponse,
} from "@/api/generated/types.gen";
import {
  getApiV1AccountHolderAllQueryKey,
  getApiV1AccountsByAccountUidBalanceQueryKey,
  getApiV1AccountsByAccountUidTransactionsQueryKey,
  getApiV1AccountsQueryKey,
} from "@/api/generated/@tanstack/react-query.gen";
import { useActiveHolderOverrideStore } from "@/account-holder/useActiveHolderOverrideStore";
import { usePaymentStatus } from "./usePaymentStatus";

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1AccountHolderAll: vi.fn(),
  getApiV1PaymentsByPaymentId: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getApiV1AccountHolderAllMock =
  sdk.getApiV1AccountHolderAll as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;
const getPaymentMock = sdk.getApiV1PaymentsByPaymentId as unknown as ReturnType<
  typeof vi.fn<(...args: unknown[]) => unknown>
>;

async function waitForHolder(client: QueryClient): Promise<void> {
  await waitFor(() => {
    expect(
      client.getQueryData(getApiV1AccountHolderAllQueryKey()),
    ).toBeDefined();
  });
}

function makeClient(): QueryClient {
  return new QueryClient({
    defaultOptions: { queries: { retry: false } },
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
const PAYMENT_ID = "44444444-4444-4444-4444-444444444444";

const HOLDERS: AccountHoldersResponse = {
  accountHolders: [
    {
      accountHolderUid: INDIVIDUAL_UID,
      accountHolderName: "Ada Lovelace",
      accountHolderType: "INDIVIDUAL",
    },
  ],
};

const PENDING: PaymentResponse = {
  paymentId: PAYMENT_ID,
  state: "PENDING",
  rail: "FPS",
  direction: "OUTBOUND",
  createdAt: "2026-05-20T10:00:00Z",
  endToEndId: "55555555-5555-5555-5555-555555555555",
  fromAccountId: SOURCE_UID,
  amountMinorUnits: 1234,
  currency: "GBP",
};

const SETTLED: PaymentResponse = { ...PENDING, state: "SETTLED" };
const RETURNED: PaymentResponse = {
  ...PENDING,
  state: "RETURNED",
  returnReason: "ACCOUNT_CLOSED",
};
const REFERRED: PaymentResponse = { ...PENDING, state: "REFERRED" };
const DECLINED: PaymentResponse = { ...PENDING, state: "DECLINED" };

describe("usePaymentStatus", () => {
  beforeEach(() => {
    getApiV1AccountHolderAllMock.mockReset();
    getPaymentMock.mockReset();
    useActiveHolderOverrideStore.setState({ override: null });
  });

  it("returns the initial payment without fetching when it is already terminal", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    const client = makeClient();

    const { result } = renderHook(() => usePaymentStatus(SETTLED), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);

    expect(result.current.payment).toEqual(SETTLED);
    expect(getPaymentMock).not.toHaveBeenCalled();
  });

  it("polls a PENDING payment until it settles and then invalidates account money data", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    getPaymentMock.mockResolvedValue({ data: SETTLED });
    const client = makeClient();
    const invalidateSpy = vi.spyOn(client, "invalidateQueries");

    const { result } = renderHook(() => usePaymentStatus(PENDING), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);

    await waitFor(
      () => {
        expect(result.current.payment.state).toBe("SETTLED");
      },
      { timeout: 5000 },
    );

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

  it("surfaces a RETURNED payment with its return reason", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    getPaymentMock.mockResolvedValue({ data: RETURNED });
    const client = makeClient();

    const { result } = renderHook(() => usePaymentStatus(PENDING), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);

    await waitFor(
      () => {
        expect(result.current.payment.state).toBe("RETURNED");
      },
      { timeout: 5000 },
    );
    expect(result.current.payment.returnReason).toBe("ACCOUNT_CLOSED");
  });

  it("polls a payment born REFERRED until a ruling releases and settles it", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    getPaymentMock.mockResolvedValue({ data: SETTLED });
    const client = makeClient();

    const { result } = renderHook(() => usePaymentStatus(REFERRED), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);

    await waitFor(
      () => {
        expect(result.current.payment.state).toBe("SETTLED");
      },
      { timeout: 15000 },
    );
  }, 20000);

  it("polls a payment born REFERRED until a ruling declines it, then invalidates account money data", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    getPaymentMock.mockResolvedValue({ data: DECLINED });
    const client = makeClient();
    const invalidateSpy = vi.spyOn(client, "invalidateQueries");

    const { result } = renderHook(() => usePaymentStatus(REFERRED), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);

    await waitFor(
      () => {
        expect(result.current.payment.state).toBe("DECLINED");
      },
      { timeout: 15000 },
    );

    const headers = { "MAL-Account-Holder-Id": INDIVIDUAL_UID };
    const expected = getApiV1AccountsByAccountUidBalanceQueryKey({
      path: { accountUid: SOURCE_UID },
      headers,
    });
    await waitFor(() => {
      expect(
        invalidateSpy.mock.calls.some(([opts]) => {
          const k = (opts as { queryKey?: unknown } | undefined)?.queryKey;
          return JSON.stringify(k) === JSON.stringify(expected);
        }),
      ).toBe(true);
    });
  }, 20000);

  it("does not fetch a payment that is already DECLINED", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    const client = makeClient();

    const { result } = renderHook(() => usePaymentStatus(DECLINED), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);

    expect(result.current.payment).toEqual(DECLINED);
    expect(getPaymentMock).not.toHaveBeenCalled();
  });

  it("keeps reporting REFERRED while no ruling has been made", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    getPaymentMock.mockResolvedValue({ data: REFERRED });
    const client = makeClient();

    const { result } = renderHook(() => usePaymentStatus(REFERRED), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);

    await waitFor(
      () => {
        expect(getPaymentMock).toHaveBeenCalled();
      },
      { timeout: 15000 },
    );
    expect(result.current.payment.state).toBe("REFERRED");
  }, 20000);

  it("keeps reporting PENDING while the scheme has not resolved the payment", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    getPaymentMock.mockResolvedValue({ data: PENDING });
    const client = makeClient();

    const { result } = renderHook(() => usePaymentStatus(PENDING), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);

    await waitFor(
      () => {
        expect(getPaymentMock).toHaveBeenCalled();
      },
      { timeout: 5000 },
    );
    expect(result.current.payment.state).toBe("PENDING");
  });
});
