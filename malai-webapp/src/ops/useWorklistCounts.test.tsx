import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1AdminMdrDecisions: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getDecisionsMock = sdk.getApiV1AdminMdrDecisions as unknown as ReturnType<
  typeof vi.fn<(...args: unknown[]) => unknown>
>;

const { useWorklistCounts } = await import("./useWorklistCounts");

function wrapperOf(client: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
  Wrapper.displayName = "QueryClientWrapper";
  return Wrapper;
}

describe("useWorklistCounts", () => {
  beforeEach(() => {
    getDecisionsMock.mockReset();
  });

  it("counts every view's queue depth from one listing per view", async () => {
    getDecisionsMock.mockImplementation((options: unknown) => {
      const { query } = options as {
        query: { rulingState?: string; advisory?: boolean };
      };
      const size =
        query.advisory === true
          ? 9
          : query.rulingState === "AWAITING_RULING"
            ? 3
            : query.rulingState === "ACCEPT"
              ? 2
              : query.rulingState === "EDIT"
                ? 1
                : 0;
      return Promise.resolve({
        data: { decisions: Array.from({ length: size }, (_, i) => ({ i })) },
      });
    });

    const { result } = renderHook(() => useWorklistCounts(), {
      wrapper: wrapperOf(
        new QueryClient({ defaultOptions: { queries: { retry: false } } }),
      ),
    });

    await waitFor(() =>
      expect(result.current).toEqual({
        AWAITING_RULING: 3,
        ACCEPT: 2,
        EDIT: 1,
        REJECT: 0,
        ADVISORY: 9,
      }),
    );
  });

  it("leaves a view's count unstated while its listing has not answered", () => {
    getDecisionsMock.mockImplementation(() => new Promise(() => undefined));

    const { result } = renderHook(() => useWorklistCounts(), {
      wrapper: wrapperOf(
        new QueryClient({ defaultOptions: { queries: { retry: false } } }),
      ),
    });

    expect(result.current).toEqual({});
  });
});
