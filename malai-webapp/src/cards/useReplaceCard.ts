import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { postApiV1CardsByCardIdReportLostOrStolenMutation } from "@/api/generated/@tanstack/react-query.gen";
import type { CardReplacementResponse } from "@/api/generated/types.gen";
import { isHttpStatus } from "@/api/http";
import { useActiveAccountHolder } from "@/account-holder/useActiveAccountHolder";
import { mintIdempotencyKey } from "@/lib/mintIdempotencyKey";
import { invalidateCards } from "./invalidateCards";

export type UseReplaceCardOptions = {
  onSuccess?: (response: CardReplacementResponse) => void;
};

export type UseReplaceCardResult = {
  replace: (cardId: string) => void;
  isPending: boolean;
};

const GENERIC_ERROR_MESSAGE = "Something went wrong. Please try again.";

export function useReplaceCard(
  options: UseReplaceCardOptions = {},
): UseReplaceCardResult {
  const holder = useActiveAccountHolder();
  const queryClient = useQueryClient();
  const activeHolderUid =
    holder.status === "ready" ? holder.activeHolder.accountHolderUid : "";

  const mutation = useMutation({
    ...postApiV1CardsByCardIdReportLostOrStolenMutation(),
    meta: { handledLocally: true },
    onSuccess: (response, variables) => {
      const cardId = (variables as { path: { cardId: string } }).path.cardId;
      invalidateCards(queryClient, activeHolderUid, cardId);
      toast.success("Replacement card issued.");
      options.onSuccess?.(response);
    },
    onError: (error, variables) => {
      if (isHttpStatus(error, 401)) return;
      if (isHttpStatus(error, 409)) {
        const cardId = (variables as { path: { cardId: string } }).path.cardId;
        invalidateCards(queryClient, activeHolderUid, cardId);
      }
      toast.error(GENERIC_ERROR_MESSAGE);
    },
  });

  return {
    replace: (cardId) => {
      mutation.mutate({
        path: { cardId },
        headers: {
          "MAL-Account-Holder-Id": activeHolderUid,
          "Idempotency-Key": mintIdempotencyKey(),
        },
      });
    },
    isPending: mutation.isPending,
  };
}
