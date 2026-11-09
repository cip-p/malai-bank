import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type { AmlCaseDetailResponse } from "@/api/generated/types.gen";
import { HttpError } from "@/api/http";

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1AdminAmlCasesByCaseId: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getCaseMock = sdk.getApiV1AdminAmlCasesByCaseId as unknown as ReturnType<
  typeof vi.fn<(...args: unknown[]) => unknown>
>;

const { CASE_DETAIL_REFETCH_MS, amlCaseQueryOptions, useAmlCase } =
  await import("./useAmlCase");

const CASE_ID = "1a2b3c4d-1111-1111-1111-111111111111";

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

function makeDetail(
  overrides: Partial<AmlCaseDetailResponse>,
): AmlCaseDetailResponse {
  return {
    caseId: CASE_ID,
    accountId: "9f8e7d6c-2222-2222-2222-222222222222",
    status: "INVESTIGATING",
    openedAt: "2026-07-20T09:00:00Z",
    updatedAt: "2026-07-20T09:30:00Z",
    alerts: [],
    ...overrides,
  };
}

describe("useAmlCase", () => {
  beforeEach(() => {
    getCaseMock.mockReset();
  });

  it("asks the bank for one investigation by id and serves it as given", async () => {
    const detail = makeDetail({});
    getCaseMock.mockResolvedValue({ data: detail });

    const { result } = renderHook(() => useAmlCase(CASE_ID), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() => expect(result.current.detail).toEqual(detail));
    const firstArg = getCaseMock.mock.calls[0][0] as {
      path?: { caseId?: string };
    };
    expect(firstArg.path).toMatchObject({ caseId: CASE_ID });
  });

  it("reports an unknown case id as not found, not as an error", async () => {
    getCaseMock.mockRejectedValue(new HttpError(404, { message: "No case" }));

    const { result } = renderHook(() => useAmlCase("no-such-case"), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() => expect(result.current.isNotFound).toBe(true));
    expect(result.current.isError).toBe(false);
    expect(result.current.detail).toBeNull();
  });

  it("stays an ordinary error for any other failure", async () => {
    getCaseMock.mockRejectedValue(new HttpError(500, { message: "boom" }));

    const { result } = renderHook(() => useAmlCase(CASE_ID), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.isNotFound).toBe(false);
    expect(result.current.detail).toBeNull();
  });

  it("is pending while the read is in flight", () => {
    getCaseMock.mockImplementation(() => new Promise(() => {}));

    const { result } = renderHook(() => useAmlCase(CASE_ID), {
      wrapper: wrapperOf(makeClient()),
    });

    expect(result.current.isPending).toBe(true);
    expect(result.current.detail).toBeNull();
  });

  it("keeps itself fresh on a short interval, and only while the window is in view", () => {
    const options = amlCaseQueryOptions(CASE_ID);
    expect(options.refetchInterval({ state: { status: "success" } })).toBe(
      CASE_DETAIL_REFETCH_MS,
    );
    expect(CASE_DETAIL_REFETCH_MS).toBeLessThanOrEqual(10_000);
    expect(options.refetchIntervalInBackground).toBe(false);
  });

  it("stops asking once the bank has said the case is not there", () => {
    const options = amlCaseQueryOptions(CASE_ID);
    expect(options.refetchInterval({ state: { status: "error" } })).toBe(false);
  });

  it("refetches on demand", async () => {
    getCaseMock.mockResolvedValue({ data: makeDetail({}) });
    const { result } = renderHook(() => useAmlCase(CASE_ID), {
      wrapper: wrapperOf(makeClient()),
    });
    await waitFor(() => expect(result.current.detail).not.toBeNull());

    result.current.refetch();

    await waitFor(() => expect(getCaseMock).toHaveBeenCalledTimes(2));
  });
});
