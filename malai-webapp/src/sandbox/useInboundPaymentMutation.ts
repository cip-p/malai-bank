import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  getApiV1AccountsByAccountUidTransactionsQueryKey,
  getApiV1AccountsQueryKey,
  postApiV1SandboxInboundPaymentsMutation,
} from "@/api/generated/@tanstack/react-query.gen";
import type {
  InboundPaymentRequest,
  SandboxRail,
} from "@/api/generated/types.gen";
import { isHttpStatus } from "@/api/http";
import { useActiveAccountHolder } from "@/account-holder/useActiveAccountHolder";
import { mintIdempotencyKey } from "@/lib/mintIdempotencyKey";

export type InboundPaymentInput = {
  rail: SandboxRail;
  creditedAccountId: string;
  originatorSortCode: string;
  originatorAccountNumber: string;
  originatorName: string;
  amountMinorUnits: number;
  reference?: string;
};

export type UseInboundPaymentMutationOptions = {
  onSuccess?: () => void;
};

export type UseInboundPaymentMutationResult = {
  submit: (input: InboundPaymentInput) => void;
  isPending: boolean;
};

const GENERIC_ERROR_MESSAGE = "Something went wrong. Please try again.";

export function useInboundPaymentMutation(
  options: UseInboundPaymentMutationOptions = {},
): UseInboundPaymentMutationResult {
  const holder = useActiveAccountHolder();
  const queryClient = useQueryClient();
  const activeHolderUid =
    holder.status === "ready" ? holder.activeHolder.accountHolderUid : "";

  const mutation = useMutation({
    ...postApiV1SandboxInboundPaymentsMutation(),
    meta: { handledLocally: true },
    onSuccess: (_response, variables) => {
      const headers = { "MAL-Account-Holder-Id": activeHolderUid };
      const creditedAccountId = (variables as { body: InboundPaymentRequest })
        .body.creditedAccountId;
      void queryClient.invalidateQueries({
        queryKey: getApiV1AccountsQueryKey({ headers }),
      });
      void queryClient.invalidateQueries({
        queryKey: getApiV1AccountsByAccountUidTransactionsQueryKey({
          path: { accountUid: creditedAccountId },
          headers,
        }),
      });
      toast.success("Inbound payment received.");
      options.onSuccess?.();
    },
    onError: (error) => {
      if (isHttpStatus(error, 401)) return;
      toast.error(GENERIC_ERROR_MESSAGE);
    },
  });

  return {
    submit: (input) => {
      const body: InboundPaymentRequest = {
        rail: input.rail,
        creditedAccountId: input.creditedAccountId,
        originatorSortCode: input.originatorSortCode,
        originatorAccountNumber: input.originatorAccountNumber,
        originatorName: input.originatorName,
        amountMinorUnits: input.amountMinorUnits,
      };
      if (input.reference !== undefined && input.reference.length > 0) {
        body.reference = input.reference;
      }
      mutation.mutate({
        body,
        headers: {
          "MAL-Account-Holder-Id": activeHolderUid,
          "Idempotency-Key": mintIdempotencyKey(),
        },
      });
    },
    isPending: mutation.isPending,
  };
}
