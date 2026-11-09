import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { postApiV1PaymentsMutation } from "@/api/generated/@tanstack/react-query.gen";
import type { PaymentResponse } from "@/api/generated/types.gen";
import { HttpError, isHttpStatus } from "@/api/http";
import { useActiveAccountHolder } from "@/account-holder/useActiveAccountHolder";
import { invalidateAccountMoneyData } from "@/accounts/invalidateAccountMoneyData";
import { mintIdempotencyKey } from "@/lib/mintIdempotencyKey";

export type TransferInput = {
  fromAccountUid: string;
  toSortCode: string;
  toAccountNumber: string;
  amountMinorUnits: number;
  description?: string;
  copVerdictOverride?: boolean;
  toName?: string;
};

export type UseTransferOptions = {
  onSuccess?: (response: PaymentResponse) => void;
};

export type UseTransferResult = {
  transfer: (input: TransferInput) => void;
  isPending: boolean;
  insufficientFunds: boolean;
  reset: () => void;
};

const INSUFFICIENT_FUNDS_CODE = "7983";
const GENERIC_ERROR_MESSAGE = "Something went wrong. Please try again.";

function isInsufficientFunds(error: unknown): boolean {
  if (!(error instanceof HttpError)) return false;
  if (error.status !== 400) return false;
  const body = error.body as { message?: unknown } | undefined;
  return body?.message === INSUFFICIENT_FUNDS_CODE;
}

export function useTransfer(
  options: UseTransferOptions = {},
): UseTransferResult {
  const holder = useActiveAccountHolder();
  const queryClient = useQueryClient();
  const [insufficientFunds, setInsufficientFunds] = useState(false);
  const activeHolderUid =
    holder.status === "ready" ? holder.activeHolder.accountHolderUid : "";

  const mutation = useMutation({
    ...postApiV1PaymentsMutation(),
    meta: { handledLocally: true },
    onSuccess: (response, variables) => {
      setInsufficientFunds(false);
      const accountUid = (variables as { body: { fromAccountId: string } }).body
        .fromAccountId;
      invalidateAccountMoneyData(queryClient, activeHolderUid, accountUid);
      if (response.state === "SETTLED") {
        toast.success("Transfer sent.");
      }
      options.onSuccess?.(response);
    },
    onError: (error) => {
      if (isHttpStatus(error, 401)) return;
      if (isInsufficientFunds(error)) {
        setInsufficientFunds(true);
        return;
      }
      toast.error(GENERIC_ERROR_MESSAGE);
    },
  });

  return {
    transfer: (input) => {
      setInsufficientFunds(false);
      const body: {
        fromAccountId: string;
        toSortCode: string;
        toAccountNumber: string;
        amountMinorUnits: number;
        currency: "GBP";
        description?: string;
        copVerdictOverride?: boolean;
        toName?: string;
      } = {
        fromAccountId: input.fromAccountUid,
        toSortCode: input.toSortCode,
        toAccountNumber: input.toAccountNumber,
        amountMinorUnits: input.amountMinorUnits,
        currency: "GBP",
      };
      if (input.description !== undefined && input.description.length > 0) {
        body.description = input.description;
      }
      if (input.copVerdictOverride === true) {
        body.copVerdictOverride = true;
      }
      const beneficiaryName = input.toName?.trim() ?? "";
      if (beneficiaryName.length > 0) {
        body.toName = beneficiaryName;
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
    insufficientFunds,
    reset: () => setInsufficientFunds(false),
  };
}
