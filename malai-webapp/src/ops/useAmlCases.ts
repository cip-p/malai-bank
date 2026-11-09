import { useQuery } from "@tanstack/react-query";
import { getApiV1AdminAmlCasesOptions } from "@/api/generated/@tanstack/react-query.gen";
import type { AmlCaseStatus, AmlCaseSummary } from "@/api/generated/types.gen";

export const CASE_LIST_REFETCH_MS = 5_000;

export type CaseDepths = Record<AmlCaseStatus, number>;

export type UseAmlCasesResult = {
  cases: AmlCaseSummary[] | null;
  depths: CaseDepths;
  total: number;
  isPending: boolean;
  isError: boolean;
  error: unknown;
  refetch: () => void;
};

const NO_DEPTHS: CaseDepths = {
  OPEN: 0,
  INVESTIGATING: 0,
  CLOSED_DISMISSED: 0,
  CLOSED_REPORTED: 0,
};

export function amlCasesQueryOptions() {
  return {
    ...getApiV1AdminAmlCasesOptions(),
    refetchInterval: CASE_LIST_REFETCH_MS,
    refetchIntervalInBackground: false,
  };
}

export function useAmlCases(status: AmlCaseStatus | null): UseAmlCasesResult {
  const query = useQuery(amlCasesQueryOptions());
  const refetch = () => {
    void query.refetch();
  };

  if (query.isPending) {
    return {
      cases: null,
      depths: NO_DEPTHS,
      total: 0,
      isPending: true,
      isError: false,
      error: null,
      refetch,
    };
  }
  if (query.isError) {
    return {
      cases: null,
      depths: NO_DEPTHS,
      total: 0,
      isPending: false,
      isError: true,
      error: query.error,
      refetch,
    };
  }

  const served = query.data?.cases ?? [];
  return {
    cases: status === null ? served : served.filter((c) => c.status === status),
    depths: depthsOf(served),
    total: served.length,
    isPending: false,
    isError: false,
    error: null,
    refetch,
  };
}

function depthsOf(cases: AmlCaseSummary[]): CaseDepths {
  const depths: CaseDepths = { ...NO_DEPTHS };
  for (const summary of cases) {
    depths[summary.status] += 1;
  }
  return depths;
}
