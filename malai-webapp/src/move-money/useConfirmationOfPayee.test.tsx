import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type {
  AccountHoldersResponse,
  ConfirmationOfPayeeRequest,
  ConfirmationOfPayeeResponse,
} from "@/api/generated/types.gen";
import { getApiV1AccountHolderAllQueryKey } from "@/api/generated/@tanstack/react-query.gen";
import { useActiveHolderOverrideStore } from "@/account-holder/useActiveHolderOverrideStore";
import { useConfirmationOfPayee } from "./useConfirmationOfPayee";

async function waitForHolder(client: QueryClient): Promise<void> {
  await waitFor(() => {
    expect(
      client.getQueryData(getApiV1AccountHolderAllQueryKey()),
    ).toBeDefined();
  });
}

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1AccountHolderAll: vi.fn(),
  postApiV1ConfirmationOfPayee: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getApiV1AccountHolderAllMock =
  sdk.getApiV1AccountHolderAll as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;
const postApiV1ConfirmationOfPayeeMock =
  sdk.postApiV1ConfirmationOfPayee as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;

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

const HOLDERS: AccountHoldersResponse = {
  accountHolders: [
    {
      accountHolderUid: INDIVIDUAL_UID,
      accountHolderName: "Ada Lovelace",
      accountHolderType: "INDIVIDUAL",
    },
  ],
};

const REQUEST: ConfirmationOfPayeeRequest = {
  name: "Sarah Mitchell",
  sortCode: "12-34-56",
  accountNumber: "12345678",
};

const MATCH_RESPONSE: ConfirmationOfPayeeResponse = { result: "MATCH" };

describe("useConfirmationOfPayee", () => {
  beforeEach(() => {
    getApiV1AccountHolderAllMock.mockReset();
    postApiV1ConfirmationOfPayeeMock.mockReset();
    useActiveHolderOverrideStore.setState({ override: null });
  });

  it("invokes the onSuccess callback with the verdict response", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    postApiV1ConfirmationOfPayeeMock.mockResolvedValue({
      data: MATCH_RESPONSE,
    });
    const onSuccess = vi.fn();
    const client = makeClient();

    const { result } = renderHook(() => useConfirmationOfPayee({ onSuccess }), {
      wrapper: wrapperOf(client),
    });

    await waitForHolder(client);
    result.current.confirmPayee(REQUEST);

    await waitFor(() => {
      expect(onSuccess).toHaveBeenCalledWith(MATCH_RESPONSE);
    });
  });

  it("sends MAL-Account-Holder-Id and an Idempotency-Key on the request", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    postApiV1ConfirmationOfPayeeMock.mockResolvedValue({
      data: MATCH_RESPONSE,
    });
    const client = makeClient();

    const { result } = renderHook(() => useConfirmationOfPayee(), {
      wrapper: wrapperOf(client),
    });

    await waitForHolder(client);
    result.current.confirmPayee(REQUEST);

    await waitFor(() => {
      expect(postApiV1ConfirmationOfPayeeMock).toHaveBeenCalledTimes(1);
    });
    const firstCall = postApiV1ConfirmationOfPayeeMock.mock.calls[0]?.[0] as {
      body?: ConfirmationOfPayeeRequest;
      headers?: Record<string, string>;
    };
    expect(firstCall.body).toEqual(REQUEST);
    expect(firstCall.headers?.["MAL-Account-Holder-Id"]).toBe(INDIVIDUAL_UID);
    expect(firstCall.headers?.["Idempotency-Key"]).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
    );
  });

  it("mints a fresh Idempotency-Key on every submit attempt", async () => {
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
    postApiV1ConfirmationOfPayeeMock.mockResolvedValue({
      data: MATCH_RESPONSE,
    });
    const client = makeClient();

    const { result } = renderHook(() => useConfirmationOfPayee(), {
      wrapper: wrapperOf(client),
    });

    await waitForHolder(client);

    result.current.confirmPayee(REQUEST);
    await waitFor(() => {
      expect(postApiV1ConfirmationOfPayeeMock).toHaveBeenCalledTimes(1);
    });

    result.current.confirmPayee(REQUEST);
    await waitFor(() => {
      expect(postApiV1ConfirmationOfPayeeMock).toHaveBeenCalledTimes(2);
    });

    const keyA = (
      postApiV1ConfirmationOfPayeeMock.mock.calls[0]?.[0] as {
        headers?: Record<string, string>;
      }
    ).headers?.["Idempotency-Key"];
    const keyB = (
      postApiV1ConfirmationOfPayeeMock.mock.calls[1]?.[0] as {
        headers?: Record<string, string>;
      }
    ).headers?.["Idempotency-Key"];
    expect(keyA).toBeDefined();
    expect(keyB).toBeDefined();
    expect(keyA).not.toBe(keyB);
  });
});
