import { useQuery } from "@tanstack/react-query";
import { getApiV1SandboxCardMerchantsOptions } from "@/api/generated/@tanstack/react-query.gen";
import type { CardMerchantResponse } from "@/api/generated/types.gen";

export type UseCardMerchantsResult = {
  merchants: CardMerchantResponse[] | null;
  isPending: boolean;
  isError: boolean;
  error: unknown;
};

export function useCardMerchants(): UseCardMerchantsResult {
  const query = useQuery(getApiV1SandboxCardMerchantsOptions());

  if (query.isPending) {
    return { merchants: null, isPending: true, isError: false, error: null };
  }
  if (query.isError) {
    return {
      merchants: null,
      isPending: false,
      isError: true,
      error: query.error,
    };
  }
  return {
    merchants: query.data?.merchants ?? [],
    isPending: false,
    isError: false,
    error: null,
  };
}
