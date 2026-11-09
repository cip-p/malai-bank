import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type { MdrActionableOutputResponse } from "@/api/generated/types.gen";
import { HttpError } from "@/api/http";

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1AdminMdrDecisionsByDecisionIdActionableOutput: vi.fn(),
}));

const toastErrorMock = vi.fn();
vi.mock("sonner", () => ({
  toast: { error: (...args: unknown[]) => toastErrorMock(...args) },
}));

const { createQueryClient } = await import("@/lib/queryClient");

const sdk = await import("@/api/generated/sdk.gen");
const gateMock =
  sdk.getApiV1AdminMdrDecisionsByDecisionIdActionableOutput as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;

const { useMdrActionableOutput } = await import("./useMdrActionableOutput");

const DECISION_ID = "11111111-1111-1111-1111-111111111111";

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

function makeInForce(
  overrides: Partial<MdrActionableOutputResponse>,
): MdrActionableOutputResponse {
  return {
    decisionId: DECISION_ID,
    ruling: "ACCEPT",
    rulingEntryId: "55555555-5555-5555-5555-555555555555",
    actionableOutput: "No anomalies found.",
    ...overrides,
  };
}

describe("useMdrActionableOutput", () => {
  beforeEach(() => {
    gateMock.mockReset();
    toastErrorMock.mockReset();
  });

  it("asks the gate nothing until asked, then returns the answer in force", async () => {
    const answer = makeInForce({});
    gateMock.mockResolvedValue({ data: answer });

    const { result } = renderHook(() => useMdrActionableOutput(DECISION_ID), {
      wrapper: wrapperOf(makeClient()),
    });

    expect(result.current.isIdle).toBe(true);
    expect(result.current.inForce).toBeNull();
    expect(gateMock).not.toHaveBeenCalled();

    act(() => result.current.ask());

    await waitFor(() => expect(result.current.inForce).toEqual(answer));
    expect(result.current.isIdle).toBe(false);
    expect(result.current.isNoRuling).toBe(false);
    expect(result.current.isError).toBe(false);
    const firstArg = gateMock.mock.calls[0][0] as {
      path?: { decisionId?: string };
    };
    expect(firstArg.path).toMatchObject({ decisionId: DECISION_ID });
  });

  it("translates the gate's 409 into no-ruling-in-force, not an error", async () => {
    gateMock.mockRejectedValue(
      new HttpError(409, { code: 6991, message: "MDR_OUTPUT_NOT_ACTIONABLE" }),
    );

    const { result } = renderHook(() => useMdrActionableOutput(DECISION_ID), {
      wrapper: wrapperOf(makeClient()),
    });

    act(() => result.current.ask());

    await waitFor(() => expect(result.current.isNoRuling).toBe(true));
    expect(result.current.isError).toBe(false);
    expect(result.current.inForce).toBeNull();
    expect(result.current.isIdle).toBe(false);
  });

  it("re-asks the gate on demand, flipping the refusal to the answer a new ruling put in force", async () => {
    gateMock.mockRejectedValueOnce(
      new HttpError(409, { code: 6991, message: "MDR_OUTPUT_NOT_ACTIONABLE" }),
    );
    const answer = makeInForce({ ruling: "EDIT" });
    gateMock.mockResolvedValueOnce({ data: answer });

    const { result } = renderHook(() => useMdrActionableOutput(DECISION_ID), {
      wrapper: wrapperOf(makeClient()),
    });

    act(() => result.current.ask());
    await waitFor(() => expect(result.current.isNoRuling).toBe(true));

    act(() => result.current.ask());

    await waitFor(() => expect(result.current.inForce).toEqual(answer));
    expect(result.current.isNoRuling).toBe(false);
    expect(result.current.isError).toBe(false);
    expect(gateMock).toHaveBeenCalledTimes(2);
  });

  it("flips a served answer back to the refusal when a later ruling bars the output", async () => {
    gateMock.mockResolvedValueOnce({ data: makeInForce({}) });
    gateMock.mockRejectedValueOnce(
      new HttpError(409, { code: 6991, message: "MDR_OUTPUT_NOT_ACTIONABLE" }),
    );

    const { result } = renderHook(() => useMdrActionableOutput(DECISION_ID), {
      wrapper: wrapperOf(makeClient()),
    });

    act(() => result.current.ask());
    await waitFor(() => expect(result.current.inForce).not.toBeNull());

    act(() => result.current.ask());

    await waitFor(() => expect(result.current.isNoRuling).toBe(true));
    expect(result.current.inForce).toBeNull();
    expect(result.current.isError).toBe(false);
  });

  it("keeps any other failure an ordinary error", async () => {
    gateMock.mockRejectedValue(new HttpError(500, { message: "boom" }));

    const { result } = renderHook(() => useMdrActionableOutput(DECISION_ID), {
      wrapper: wrapperOf(makeClient()),
    });

    act(() => result.current.ask());

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.isNoRuling).toBe(false);
    expect(result.current.inForce).toBeNull();
  });

  it("is asking while the gate is in flight, holding no answer meanwhile", async () => {
    let answer: (value: unknown) => void = () => {};
    gateMock.mockImplementation(
      () => new Promise((resolve) => (answer = resolve)),
    );

    const { result } = renderHook(() => useMdrActionableOutput(DECISION_ID), {
      wrapper: wrapperOf(makeClient()),
    });

    expect(result.current.isAsking).toBe(false);
    act(() => result.current.ask());

    await waitFor(() => expect(result.current.isAsking).toBe(true));
    expect(result.current.isIdle).toBe(false);
    expect(result.current.inForce).toBeNull();

    act(() => answer({ data: makeInForce({}) }));
    await waitFor(() => expect(result.current.isAsking).toBe(false));
    expect(result.current.inForce).not.toBeNull();

    act(() => result.current.ask());
    await waitFor(() => expect(result.current.isAsking).toBe(true));
    expect(result.current.inForce).toBeNull();
  });

  it("never toasts about the gate — the refusal and even a failure stay in the panel", async () => {
    gateMock.mockRejectedValueOnce(
      new HttpError(409, { code: 6991, message: "MDR_OUTPUT_NOT_ACTIONABLE" }),
    );
    gateMock.mockRejectedValueOnce(new HttpError(500, { message: "boom" }));

    const { result } = renderHook(() => useMdrActionableOutput(DECISION_ID), {
      wrapper: wrapperOf(createQueryClient()),
    });

    act(() => result.current.ask());
    await waitFor(() => expect(result.current.isNoRuling).toBe(true));

    act(() => result.current.ask());
    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(toastErrorMock).not.toHaveBeenCalled();
  });
});
