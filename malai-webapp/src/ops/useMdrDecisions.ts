import { useQuery } from "@tanstack/react-query";
import { getApiV1AdminMdrDecisionsOptions } from "@/api/generated/@tanstack/react-query.gen";
import type { MdrDecisionSummary } from "@/api/generated/types.gen";

export type RulingState = "AWAITING_RULING" | "ACCEPT" | "EDIT" | "REJECT";

export type MdrDecisionFilter = {
  rulingState?: RulingState;
  advisory?: boolean;
};

export type UseMdrDecisionsResult = {
  decisions: MdrDecisionSummary[] | null;
  isPending: boolean;
  isError: boolean;
  error: unknown;
  refetch: () => void;
};

export function mdrDecisionsQueryOptions({
  rulingState,
  advisory,
}: MdrDecisionFilter) {
  return getApiV1AdminMdrDecisionsOptions({
    query: {
      ...(rulingState === undefined ? {} : { rulingState }),
      ...(advisory === undefined ? {} : { advisory }),
    },
  });
}

export function useMdrDecisions(
  filter: MdrDecisionFilter,
): UseMdrDecisionsResult {
  const query = useQuery(mdrDecisionsQueryOptions(filter));
  const refetch = () => {
    void query.refetch();
  };

  if (query.isPending) {
    return {
      decisions: null,
      isPending: true,
      isError: false,
      error: null,
      refetch,
    };
  }
  if (query.isError) {
    return {
      decisions: null,
      isPending: false,
      isError: true,
      error: query.error,
      refetch,
    };
  }

  return {
    decisions: query.data?.decisions ?? [],
    isPending: false,
    isError: false,
    error: null,
    refetch,
  };
}
