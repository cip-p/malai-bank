import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  getApiV1AdminAmlCasesByCaseIdQueryKey,
  getApiV1AdminAmlCasesQueryKey,
  postApiV1AdminAmlCasesByCaseIdCloseDismissedMutation,
  postApiV1AdminAmlCasesByCaseIdCloseReportedMutation,
} from "@/api/generated/@tanstack/react-query.gen";
import { closeOutcome, type CloseOutcome } from "@/ops/closeRefusal";

const CASE_LIST_QUERY_ID = (
  getApiV1AdminAmlCasesQueryKey()[0] as { _id: string }
)._id;

export type CloseAction = "dismissed" | "reported";

export type UseCloseCaseResult = {
  close: (action: CloseAction, idempotencyKey: string) => void;
  pending: CloseAction | null;
  outcome: CloseOutcome | null;
};

export function useCloseCase(caseId: string): UseCloseCaseResult {
  const [action, setAction] = useState<CloseAction | null>(null);
  const queryClient = useQueryClient();
  const onSuccess = () => {
    void queryClient.invalidateQueries({
      queryKey: getApiV1AdminAmlCasesByCaseIdQueryKey({ path: { caseId } }),
    });
    void queryClient.invalidateQueries({
      predicate: (query) =>
        (query.queryKey[0] as { _id?: string } | undefined)?._id ===
        CASE_LIST_QUERY_ID,
    });
  };
  const dismissed = useMutation({
    ...postApiV1AdminAmlCasesByCaseIdCloseDismissedMutation(),
    meta: { handledLocally: true },
    onSuccess,
  });
  const reported = useMutation({
    ...postApiV1AdminAmlCasesByCaseIdCloseReportedMutation(),
    meta: { handledLocally: true },
    onSuccess,
  });
  const attempt = action === "reported" ? reported : dismissed;

  const close = (fired: CloseAction, idempotencyKey: string) => {
    setAction(fired);
    const mutation = fired === "reported" ? reported : dismissed;
    mutation.mutate({
      path: { caseId },
      headers: { "Idempotency-Key": idempotencyKey },
    });
  };

  return {
    close,
    pending: action !== null && attempt.isPending ? action : null,
    outcome:
      action !== null && attempt.isError ? closeOutcome(attempt.error) : null,
  };
}
