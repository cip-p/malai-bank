import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type { LoginRequest } from "@/api/generated/types.gen";
import { HttpError } from "@/api/http";
import { useLogin } from "./useLogin";

vi.mock("@/api/generated/sdk.gen", () => ({
  postApiV1AuthLogin: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const postApiV1AuthLoginMock = sdk.postApiV1AuthLogin as unknown as ReturnType<
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

const VALID_INPUT: LoginRequest = { email: "ada@example.com" };

describe("useLogin", () => {
  beforeEach(() => {
    postApiV1AuthLoginMock.mockReset();
    navigateMock.mockReset();
  });

  it("on success navigates to the Verification screen carrying the login origin", async () => {
    postApiV1AuthLoginMock.mockResolvedValue({ data: { message: "ok" } });

    const { result } = renderHook(() => useLogin(), {
      wrapper: wrapperOf(makeClient()),
    });

    result.current.login(VALID_INPUT);

    await waitFor(() => {
      expect(navigateMock).toHaveBeenCalledWith("/verify", {
        state: { origin: { kind: "login", request: VALID_INPUT } },
      });
    });
  });

  it("submits only the email as the request body", async () => {
    postApiV1AuthLoginMock.mockResolvedValue({ data: { message: "ok" } });

    const { result } = renderHook(() => useLogin(), {
      wrapper: wrapperOf(makeClient()),
    });

    result.current.login(VALID_INPUT);

    await waitFor(() => {
      expect(postApiV1AuthLoginMock).toHaveBeenCalledTimes(1);
    });
    const firstCall = postApiV1AuthLoginMock.mock.calls[0]?.[0] as
      | { body?: LoginRequest }
      | undefined;
    expect(firstCall?.body).toEqual(VALID_INPUT);
  });

  it("on error does not navigate to the Verification screen", async () => {
    postApiV1AuthLoginMock.mockRejectedValue(
      new HttpError(500, { errorCode: "INTERNAL_ERROR" }),
    );

    const { result } = renderHook(() => useLogin(), {
      wrapper: wrapperOf(makeClient()),
    });

    result.current.login(VALID_INPUT);

    await waitFor(() => {
      expect(result.current.isPending).toBe(false);
    });
    expect(navigateMock).not.toHaveBeenCalled();
  });
});
