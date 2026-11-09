import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { toast } from "sonner";
import {
  getApiV1PayeesQueryKey,
  postApiV1PayeesMutation,
} from "@/api/generated/@tanstack/react-query.gen";
import type { PayeeRequest, PayeeResponse } from "@/api/generated/types.gen";
import { useActiveAccountHolder } from "@/account-holder/useActiveAccountHolder";
import { mintIdempotencyKey } from "@/lib/mintIdempotencyKey";

export type UseCreatePayeeOptions = {
  onSuccess?: (payee: PayeeResponse) => void;
  navigateOnSuccess?: boolean;
};

export type UseCreatePayeeResult = {
  createPayee: (input: PayeeRequest) => void;
  isPending: boolean;
};

export function useCreatePayee(
  options: UseCreatePayeeOptions = {},
): UseCreatePayeeResult {
  const holder = useActiveAccountHolder();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const activeHolderUid =
    holder.status === "ready" ? holder.activeHolder.accountHolderUid : "";
  const navigateOnSuccess = options.navigateOnSuccess !== false;

  const mutation = useMutation({
    ...postApiV1PayeesMutation(),
    onSuccess: (payee) => {
      void queryClient.invalidateQueries({
        queryKey: getApiV1PayeesQueryKey({
          headers: { "MAL-Account-Holder-Id": activeHolderUid },
        }),
      });
      toast.success("Payee saved.");
      options.onSuccess?.(payee);
      if (navigateOnSuccess) navigate("/move/payees");
    },
  });

  return {
    createPayee: (input) =>
      mutation.mutate({
        body: input,
        headers: {
          "MAL-Account-Holder-Id": activeHolderUid,
          "Idempotency-Key": mintIdempotencyKey(),
        },
      }),
    isPending: mutation.isPending,
  };
}
