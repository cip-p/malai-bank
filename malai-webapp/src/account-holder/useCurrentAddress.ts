import { useQuery } from "@tanstack/react-query";
import { getApiV1AddressesOptions } from "@/api/generated/@tanstack/react-query.gen";
import type { AddressV2Response } from "@/api/generated/types.gen";
import { useActiveAccountHolder } from "@/account-holder/useActiveAccountHolder";

export type UseCurrentAddressResult = {
  address: AddressV2Response | null;
  isPending: boolean;
  isError: boolean;
  error: unknown;
};

export function useCurrentAddress(): UseCurrentAddressResult {
  const holder = useActiveAccountHolder();
  const activeHolderUid =
    holder.status === "ready" ? holder.activeHolder.accountHolderUid : "";

  const query = useQuery({
    ...getApiV1AddressesOptions({
      headers: { "MAL-Account-Holder-Id": activeHolderUid },
    }),
    enabled: holder.status === "ready",
  });

  if (holder.status === "loading") {
    return { address: null, isPending: true, isError: false, error: null };
  }
  if (holder.status === "error") {
    return {
      address: null,
      isPending: false,
      isError: true,
      error: holder.error,
    };
  }

  if (query.isPending) {
    return { address: null, isPending: true, isError: false, error: null };
  }
  if (query.isError) {
    return {
      address: null,
      isPending: false,
      isError: true,
      error: query.error,
    };
  }

  return {
    address: query.data?.current ?? null,
    isPending: false,
    isError: false,
    error: null,
  };
}
