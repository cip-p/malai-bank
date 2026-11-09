import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { getApiV1UserQueryKey } from "@/api/generated/@tanstack/react-query.gen";
import type { UserResponse } from "@/api/generated/types.gen";
import { HttpError } from "@/api/http";
import { useUpdateUser } from "./useUpdateUser";

vi.mock("@/api/generated/sdk.gen", () => ({
  putApiV1User: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const putApiV1UserMock = sdk.putApiV1User as unknown as ReturnType<
  typeof vi.fn<(...args: unknown[]) => unknown>
>;

const toastSuccessMock = vi.fn();
vi.mock("sonner", () => ({
  toast: {
    success: (...args: unknown[]) => toastSuccessMock(...args),
  },
}));

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

const RESPONSE: UserResponse = {
  firstName: "Ada-New",
  lastName: "Lovelace",
  email: "ada@example.com",
  dateOfBirth: "1815-12-10",
  roles: ["CUSTOMER"],
};

describe("useUpdateUser", () => {
  beforeEach(() => {
    putApiV1UserMock.mockReset();
    toastSuccessMock.mockReset();
  });

  it("on success writes the response into the user-query cache", async () => {
    putApiV1UserMock.mockResolvedValue({ data: RESPONSE });
    const client = makeClient();

    const { result } = renderHook(() => useUpdateUser(), {
      wrapper: wrapperOf(client),
    });

    result.current.updateUser({ firstName: "Ada-New", lastName: "Lovelace" });

    await waitFor(() => {
      expect(client.getQueryData(getApiV1UserQueryKey())).toEqual(RESPONSE);
    });
  });

  it("on success fires the 'Profile updated.' toast", async () => {
    putApiV1UserMock.mockResolvedValue({ data: RESPONSE });
    const client = makeClient();

    const { result } = renderHook(() => useUpdateUser(), {
      wrapper: wrapperOf(client),
    });

    result.current.updateUser({ firstName: "Ada-New", lastName: "Lovelace" });

    await waitFor(() => {
      expect(toastSuccessMock).toHaveBeenCalledWith("Profile updated.");
    });
  });

  it("submits firstName and lastName as the request body", async () => {
    putApiV1UserMock.mockResolvedValue({ data: RESPONSE });
    const client = makeClient();

    const { result } = renderHook(() => useUpdateUser(), {
      wrapper: wrapperOf(client),
    });

    result.current.updateUser({ firstName: "Grace", lastName: "Hopper" });

    await waitFor(() => {
      expect(putApiV1UserMock).toHaveBeenCalledTimes(1);
    });
    const firstCall = putApiV1UserMock.mock.calls[0]?.[0] as
      | { body?: { firstName?: string; lastName?: string } }
      | undefined;
    expect(firstCall?.body).toEqual({ firstName: "Grace", lastName: "Hopper" });
  });

  it("on 4xx does not write the user-query cache and does not fire the success toast", async () => {
    putApiV1UserMock.mockRejectedValue(
      new HttpError(400, { errorCode: "BAD_REQUEST" }),
    );
    const client = makeClient();

    const { result } = renderHook(() => useUpdateUser(), {
      wrapper: wrapperOf(client),
    });

    result.current.updateUser({ firstName: "Ada-New", lastName: "Lovelace" });

    await waitFor(() => {
      expect(result.current.isPending).toBe(false);
    });
    expect(client.getQueryData(getApiV1UserQueryKey())).toBeUndefined();
    expect(toastSuccessMock).not.toHaveBeenCalled();
  });
});
