import { useQuery } from "@tanstack/react-query";
import { getApiV1SandboxCounterpartiesOptions } from "@/api/generated/@tanstack/react-query.gen";
import type { CounterpartyResponse } from "@/api/generated/types.gen";

export type UseCounterpartiesResult = {
  counterparties: CounterpartyResponse[] | null;
  isPending: boolean;
  isError: boolean;
  error: unknown;
};

export function useCounterparties(): UseCounterpartiesResult {
  const query = useQuery(getApiV1SandboxCounterpartiesOptions());

  if (query.isPending) {
    return {
      counterparties: null,
      isPending: true,
      isError: false,
      error: null,
    };
  }
  if (query.isError) {
    return {
      counterparties: null,
      isPending: false,
      isError: true,
      error: query.error,
    };
  }
  return {
    counterparties: query.data?.counterparties ?? [],
    isPending: false,
    isError: false,
    error: null,
  };
}
