import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { postApiV1CardsByCardIdFreezeMutation } from "@/api/generated/@tanstack/react-query.gen";
import { isHttpStatus } from "@/api/http";
import { useActiveAccountHolder } from "@/account-holder/useActiveAccountHolder";
import { mintIdempotencyKey } from "@/lib/mintIdempotencyKey";
import { invalidateCards } from "./invalidateCards";

export type UseFreezeCardResult = {
  freeze: (cardId: string) => void;
  isPending: boolean;
};

const GENERIC_ERROR_MESSAGE = "Something went wrong. Please try again.";

export function useFreezeCard(): UseFreezeCardResult {
  const holder = useActiveAccountHolder();
  const queryClient = useQueryClient();
  const activeHolderUid =
    holder.status === "ready" ? holder.activeHolder.accountHolderUid : "";

  const mutation = useMutation({
    ...postApiV1CardsByCardIdFreezeMutation(),
    meta: { handledLocally: true },
    onSuccess: (_response, variables) => {
      const cardId = (variables as { path: { cardId: string } }).path.cardId;
      invalidateCards(queryClient, activeHolderUid, cardId);
      toast.success("Card frozen.");
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
    freeze: (cardId) => {
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
