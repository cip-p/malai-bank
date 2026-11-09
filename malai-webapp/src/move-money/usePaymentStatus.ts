import { useEffect, useRef } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { getApiV1PaymentsByPaymentIdOptions } from "@/api/generated/@tanstack/react-query.gen";
import type { PaymentResponse } from "@/api/generated/types.gen";
import { useActiveAccountHolder } from "@/account-holder/useActiveAccountHolder";
import { invalidateAccountMoneyData } from "@/accounts/invalidateAccountMoneyData";
import { isInFlight, pollIntervalFor } from "./paymentLifecycle";

export type UsePaymentStatusResult = {
  payment: PaymentResponse;
};

export function usePaymentStatus(
  initial: PaymentResponse,
): UsePaymentStatusResult {
  const holder = useActiveAccountHolder();
  const queryClient = useQueryClient();
  const activeHolderUid =
    holder.status === "ready" ? holder.activeHolder.accountHolderUid : "";

  const query = useQuery({
    ...getApiV1PaymentsByPaymentIdOptions({
      path: { paymentId: initial.paymentId },
      headers: { "MAL-Account-Holder-Id": activeHolderUid },
    }),
    initialData: initial,
    enabled: holder.status === "ready" && isInFlight(initial.state),
    refetchInterval: (q) => {
      const state = q.state.data?.state;
      return state ? (pollIntervalFor(state) ?? false) : false;
    },
  });

  const payment = query.data ?? initial;

  const terminalHandled = useRef(false);
  useEffect(() => {
    if (terminalHandled.current) return;
    if (!isInFlight(initial.state)) return;
    if (isInFlight(payment.state)) return;
    terminalHandled.current = true;
    if (payment.fromAccountId) {
      invalidateAccountMoneyData(
        queryClient,
        activeHolderUid,
        payment.fromAccountId,
      );
    }
  }, [
    initial.state,
    payment.state,
    payment.fromAccountId,
    queryClient,
    activeHolderUid,
  ]);

  return { payment };
}
