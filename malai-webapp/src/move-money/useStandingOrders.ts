import { useQuery } from "@tanstack/react-query";
import { getApiV1StandingOrdersOptions } from "@/api/generated/@tanstack/react-query.gen";
import type { StandingOrderResponse } from "@/api/generated/types.gen";
import { useActiveAccountHolder } from "@/account-holder/useActiveAccountHolder";

export type UseStandingOrdersResult = {
  standingOrders: StandingOrderResponse[] | null;
  isPending: boolean;
  isError: boolean;
  error: unknown;
};

export function useStandingOrders(): UseStandingOrdersResult {
  const holder = useActiveAccountHolder();
  const activeHolderUid =
    holder.status === "ready" ? holder.activeHolder.accountHolderUid : "";

  const query = useQuery({
    ...getApiV1StandingOrdersOptions({
      headers: { "MAL-Account-Holder-Id": activeHolderUid },
    }),
    enabled: holder.status === "ready",
  });

  if (holder.status === "loading") {
    return {
      standingOrders: null,
      isPending: true,
      isError: false,
      error: null,
    };
  }
  if (holder.status === "error") {
    return {
      standingOrders: null,
      isPending: false,
      isError: true,
      error: holder.error,
    };
  }

  if (query.isPending) {
    return {
      standingOrders: null,
      isPending: true,
      isError: false,
      error: null,
    };
  }
  if (query.isError) {
    return {
      standingOrders: null,
      isPending: false,
      isError: true,
      error: query.error,
    };
  }

  return {
    standingOrders: query.data?.standingOrders ?? [],
    isPending: false,
    isError: false,
    error: null,
  };
}
