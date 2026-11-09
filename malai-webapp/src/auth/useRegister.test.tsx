import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import {
  QueryClient,
  QueryClientProvider,
  type QueryKey,
} from "@tanstack/react-query";
import type { ReactNode } from "react";
import { HttpError } from "@/api/http";
import { getApiV1UserQueryKey } from "@/api/generated/@tanstack/react-query.gen";
import type { RegisterRequest } from "@/api/generated/types.gen";
import { useRegister } from "./useRegister";

vi.mock("@/api/generated/sdk.gen", () => ({
  postApiV1AuthRegister: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const postApiV1AuthRegisterMock =
  sdk.postApiV1AuthRegister as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;

const navigateMock = vi.fn();
vi.mock("react-router-dom", async () => {
  const actual =
    await vi.importActual<typeof import("react-router-dom")>(
      "react-router-dom",
    );
  return { ...actual, useNavigate: () => navigateMock };
});

function makeClient(): QueryClient {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
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

const VALID_INPUT: RegisterRequest = {
  firstName: "Jane",
  lastName: "Doe",
  email: "jane+test@example.com",
};

describe("useRegister", () => {
  beforeEach(() => {
    postApiV1AuthRegisterMock.mockReset();
    navigateMock.mockReset();
  });

  it("on success navigates to the Verification screen carrying the register origin", async () => {
    postApiV1AuthRegisterMock.mockResolvedValue({
      data: { message: "If that email can register, we've sent a code." },
    });
    const client = makeClient();

    const { result } = renderHook(() => useRegister(), {
      wrapper: wrapperOf(client),
    });

    result.current.register(VALID_INPUT);

    await waitFor(() => {
      expect(navigateMock).toHaveBeenCalledWith("/verify", {
        state: { origin: { kind: "register", request: VALID_INPUT } },
      });
    });
  });

  it("submits the name-and-email RegisterRequest as the request body", async () => {
    postApiV1AuthRegisterMock.mockResolvedValue({ data: { message: "ok" } });
    const client = makeClient();

    const { result } = renderHook(() => useRegister(), {
      wrapper: wrapperOf(client),
    });

    result.current.register(VALID_INPUT);

    await waitFor(() => {
      expect(postApiV1AuthRegisterMock).toHaveBeenCalledTimes(1);
    });
    const firstCall = postApiV1AuthRegisterMock.mock.calls[0]?.[0] as
      | { body?: RegisterRequest }
      | undefined;
    expect(firstCall?.body).toEqual(VALID_INPUT);
  });

  it("on 4xx does not navigate and does not invalidate the user query", async () => {
    postApiV1AuthRegisterMock.mockRejectedValue(
      new HttpError(400, { message: "9999" }),
    );
    const client = makeClient();
    const invalidateSpy = vi.spyOn(client, "invalidateQueries");

    const { result } = renderHook(() => useRegister(), {
      wrapper: wrapperOf(client),
    });

    result.current.register(VALID_INPUT);

    await waitFor(() => {
      expect(result.current.isPending).toBe(false);
    });
    expect(navigateMock).not.toHaveBeenCalled();
    const userQueryKey = getApiV1UserQueryKey() as unknown as QueryKey;
    expect(
      invalidateSpy.mock.calls.some(([opts]) => {
        const k = (opts as { queryKey?: QueryKey } | undefined)?.queryKey;
        return JSON.stringify(k) === JSON.stringify(userQueryKey);
      }),
    ).toBe(false);
  });
});
