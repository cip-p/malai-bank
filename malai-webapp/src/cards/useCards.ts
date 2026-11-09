import { useQuery } from "@tanstack/react-query";
import { getApiV1CardsOptions } from "@/api/generated/@tanstack/react-query.gen";
import type { CardResponse } from "@/api/generated/types.gen";
import { useActiveAccountHolder } from "@/account-holder/useActiveAccountHolder";

export type UseCardsResult = {
  cards: CardResponse[] | null;
  isPending: boolean;
  isError: boolean;
  error: unknown;
};

export function useCards(): UseCardsResult {
  const holder = useActiveAccountHolder();
  const activeHolderUid =
    holder.status === "ready" ? holder.activeHolder.accountHolderUid : "";

  const query = useQuery({
    ...getApiV1CardsOptions({
      headers: { "MAL-Account-Holder-Id": activeHolderUid },
    }),
    enabled: holder.status === "ready",
  });

  if (holder.status === "loading") {
    return { cards: null, isPending: true, isError: false, error: null };
  }
  if (holder.status === "error") {
    return {
      cards: null,
      isPending: false,
      isError: true,
      error: holder.error,
    };
  }

  if (query.isPending) {
    return { cards: null, isPending: true, isError: false, error: null };
  }
  if (query.isError) {
    return { cards: null, isPending: false, isError: true, error: query.error };
  }

  return {
    cards: query.data?.cards ?? [],
    isPending: false,
    isError: false,
    error: null,
  };
}
