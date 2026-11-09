import { Component, type ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type {
  AccountHolderSummaryResponse,
  AccountHoldersResponse,
} from "@/api/generated/types.gen";
import { HttpError } from "@/api/http";
import { InvariantViolation } from "@/lib/invariant";
import { useActiveAccountHolder } from "./useActiveAccountHolder";
import { useActiveHolderOverrideStore } from "./useActiveHolderOverrideStore";

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1AccountHolderAll: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getApiV1AccountHolderAllMock =
  sdk.getApiV1AccountHolderAll as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;

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

const INDIVIDUAL: AccountHolderSummaryResponse = {
  accountHolderUid: "11111111-1111-1111-1111-111111111111",
  accountHolderName: "Ada Lovelace",
  accountHolderType: "INDIVIDUAL",
};

const JOINT: AccountHolderSummaryResponse = {
  accountHolderUid: "22222222-2222-2222-2222-222222222222",
  accountHolderName: "Ada & Charles",
  accountHolderType: "JOINT",
};

const BUSINESS: AccountHolderSummaryResponse = {
  accountHolderUid: "33333333-3333-3333-3333-333333333333",
  accountHolderName: "Analytical Engines Ltd",
  accountHolderType: "BUSINESS",
};

function respondWith(holders: AccountHolderSummaryResponse[]) {
  const data: AccountHoldersResponse = { accountHolders: holders };
  getApiV1AccountHolderAllMock.mockResolvedValue({ data });
}

type CapturedErrorBoundaryProps = {
  children: ReactNode;
  onError: (error: Error) => void;
};
class CaptureErrorBoundary extends Component<
  CapturedErrorBoundaryProps,
  { error: Error | null }
> {
  state: { error: Error | null } = { error: null };
  static getDerivedStateFromError(error: Error) {
    return { error };
  }
  componentDidCatch(error: Error) {
    this.props.onError(error);
  }
  render() {
    if (this.state.error) return null;
    return this.props.children;
  }
}

function Probe() {
  useActiveAccountHolder();
  return null;
}

describe("useActiveAccountHolder", () => {
  beforeEach(() => {
    getApiV1AccountHolderAllMock.mockReset();
    useActiveHolderOverrideStore.setState({ override: null });
  });

  it("returns status 'loading' while the holders query is pending", () => {
    getApiV1AccountHolderAllMock.mockImplementation(
      () => new Promise(() => {}),
    );
    const { result } = renderHook(() => useActiveAccountHolder(), {
      wrapper: wrapperOf(makeClient()),
    });
    expect(result.current).toEqual({
      status: "loading",
      activeHolder: null,
      error: null,
    });
  });

  it("picks the INDIVIDUAL holder among multiple types", async () => {
    respondWith([JOINT, INDIVIDUAL, BUSINESS]);
    const { result } = renderHook(() => useActiveAccountHolder(), {
      wrapper: wrapperOf(makeClient()),
    });
    await waitFor(() =>
      expect(result.current).toEqual({
        status: "ready",
        activeHolder: INDIVIDUAL,
        error: null,
      }),
    );
  });

  it("picks the first INDIVIDUAL regardless of position", async () => {
    respondWith([BUSINESS, JOINT, INDIVIDUAL]);
    const { result } = renderHook(() => useActiveAccountHolder(), {
      wrapper: wrapperOf(makeClient()),
    });
    await waitFor(() => expect(result.current.status).toBe("ready"));
    expect(result.current.activeHolder).toEqual(INDIVIDUAL);
  });

  it("surfaces query error state without throwing", async () => {
    const httpError = new HttpError(500, { message: "boom" });
    getApiV1AccountHolderAllMock.mockRejectedValue(httpError);
    const { result } = renderHook(() => useActiveAccountHolder(), {
      wrapper: wrapperOf(makeClient()),
    });
    await waitFor(() => expect(result.current.status).toBe("error"));
    expect(result.current.error).toBe(httpError);
    expect(result.current.activeHolder).toBeNull();
  });

  it("throws InvariantViolation when no INDIVIDUAL holder is present", async () => {
    respondWith([JOINT, BUSINESS]);
    const captured = vi.fn();
    const consoleError = vi
      .spyOn(console, "error")
      .mockImplementation(() => {});
    render(
      <QueryClientProvider client={makeClient()}>
        <CaptureErrorBoundary onError={captured}>
          <Probe />
        </CaptureErrorBoundary>
      </QueryClientProvider>,
    );
    await waitFor(() => {
      expect(captured).toHaveBeenCalledTimes(1);
    });
    const error = captured.mock.calls[0][0] as Error;
    expect(error).toBeInstanceOf(InvariantViolation);
    consoleError.mockRestore();
  });

  it("honours an override set on useActiveHolderOverrideStore", async () => {
    respondWith([INDIVIDUAL, JOINT, BUSINESS]);
    useActiveHolderOverrideStore.getState().setOverride(JOINT.accountHolderUid);

    const { result } = renderHook(() => useActiveAccountHolder(), {
      wrapper: wrapperOf(makeClient()),
    });
    await waitFor(() =>
      expect(result.current).toEqual({
        status: "ready",
        activeHolder: JOINT,
        error: null,
      }),
    );
  });
});
