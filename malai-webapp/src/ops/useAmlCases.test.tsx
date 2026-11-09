import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type { AmlCaseSummary } from "@/api/generated/types.gen";

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1AdminAmlCases: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getCasesMock = sdk.getApiV1AdminAmlCases as unknown as ReturnType<
  typeof vi.fn<(...args: unknown[]) => unknown>
>;

const { CASE_LIST_REFETCH_MS, amlCasesQueryOptions, useAmlCases } =
  await import("./useAmlCases");

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

function makeCase(overrides: Partial<AmlCaseSummary>): AmlCaseSummary {
  return {
    caseId: "11111111-1111-1111-1111-111111111111",
    accountId: "22222222-2222-2222-2222-222222222222",
    status: "OPEN",
    alertCount: 1,
    openedAt: "2026-07-20T09:00:00Z",
    updatedAt: "2026-07-20T09:30:00Z",
    ...overrides,
  };
}

const OPEN_CASE = makeCase({ caseId: "aaaaaaaa-0000-0000-0000-000000000001" });
const INVESTIGATING_CASE = makeCase({
  caseId: "bbbbbbbb-0000-0000-0000-000000000002",
  status: "INVESTIGATING",
});
const REPORTED_CASE = makeCase({
  caseId: "cccccccc-0000-0000-0000-000000000003",
  status: "CLOSED_REPORTED",
});
const ESTATE = [OPEN_CASE, INVESTIGATING_CASE, REPORTED_CASE];

describe("useAmlCases", () => {
  beforeEach(() => {
    getCasesMock.mockReset();
  });

  it("serves the whole investigation estate in the order the bank gave it", async () => {
    getCasesMock.mockResolvedValue({ data: { cases: ESTATE } });

    const { result } = renderHook(() => useAmlCases(null), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() => expect(result.current.cases).toEqual(ESTATE));
    expect(result.current.total).toBe(3);
  });

  it("asks the bank once, unfiltered, and narrows to a status in the browser", async () => {
    getCasesMock.mockResolvedValue({ data: { cases: ESTATE } });

    const { result } = renderHook(() => useAmlCases("CLOSED_REPORTED"), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() => expect(result.current.cases).toEqual([REPORTED_CASE]));
    expect(getCasesMock).toHaveBeenCalledTimes(1);
    const firstArg = getCasesMock.mock.calls[0][0] as {
      query?: Record<string, unknown>;
    };
    expect(firstArg?.query ?? {}).not.toHaveProperty("status");
  });

  it("carries every chip's depth from that one fetch, narrowed or not", async () => {
    getCasesMock.mockResolvedValue({
      data: { cases: [...ESTATE, makeCase({ caseId: "dddddddd" })] },
    });

    const { result } = renderHook(() => useAmlCases("OPEN"), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() => expect(result.current.depths.OPEN).toBe(2));
    expect(result.current.depths).toEqual({
      OPEN: 2,
      INVESTIGATING: 1,
      CLOSED_DISMISSED: 0,
      CLOSED_REPORTED: 1,
    });
    expect(result.current.total).toBe(4);
    expect(getCasesMock).toHaveBeenCalledTimes(1);
  });

  it("does not re-ask the bank when the chip changes", async () => {
    getCasesMock.mockResolvedValue({ data: { cases: ESTATE } });

    const { result, rerender } = renderHook(
      ({ status }: { status: "OPEN" | "INVESTIGATING" | null }) =>
        useAmlCases(status),
      {
        wrapper: wrapperOf(makeClient()),
        initialProps: { status: null as "OPEN" | "INVESTIGATING" | null },
      },
    );
    await waitFor(() => expect(result.current.cases).toEqual(ESTATE));

    rerender({ status: "INVESTIGATING" });

    await waitFor(() =>
      expect(result.current.cases).toEqual([INVESTIGATING_CASE]),
    );
    expect(getCasesMock).toHaveBeenCalledTimes(1);
  });

  it("keeps itself fresh on a short interval, and only while the window is in view", () => {
    const options = amlCasesQueryOptions();
    expect(options.refetchInterval).toBe(CASE_LIST_REFETCH_MS);
    expect(CASE_LIST_REFETCH_MS).toBeLessThanOrEqual(10_000);
    expect(options.refetchIntervalInBackground).toBe(false);
  });

  it("is pending while the listing is in flight", () => {
    getCasesMock.mockImplementation(() => new Promise(() => {}));
    const { result } = renderHook(() => useAmlCases(null), {
      wrapper: wrapperOf(makeClient()),
    });
    expect(result.current.isPending).toBe(true);
    expect(result.current.cases).toBeNull();
  });

  it("reports an error when the listing fails, holding back no half-truth", async () => {
    getCasesMock.mockRejectedValue(new Error("boom"));
    const { result } = renderHook(() => useAmlCases(null), {
      wrapper: wrapperOf(makeClient()),
    });
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.cases).toBeNull();
    expect(result.current.total).toBe(0);
  });

  it("refetches on demand", async () => {
    getCasesMock.mockResolvedValue({ data: { cases: ESTATE } });
    const { result } = renderHook(() => useAmlCases(null), {
      wrapper: wrapperOf(makeClient()),
    });
    await waitFor(() => expect(result.current.cases).toEqual(ESTATE));

    result.current.refetch();

    await waitFor(() => expect(getCasesMock).toHaveBeenCalledTimes(2));
  });
});
