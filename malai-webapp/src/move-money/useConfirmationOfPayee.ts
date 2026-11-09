import { useMutation } from "@tanstack/react-query";
import { postApiV1ConfirmationOfPayeeMutation } from "@/api/generated/@tanstack/react-query.gen";
import type {
  ConfirmationOfPayeeRequest,
  ConfirmationOfPayeeResponse,
} from "@/api/generated/types.gen";
import { useActiveAccountHolder } from "@/account-holder/useActiveAccountHolder";
import { mintIdempotencyKey } from "@/lib/mintIdempotencyKey";

export type UseConfirmationOfPayeeOptions = {
  onSuccess?: (response: ConfirmationOfPayeeResponse) => void;
};

export type UseConfirmationOfPayeeResult = {
  confirmPayee: (input: ConfirmationOfPayeeRequest) => void;
  isPending: boolean;
};

export function useConfirmationOfPayee(
  options: UseConfirmationOfPayeeOptions = {},
): UseConfirmationOfPayeeResult {
  const holder = useActiveAccountHolder();
  const activeHolderUid =
    holder.status === "ready" ? holder.activeHolder.accountHolderUid : "";

  const mutation = useMutation({
    ...postApiV1ConfirmationOfPayeeMutation(),
    onSuccess: (response) => {
      options.onSuccess?.(response);
    },
  });

  return {
    confirmPayee: (input) =>
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
