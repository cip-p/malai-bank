import { useRef } from "react";
import { useMutation } from "@tanstack/react-query";
import { postApiV1AdminCopilotExplanationsMutation } from "@/api/generated/@tanstack/react-query.gen";
import type { Claim, ExplanationResponse } from "@/api/generated/types.gen";
import { mintIdempotencyKey } from "@/lib/mintIdempotencyKey";

export type ExplanationState =
  | { kind: "idle" }
  | { kind: "asking" }
  | { kind: "grounded"; decisionId: string; claims: Claim[] }
  | { kind: "refused"; decisionId: string }
  | {
      kind: "no-answer";
      decisionId: string;
      outcome: Exclude<ExplanationResponse["outcome"], "COMPLETED">;
    }
  | { kind: "ask-failed" };

export type UseExplanationResult = {
  state: ExplanationState;
  explain: () => void;
};

export function useExplanation(subjectId: string): UseExplanationResult {
  const invocationKey = useRef<string | null>(null);
  const inFlight = useRef(false);
  const mutation = useMutation({
    ...postApiV1AdminCopilotExplanationsMutation(),
    meta: { handledLocally: true },
  });

  const explain = () => {
    if (inFlight.current) return;
    inFlight.current = true;
    invocationKey.current ??= mintIdempotencyKey();
    mutation.mutate(
      {
        body: { subjectType: "PAYMENT", subjectId },
        headers: { "Idempotency-Key": invocationKey.current },
      },
      {
        onSettled: () => {
          inFlight.current = false;
        },
      },
    );
  };

  return { state: stateOf(mutation), explain };
}

function stateOf(mutation: {
  isPending: boolean;
  isError: boolean;
  data: ExplanationResponse | undefined;
}): ExplanationState {
  if (mutation.isPending) return { kind: "asking" };
  if (mutation.isError) return { kind: "ask-failed" };
  const served = mutation.data;
  if (served === undefined) return { kind: "idle" };
  if (served.outcome !== "COMPLETED") {
    return {
      kind: "no-answer",
      decisionId: served.decisionId,
      outcome: served.outcome,
    };
  }
  if (served.claims.length > 0) {
    return {
      kind: "grounded",
      decisionId: served.decisionId,
      claims: served.claims,
    };
  }
  return { kind: "refused", decisionId: served.decisionId };
}
