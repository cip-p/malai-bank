import { useQuery } from "@tanstack/react-query";
import { getApiV1CardsByCardIdOptions } from "@/api/generated/@tanstack/react-query.gen";
import type { CardDetailResponse } from "@/api/generated/types.gen";
import { useActiveAccountHolder } from "@/account-holder/useActiveAccountHolder";

export type UseCardRevealResult = {
  detail: CardDetailResponse | null;
  isPending: boolean;
  isError: boolean;
  error: unknown;
};

export function useCardReveal(
  cardId: string,
  revealed: boolean,
): UseCardRevealResult {
  const holder = useActiveAccountHolder();
  const activeHolderUid =
    holder.status === "ready" ? holder.activeHolder.accountHolderUid : "";

  const query = useQuery({
    ...getApiV1CardsByCardIdOptions({
      path: { cardId },
      headers: { "MAL-Account-Holder-Id": activeHolderUid },
    }),
    enabled: revealed && holder.status === "ready",
  });

  if (!revealed) {
    return { detail: null, isPending: false, isError: false, error: null };
  }

  if (holder.status === "loading") {
    return { detail: null, isPending: true, isError: false, error: null };
  }
  if (holder.status === "error") {
    return {
      detail: null,
      isPending: false,
      isError: true,
      error: holder.error,
    };
  }

  if (query.isPending) {
    return { detail: null, isPending: true, isError: false, error: null };
  }
  if (query.isError) {
    return {
      detail: null,
      isPending: false,
      isError: true,
      error: query.error,
    };
  }

  return {
    detail: query.data ?? null,
    isPending: false,
    isError: false,
    error: null,
  };
}
