import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { postApiV1SandboxCardRefundsMutation } from "@/api/generated/@tanstack/react-query.gen";
import { HttpError, isHttpStatus } from "@/api/http";
import { useActiveAccountHolder } from "@/account-holder/useActiveAccountHolder";
import { invalidateCreditAccountData } from "@/cards/invalidateCreditAccountData";
import { invalidateAllAccountMoneyData } from "./invalidateAllAccountMoneyData";

export type CardRefundInput = {
  authorisationId: string;
  accountUid?: string;
};

export type UseCardRefundMutationResult = {
  refund: (input: CardRefundInput) => void;
  isPending: boolean;
};

const NOT_SETTLED_CODE = "7966";
const GENERIC_ERROR_MESSAGE = "Something went wrong. Please try again.";

function isNotSettled(error: unknown): boolean {
  if (!(error instanceof HttpError)) return false;
  if (error.status !== 400) return false;
  const body = error.body as { message?: unknown } | undefined;
  return body?.message === NOT_SETTLED_CODE;
}

export function useCardRefundMutation(): UseCardRefundMutationResult {
  const holder = useActiveAccountHolder();
  const queryClient = useQueryClient();
  const activeHolderUid =
    holder.status === "ready" ? holder.activeHolder.accountHolderUid : "";

  const mutation = useMutation({
    ...postApiV1SandboxCardRefundsMutation(),
    meta: { handledLocally: true },
    onError: (error) => {
      if (isHttpStatus(error, 401)) return;
      if (isNotSettled(error)) {
        toast.error(
          "That purchase hasn’t settled yet — present pending captures first.",
        );
        return;
      }
      if (isHttpStatus(error, 404)) {
        toast.error("We couldn’t find that purchase.");
        return;
      }
      toast.error(GENERIC_ERROR_MESSAGE);
    },
  });

  return {
    refund: (input) => {
      mutation.mutate(
        {
          body: { authorisationId: input.authorisationId },
          headers: { "MAL-Account-Holder-Id": activeHolderUid },
        },
        {
          onSuccess: () => {
            if (input.accountUid) {
              invalidateCreditAccountData(
                queryClient,
                activeHolderUid,
                input.accountUid,
              );
            } else {
              invalidateAllAccountMoneyData(queryClient);
            }
            toast.success("Refund on the way.");
          },
        },
      );
    },
    isPending: mutation.isPending,
  };
}
