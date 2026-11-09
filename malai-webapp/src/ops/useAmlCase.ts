import { useQuery } from "@tanstack/react-query";
import { getApiV1AdminAmlCasesByCaseIdOptions } from "@/api/generated/@tanstack/react-query.gen";
import type { AmlCaseDetailResponse } from "@/api/generated/types.gen";
import { isHttpStatus } from "@/api/http";

export const CASE_DETAIL_REFETCH_MS = 5_000;

export type UseAmlCaseResult = {
  detail: AmlCaseDetailResponse | null;
  isPending: boolean;
  isNotFound: boolean;
  isError: boolean;
  error: unknown;
  refetch: () => void;
};

type PollingQuery = { state: { status: "pending" | "error" | "success" } };

export function amlCaseQueryOptions(caseId: string) {
  return {
    ...getApiV1AdminAmlCasesByCaseIdOptions({ path: { caseId } }),
    refetchInterval: (query: PollingQuery) =>
      query.state.status === "error" ? false : CASE_DETAIL_REFETCH_MS,
    refetchIntervalInBackground: false,
  };
}

export function useAmlCase(caseId: string): UseAmlCaseResult {
  const query = useQuery(amlCaseQueryOptions(caseId));
  const refetch = () => {
    void query.refetch();
  };

  if (query.isError) {
    const notFound = isHttpStatus(query.error, 404);
    return {
      detail: null,
      isPending: false,
      isNotFound: notFound,
      isError: !notFound,
      error: notFound ? null : query.error,
      refetch,
    };
  }

  return {
    detail: query.data ?? null,
    isPending: query.isPending,
    isNotFound: false,
    isError: false,
    error: null,
    refetch,
  };
}
