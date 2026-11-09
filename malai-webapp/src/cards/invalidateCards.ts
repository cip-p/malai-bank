import type { QueryClient } from "@tanstack/react-query";
import {
  getApiV1CardsByCardIdQueryKey,
  getApiV1CardsQueryKey,
} from "@/api/generated/@tanstack/react-query.gen";

export function invalidateCards(
  queryClient: QueryClient,
  activeHolderUid: string,
  cardId?: string,
): void {
  const headers = { "MAL-Account-Holder-Id": activeHolderUid };
  void queryClient.invalidateQueries({
    queryKey: getApiV1CardsQueryKey({ headers }),
  });
  if (cardId) {
    void queryClient.invalidateQueries({
      queryKey: getApiV1CardsByCardIdQueryKey({ path: { cardId }, headers }),
    });
  }
}
