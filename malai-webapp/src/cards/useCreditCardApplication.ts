import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  getApiV1AccountsQueryKey,
  postApiV1CreditCardApplicationsByAccountHolderIdMutation,
} from "@/api/generated/@tanstack/react-query.gen";
import type { CreditCardApplicationResponse } from "@/api/generated/types.gen";
import { isHttpStatus } from "@/api/http";
import { useActiveAccountHolder } from "@/account-holder/useActiveAccountHolder";
import { mintIdempotencyKey } from "@/lib/mintIdempotencyKey";
import { invalidateCards } from "./invalidateCards";

export type UseCreditCardApplicationOptions = {
  onSuccess?: (response: CreditCardApplicationResponse) => void;
};

export type UseCreditCardApplicationResult = {
  apply: () => void;
  isPending: boolean;
  alreadyExists: boolean;
};

const GENERIC_ERROR_MESSAGE = "Something went wrong. Please try again.";

export function useCreditCardApplication(
  options: UseCreditCardApplicationOptions = {},
): UseCreditCardApplicationResult {
  const holder = useActiveAccountHolder();
  const queryClient = useQueryClient();
  const [alreadyExists, setAlreadyExists] = useState(false);
  const activeHolderUid =
    holder.status === "ready" ? holder.activeHolder.accountHolderUid : "";

  const mutation = useMutation({
    ...postApiV1CreditCardApplicationsByAccountHolderIdMutation(),
    meta: { handledLocally: true },
    onSuccess: (response) => {
      setAlreadyExists(false);
      void queryClient.invalidateQueries({
        queryKey: getApiV1AccountsQueryKey({
          headers: { "MAL-Account-Holder-Id": activeHolderUid },
        }),
      });
      invalidateCards(queryClient, activeHolderUid);
      options.onSuccess?.(response);
    },
    onError: (error) => {
      if (isHttpStatus(error, 401)) return;
      if (isHttpStatus(error, 409)) {
        setAlreadyExists(true);
        return;
      }
      toast.error(GENERIC_ERROR_MESSAGE);
    },
  });

  return {
    apply: () => {
      setAlreadyExists(false);
      mutation.mutate({
        path: { accountHolderId: activeHolderUid },
        headers: {
          "MAL-Account-Holder-Id": activeHolderUid,
          "Idempotency-Key": mintIdempotencyKey(),
        },
      });
    },
    isPending: mutation.isPending,
    alreadyExists,
  };
}
