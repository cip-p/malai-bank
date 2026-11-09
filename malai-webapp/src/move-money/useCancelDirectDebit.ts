import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  deleteApiV1DirectDebitsByDirectDebitIdMutation,
  getApiV1DirectDebitsQueryKey,
} from "@/api/generated/@tanstack/react-query.gen";
import type { DirectDebitsResponse } from "@/api/generated/types.gen";
import { isHttpStatus } from "@/api/http";
import { useActiveAccountHolder } from "@/account-holder/useActiveAccountHolder";

export type UseCancelDirectDebitResult = {
  cancelDirectDebit: (directDebitId: string) => void;
  isPending: boolean;
};

const GENERIC_ERROR_MESSAGE = "Something went wrong. Please try again.";

type OptimisticContext = {
  previous: DirectDebitsResponse | undefined;
  queryKey: ReturnType<typeof getApiV1DirectDebitsQueryKey>;
};

export function useCancelDirectDebit(): UseCancelDirectDebitResult {
  const holder = useActiveAccountHolder();
  const queryClient = useQueryClient();
  const activeHolderUid =
    holder.status === "ready" ? holder.activeHolder.accountHolderUid : "";

  const directDebitsKey = (): ReturnType<typeof getApiV1DirectDebitsQueryKey> =>
    getApiV1DirectDebitsQueryKey({
      headers: { "MAL-Account-Holder-Id": activeHolderUid },
    });

  const succeed = (): void => {
    void queryClient.invalidateQueries({ queryKey: directDebitsKey() });
    toast.success("Direct debit cancelled.");
  };

  const mutation = useMutation({
    ...deleteApiV1DirectDebitsByDirectDebitIdMutation(),
    meta: { handledLocally: true },
    onMutate: (variables): OptimisticContext => {
      const directDebitId = (variables as { path: { directDebitId: string } })
        .path.directDebitId;
      const queryKey = directDebitsKey();
      const previous = queryClient.getQueryData<DirectDebitsResponse>(queryKey);
      if (previous) {
        queryClient.setQueryData<DirectDebitsResponse>(queryKey, {
          ...previous,
          directDebits: previous.directDebits.map((dd) =>
            dd.directDebitId === directDebitId
              ? { ...dd, status: "CANCELLED" }
              : dd,
          ),
        });
      }
      return { previous, queryKey };
    },
    onSuccess: () => {
      succeed();
    },
    onError: (error, _variables, context) => {
      if (isHttpStatus(error, 404)) {
        succeed();
        return;
      }
      if (isHttpStatus(error, 401)) return;
      const ctx = context as OptimisticContext | undefined;
      if (ctx?.previous) {
        queryClient.setQueryData<DirectDebitsResponse>(
          ctx.queryKey,
          ctx.previous,
        );
      }
      toast.error(GENERIC_ERROR_MESSAGE);
    },
  });

  return {
    cancelDirectDebit: (directDebitId: string) =>
      mutation.mutate({
        path: { directDebitId },
        headers: { "MAL-Account-Holder-Id": activeHolderUid },
      }),
    isPending: mutation.isPending,
  };
}
