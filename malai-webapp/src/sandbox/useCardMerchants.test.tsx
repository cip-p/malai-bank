import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type { CardMerchantsResponse } from "@/api/generated/types.gen";
import { HttpError } from "@/api/http";
import { useCardMerchants } from "./useCardMerchants";

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1SandboxCardMerchants: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const merchantsMock = sdk.getApiV1SandboxCardMerchants as unknown as ReturnType<
  typeof vi.fn<(...args: unknown[]) => unknown>
>;

function makeClient(): QueryClient {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

function wrapperOf(client: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
  Wrapper.displayName = "QueryClientWrapper";
  return Wrapper;
}

const MERCHANTS: CardMerchantsResponse = {
  merchants: [
    { merchantId: "merch-1", name: "Fern & Forage", mcc: "5411" },
    { merchantId: "merch-2", name: "Brindle Coffee", mcc: "5814" },
  ],
};

describe("useCardMerchants", () => {
  beforeEach(() => {
    merchantsMock.mockReset();
  });

  it("returns the merchant directory", async () => {
    merchantsMock.mockResolvedValue({ data: MERCHANTS });

    const { result } = renderHook(() => useCardMerchants(), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() =>
      expect(result.current.merchants).toEqual(MERCHANTS.merchants),
    );
  });

  it("surfaces errors", async () => {
    merchantsMock.mockRejectedValue(new HttpError(500, { message: "boom" }));

    const { result } = renderHook(() => useCardMerchants(), {
      wrapper: wrapperOf(makeClient()),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.merchants).toBeNull();
  });
});
