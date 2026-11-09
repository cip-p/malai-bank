import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  getApiV1AccountsByAccountUidTransactionsOptions,
  postApiV1SandboxCardPurchasesMutation,
} from "@/api/generated/@tanstack/react-query.gen";
import type { SandboxCardPurchaseResponse } from "@/api/generated/types.gen";
import { isHttpStatus } from "@/api/http";
import { useActiveAccountHolder } from "@/account-holder/useActiveAccountHolder";
import { formatSchemeDeclineCode } from "@/cards/formatDeclineReason";
import { invalidateCreditAccountData } from "@/cards/invalidateCreditAccountData";
import { useRecentCardPurchasesStore } from "./useRecentCardPurchasesStore";

export type CardPurchaseInput = {
  cardId: string;
  merchantId: string;
  merchantName: string;
  amountMinorUnits: number;
  accountUid: string;
};

export type UseCardPurchaseMutationResult = {
  submit: (input: CardPurchaseInput) => void;
  isPending: boolean;
  outcome: SandboxCardPurchaseResponse | null;
  reset: () => void;
};

const GENERIC_ERROR_MESSAGE = "Something went wrong. Please try again.";

export function useCardPurchaseMutation(): UseCardPurchaseMutationResult {
  const holder = useActiveAccountHolder();
  const queryClient = useQueryClient();
  const record = useRecentCardPurchasesStore((s) => s.record);
  const [outcome, setOutcome] = useState<SandboxCardPurchaseResponse | null>(
    null,
  );
  const activeHolderUid =
    holder.status === "ready" ? holder.activeHolder.accountHolderUid : "";

  const mutation = useMutation({
    ...postApiV1SandboxCardPurchasesMutation(),
    meta: { handledLocally: true },
    onError: (error) => {
      if (isHttpStatus(error, 401)) return;
      toast.error(GENERIC_ERROR_MESSAGE);
    },
  });

  function captureAuthorisation(input: CardPurchaseInput) {
    const headers = { "MAL-Account-Holder-Id": activeHolderUid };
    void queryClient
      .fetchQuery(
        getApiV1AccountsByAccountUidTransactionsOptions({
          path: { accountUid: input.accountUid },
          headers,
        }),
      )
      .then((data) => {
        const row = data.transactions.find(
          (tx) => tx.status === "PENDING" && tx.cardId === input.cardId,
        );
        if (!row) return;
        record({
          authorisationId: row.entryId,
          accountUid: input.accountUid,
          cardId: input.cardId,
          merchantName: row.merchantName ?? input.merchantName,
          amountMinorUnits: row.amountMinorUnits,
          currency: row.currency,
        });
      })
      .catch(() => undefined);
  }

  return {
    submit: (input) => {
      setOutcome(null);
      mutation.mutate(
        {
          body: {
            cardId: input.cardId,
            merchantId: input.merchantId,
            amountMinorUnits: input.amountMinorUnits,
          },
          headers: { "MAL-Account-Holder-Id": activeHolderUid },
        },
        {
          onSuccess: (response) => {
            setOutcome(response);
            invalidateCreditAccountData(
              queryClient,
              activeHolderUid,
              input.accountUid,
            );
            if (response.outcome === "APPROVED") {
              toast.success("Purchase approved.");
              captureAuthorisation(input);
            } else {
              toast.error(
                `Purchase declined — ${formatSchemeDeclineCode(response.declineReasonCode)}.`,
              );
            }
          },
        },
      );
    },
    isPending: mutation.isPending,
    outcome,
    reset: () => setOutcome(null),
  };
}
