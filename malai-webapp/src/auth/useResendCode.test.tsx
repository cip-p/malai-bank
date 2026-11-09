import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { toast } from "sonner";
import type { LoginRequest, RegisterRequest } from "@/api/generated/types.gen";
import type { VerificationOrigin } from "./verificationOrigin";
import { CODE_RESENT_MESSAGE, useResendCode } from "./useResendCode";

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

vi.mock("@/api/generated/sdk.gen", () => ({
  postApiV1AuthRegister: vi.fn(),
  postApiV1AuthLogin: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const postApiV1AuthRegisterMock =
  sdk.postApiV1AuthRegister as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;
const postApiV1AuthLoginMock = sdk.postApiV1AuthLogin as unknown as ReturnType<
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

const REGISTER_REQUEST: RegisterRequest = {
  firstName: "Jane",
  lastName: "Doe",
  email: "jane@example.com",
};
const LOGIN_REQUEST: LoginRequest = { email: "ada@example.com" };

describe("useResendCode", () => {
  beforeEach(() => {
    postApiV1AuthRegisterMock.mockReset();
    postApiV1AuthLoginMock.mockReset();
    (toast.success as ReturnType<typeof vi.fn>).mockReset();
  });

  it("re-submits the register request and toasts on a register origin", async () => {
    postApiV1AuthRegisterMock.mockResolvedValue({ data: { message: "ok" } });
    const origin: VerificationOrigin = {
      kind: "register",
      request: REGISTER_REQUEST,
    };

    const { result } = renderHook(() => useResendCode(origin), {
      wrapper: wrapperOf(makeClient()),
    });

    result.current.resend();

    await waitFor(() => {
      expect(postApiV1AuthRegisterMock).toHaveBeenCalledTimes(1);
    });
    const body = (
      postApiV1AuthRegisterMock.mock.calls[0]?.[0] as {
        body?: RegisterRequest;
      }
    )?.body;
    expect(body).toEqual(REGISTER_REQUEST);
    expect(postApiV1AuthLoginMock).not.toHaveBeenCalled();
    await waitFor(() => {
      expect(toast.success).toHaveBeenCalledWith(CODE_RESENT_MESSAGE);
    });
  });

  it("re-submits the login request on a login origin", async () => {
    postApiV1AuthLoginMock.mockResolvedValue({ data: { message: "ok" } });
    const origin: VerificationOrigin = {
      kind: "login",
      request: LOGIN_REQUEST,
    };

    const { result } = renderHook(() => useResendCode(origin), {
      wrapper: wrapperOf(makeClient()),
    });

    result.current.resend();

    await waitFor(() => {
      expect(postApiV1AuthLoginMock).toHaveBeenCalledTimes(1);
    });
    const body = (
      postApiV1AuthLoginMock.mock.calls[0]?.[0] as { body?: LoginRequest }
    )?.body;
    expect(body).toEqual(LOGIN_REQUEST);
    expect(postApiV1AuthRegisterMock).not.toHaveBeenCalled();
  });
});
