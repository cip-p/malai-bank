import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type { ExecMetricsResponse } from "@/api/generated/types.gen";

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1ExecMetrics: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getMetricsMock = sdk.getApiV1ExecMetrics as unknown as ReturnType<
  typeof vi.fn<(...args: unknown[]) => unknown>
>;

const { useExecMetrics, execMetricsQueryOptions } =
  await import("./useExecMetrics");

function makeClient(): QueryClient {
  return new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
}

function wrapperOf(client: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
  Wrapper.displayName = "QueryClientWrapper";
  return Wrapper;
}

function makeResponse(
  overrides: Partial<ExecMetricsResponse> = {},
): ExecMetricsResponse {
  return {
    window: "TODAY",
    windowStart: "2026-07-21",
    bankingDay: "2026-07-21",
    asOf: "2026-07-21T13:30:00Z",
    newCustomers: { status: "AVAILABLE", count: 12 },
    deposits: {
      status: "AVAILABLE",
      amountMinorUnits: 1_000_000,
      currency: "GBP",
    },
    cardsIssued: { status: "AVAILABLE", count: 5 },
    paymentsStopped: {
      status: "AVAILABLE",
      amountMinorUnits: 50_000,
      currency: "GBP",
    },
    ...overrides,
  };
}

describe("useExecMetrics", () => {
  beforeEach(() => {
    getMetricsMock.mockReset();
  });

  it("translates AVAILABLE count and money metrics into first-class tile states", async () => {
    getMetricsMock.mockResolvedValue({ data: makeResponse() });

    const { result } = renderHook(() => useExecMetrics(), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() => expect(result.current.isPending).toBe(false));
    expect(result.current.isError).toBe(false);
    expect(result.current.newCustomers).toEqual({ kind: "count", count: 12 });
    expect(result.current.cardsIssued).toEqual({ kind: "count", count: 5 });
    expect(result.current.deposits).toEqual({
      kind: "money",
      amountMinorUnits: 1_000_000,
      currency: "GBP",
    });
    expect(result.current.paymentsStopped).toEqual({
      kind: "money",
      amountMinorUnits: 50_000,
      currency: "GBP",
    });
  });

  it("exposes the bankingDay, windowStart, and asOf the contract supplies", async () => {
    getMetricsMock.mockResolvedValue({ data: makeResponse() });

    const { result } = renderHook(() => useExecMetrics(), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() => expect(result.current.isPending).toBe(false));
    expect(result.current.bankingDay).toBe("2026-07-21");
    expect(result.current.windowStart).toBe("2026-07-21");
    expect(result.current.asOf).toBe("2026-07-21T13:30:00Z");
  });

  it("asks for the Banking day by default and carries a wider window across when given one", async () => {
    getMetricsMock.mockResolvedValue({ data: makeResponse() });

    renderHook(() => useExecMetrics(), { wrapper: wrapperOf(makeClient()) });
    await waitFor(() => expect(getMetricsMock).toHaveBeenCalled());
    const defaultArg = getMetricsMock.mock.calls[0][0] as {
      query?: { window?: string };
    };
    expect(defaultArg.query).toMatchObject({ window: "TODAY" });

    getMetricsMock.mockClear();
    renderHook(() => useExecMetrics("LAST_7_DAYS"), {
      wrapper: wrapperOf(makeClient()),
    });
    await waitFor(() => expect(getMetricsMock).toHaveBeenCalled());
    const widerArg = getMetricsMock.mock.calls[0][0] as {
      query?: { window?: string };
    };
    expect(widerArg.query).toMatchObject({ window: "LAST_7_DAYS" });
  });

  it("translates an UNAVAILABLE metric into the unavailable state, valuing the others", async () => {
    getMetricsMock.mockResolvedValue({
      data: makeResponse({
        deposits: { status: "UNAVAILABLE" },
        cardsIssued: { status: "UNAVAILABLE" },
        paymentsStopped: { status: "UNAVAILABLE" },
      }),
    });

    const { result } = renderHook(() => useExecMetrics(), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() => expect(result.current.isPending).toBe(false));
    expect(result.current.deposits).toEqual({ kind: "unavailable" });
    expect(result.current.cardsIssued).toEqual({ kind: "unavailable" });
    expect(result.current.paymentsStopped).toEqual({ kind: "unavailable" });
    expect(result.current.newCustomers).toEqual({ kind: "count", count: 12 });
  });

  it("is loading on every tile while the read is in flight, distinct from unavailable", () => {
    getMetricsMock.mockImplementation(() => new Promise(() => {}));

    const { result } = renderHook(() => useExecMetrics(), {
      wrapper: wrapperOf(makeClient()),
    });

    expect(result.current.isPending).toBe(true);
    expect(result.current.newCustomers).toEqual({ kind: "loading" });
    expect(result.current.deposits).toEqual({ kind: "loading" });
    expect(result.current.cardsIssued).toEqual({ kind: "loading" });
    expect(result.current.paymentsStopped).toEqual({ kind: "loading" });
    expect(result.current.asOf).toBeNull();
    expect(result.current.bankingDay).toBeNull();
  });

  it("degrades every tile to unavailable when the whole read fails", async () => {
    getMetricsMock.mockRejectedValue(new Error("boom"));

    const { result } = renderHook(() => useExecMetrics(), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.newCustomers).toEqual({ kind: "unavailable" });
    expect(result.current.paymentsStopped).toEqual({ kind: "unavailable" });
    expect(result.current.asOf).toBeNull();
  });

  it("refetches on demand", async () => {
    getMetricsMock.mockResolvedValue({ data: makeResponse() });

    const { result } = renderHook(() => useExecMetrics(), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() => expect(result.current.isPending).toBe(false));
    expect(getMetricsMock).toHaveBeenCalledTimes(1);

    result.current.refetch();

    await waitFor(() => expect(getMetricsMock).toHaveBeenCalledTimes(2));
  });

  it("opts this query into refetchOnWindowFocus without touching the global default", () => {
    expect(execMetricsQueryOptions().refetchOnWindowFocus).toBe(true);
  });
});
