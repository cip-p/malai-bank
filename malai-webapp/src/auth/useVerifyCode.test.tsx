import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { getApiV1UserQueryKey } from "@/api/generated/@tanstack/react-query.gen";
import type { LoginResponse } from "@/api/generated/types.gen";
import { HttpError } from "@/api/http";
import { useVerifyCode } from "./useVerifyCode";

vi.mock("@/api/generated/sdk.gen", () => ({
  postApiV1AuthVerifyCode: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const postApiV1AuthVerifyCodeMock =
  sdk.postApiV1AuthVerifyCode as unknown as ReturnType<
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

const SAMPLE_LOGIN_RESPONSE: LoginResponse = {
  userId: "11111111-1111-1111-1111-111111111111",
  accessToken: "access",
  refreshToken: "refresh",
  idToken: "id",
  expiresIn: 3600,
};

describe("useVerifyCode", () => {
  beforeEach(() => {
    postApiV1AuthVerifyCodeMock.mockReset();
  });

  it("submits email and confirmationCode as the request body", async () => {
    postApiV1AuthVerifyCodeMock.mockResolvedValue({
      data: SAMPLE_LOGIN_RESPONSE,
    });
    const { result } = renderHook(() => useVerifyCode(), {
      wrapper: wrapperOf(makeClient()),
    });

    result.current.verify({
      email: "ada@example.com",
      confirmationCode: "123456",
    });

    await waitFor(() => {
      expect(postApiV1AuthVerifyCodeMock).toHaveBeenCalledTimes(1);
    });
    const firstCall = postApiV1AuthVerifyCodeMock.mock.calls[0]?.[0] as
      | { body?: unknown }
      | undefined;
    expect(firstCall?.body).toEqual({
      email: "ada@example.com",
      confirmationCode: "123456",
    });
  });

  it("invalidates the user query on a verified code (the sole auth-state signal)", async () => {
    postApiV1AuthVerifyCodeMock.mockResolvedValue({
      data: SAMPLE_LOGIN_RESPONSE,
    });
    const client = makeClient();
    const invalidateSpy = vi.spyOn(client, "invalidateQueries");

    const { result } = renderHook(() => useVerifyCode(), {
      wrapper: wrapperOf(client),
    });

    result.current.verify({
      email: "ada@example.com",
      confirmationCode: "123456",
    });

    await waitFor(() => {
      expect(invalidateSpy).toHaveBeenCalledWith({
        queryKey: getApiV1UserQueryKey(),
      });
    });
  });

  it("on a wrong or expired code exposes isError and does not invalidate the user query", async () => {
    postApiV1AuthVerifyCodeMock.mockRejectedValue(
      new HttpError(401, { message: "5995" }),
    );
    const client = makeClient();
    const invalidateSpy = vi.spyOn(client, "invalidateQueries");

    const { result } = renderHook(() => useVerifyCode(), {
      wrapper: wrapperOf(client),
    });

    result.current.verify({
      email: "ada@example.com",
      confirmationCode: "000000",
    });

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });
    expect(invalidateSpy).not.toHaveBeenCalled();
  });
});
