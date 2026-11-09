import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  getApiV1AdminMdrDecisionsByDecisionIdQueryKey,
  getApiV1AdminMdrDecisionsQueryKey,
  postApiV1AdminMdrDecisionsByDecisionIdDispositionsMutation,
} from "@/api/generated/@tanstack/react-query.gen";

const WORKLIST_QUERY_ID = (
  getApiV1AdminMdrDecisionsQueryKey()[0] as { _id: string }
)._id;

export type UseRulingDraftResult = {
  isOpen: boolean;
  entryId: string | null;
  isError: boolean;
  isPending: boolean;
  open: () => void;
  discard: () => void;
  appendAccept: () => void;
  appendEdit: (replacementOutput: string) => void;
  appendReject: () => void;
};

export function useRulingDraft(decisionId: string): UseRulingDraftResult {
  const [entryId, setEntryId] = useState<string | null>(null);
  const queryClient = useQueryClient();

  const mutation = useMutation({
    ...postApiV1AdminMdrDecisionsByDecisionIdDispositionsMutation(),
    meta: { handledLocally: true },
    onSuccess: () => {
      setEntryId(null);
      void queryClient.invalidateQueries({
        queryKey: getApiV1AdminMdrDecisionsByDecisionIdQueryKey({
          path: { decisionId },
        }),
      });
      void queryClient.invalidateQueries({
        predicate: (query) =>
          (query.queryKey[0] as { _id?: string } | undefined)?._id ===
          WORKLIST_QUERY_ID,
      });
    },
  });

  const append = (body: {
    disposition: "ACCEPT" | "EDIT" | "REJECT";
    editedOutput?: string;
  }) => {
    if (entryId === null) return;
    mutation.mutate({ path: { decisionId }, body: { entryId, ...body } });
  };

  return {
    isOpen: entryId !== null,
    entryId,
    isError: mutation.isError,
    isPending: mutation.isPending,
    open: () => {
      mutation.reset();
      setEntryId(crypto.randomUUID());
    },
    discard: () => {
      mutation.reset();
      setEntryId(null);
    },
    appendAccept: () => append({ disposition: "ACCEPT" }),
    appendEdit: (replacementOutput) =>
      append({ disposition: "EDIT", editedOutput: replacementOutput }),
    appendReject: () => append({ disposition: "REJECT" }),
  };
}
