import { useRef } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  getApiV1AdminAmlCasesByCaseIdQueryKey,
  postApiV1AdminAmlCasesByCaseIdSarDraftMutation,
} from "@/api/generated/@tanstack/react-query.gen";
import type { AmlSarDraftResponse } from "@/api/generated/types.gen";
import { mintIdempotencyKey } from "@/lib/mintIdempotencyKey";
import type { SarDraftInvocation } from "@/ops/sarPanelState";

export type UseSarDraftResult = {
  invocation: SarDraftInvocation;
  requestDraft: () => void;
};

export function useSarDraft(caseId: string): UseSarDraftResult {
  const invocationKey = useRef<string | null>(null);
  const inFlight = useRef(false);
  const queryClient = useQueryClient();
  const mutation = useMutation({
    ...postApiV1AdminAmlCasesByCaseIdSarDraftMutation(),
    meta: { handledLocally: true },
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: getApiV1AdminAmlCasesByCaseIdQueryKey({ path: { caseId } }),
      });
    },
  });

  const requestDraft = () => {
    if (inFlight.current) return;
    inFlight.current = true;
    invocationKey.current ??= mintIdempotencyKey();
    mutation.mutate(
      {
        path: { caseId },
        headers: { "Idempotency-Key": invocationKey.current },
      },
      {
        onSettled: () => {
          inFlight.current = false;
        },
      },
    );
  };

  return { invocation: invocationOf(mutation), requestDraft };
}

function invocationOf(mutation: {
  isPending: boolean;
  isError: boolean;
  data: AmlSarDraftResponse | undefined;
}): SarDraftInvocation {
  if (mutation.isPending) return { kind: "drafting" };
  if (mutation.isError) return { kind: "failed" };
  if (mutation.data === undefined) return { kind: "idle" };
  return { kind: "served", response: mutation.data };
}
