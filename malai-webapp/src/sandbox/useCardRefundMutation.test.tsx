import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type { AccountHoldersResponse } from "@/api/generated/types.gen";
import { HttpError } from "@/api/http";
import { getApiV1AccountHolderAllQueryKey } from "@/api/generated/@tanstack/react-query.gen";
import { useActiveHolderOverrideStore } from "@/account-holder/useActiveHolderOverrideStore";
import { useCardRefundMutation } from "./useCardRefundMutation";

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1AccountHolderAll: vi.fn(),
  postApiV1SandboxCardRefunds: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getApiV1AccountHolderAllMock =
  sdk.getApiV1AccountHolderAll as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;
const refundMock = sdk.postApiV1SandboxCardRefunds as unknown as ReturnType<
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
const AUTHORISATION_ID = "77777777-7777-7777-7777-777777777777";

const HOLDERS: AccountHoldersResponse = {
  accountHolders: [
    {
      accountHolderUid: INDIVIDUAL_UID,
      accountHolderName: "Ada Lovelace",
      accountHolderType: "INDIVIDUAL",
    },
  ],
};

describe("useCardRefundMutation", () => {
  beforeEach(() => {
    getApiV1AccountHolderAllMock.mockReset();
    refundMock.mockReset();
    toastSuccessMock.mockReset();
    toastErrorMock.mockReset();
    useActiveHolderOverrideStore.setState({ override: null });
  });

  it("posts the refund with the holder header and toasts on success", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    refundMock.mockResolvedValue({ data: undefined });
    const client = makeClient();

    const { result } = renderHook(() => useCardRefundMutation(), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);

    act(() => {
      result.current.refund({ authorisationId: AUTHORISATION_ID });
    });

    await waitFor(() => {
      expect(toastSuccessMock).toHaveBeenCalledWith("Refund on the way.");
    });

    const call = refundMock.mock.calls[0]?.[0] as {
      body?: Record<string, unknown>;
      headers?: Record<string, string>;
    };
    expect(call.body).toEqual({ authorisationId: AUTHORISATION_ID });
    expect(call.headers?.["MAL-Account-Holder-Id"]).toBe(INDIVIDUAL_UID);
  });

  it("explains a not-yet-settled purchase distinctly", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    refundMock.mockRejectedValue(new HttpError(400, { message: "7966" }));
    const client = makeClient();

    const { result } = renderHook(() => useCardRefundMutation(), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);

    act(() => {
      result.current.refund({ authorisationId: AUTHORISATION_ID });
    });

    await waitFor(() => {
      expect(toastErrorMock).toHaveBeenCalledWith(
        "That purchase hasn’t settled yet — present pending captures first.",
      );
    });
  });

  it("explains an unknown purchase distinctly", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    refundMock.mockRejectedValue(new HttpError(404, { message: "8993" }));
    const client = makeClient();

    const { result } = renderHook(() => useCardRefundMutation(), {
      wrapper: wrapperOf(client),
    });
    await waitForHolder(client);

    act(() => {
      result.current.refund({ authorisationId: AUTHORISATION_ID });
    });

    await waitFor(() => {
      expect(toastErrorMock).toHaveBeenCalledWith(
        "We couldn’t find that purchase.",
      );
    });
  });
});
