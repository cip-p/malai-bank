import type {
  AmlCaseStatus,
  AmlSarDraftResponse,
  AmlSarView,
  HumanDispositionKind,
} from "@/api/generated/types.gen";
import { isTerminal } from "@/ops/caseTags";

export type SarDraftInvocation =
  | { kind: "idle" }
  | { kind: "drafting" }
  | { kind: "served"; response: AmlSarDraftResponse }
  | { kind: "failed" };

export type SarPanelState =
  | { kind: "requestable" }
  | { kind: "never-drafted" }
  | { kind: "drafting" }
  | { kind: "grounded"; decisionId: string; narrative: string }
  | { kind: "refused"; decisionId: string }
  | {
      kind: "no-answer";
      decisionId: string;
      outcome: Exclude<AmlSarDraftResponse["outcome"], "COMPLETED">;
    }
  | { kind: "request-failed" }
  | {
      kind: "on-record";
      decisionId: string;
      rulingInForce: HumanDispositionKind | undefined;
    }
  | { kind: "attached"; decisionId: string; narrative: string };

export function sarPanelState(
  status: AmlCaseStatus,
  sar: AmlSarView | undefined,
  invocation: SarDraftInvocation,
): SarPanelState {
  if (status === "CLOSED_REPORTED" && sar?.narrative !== undefined) {
    return {
      kind: "attached",
      decisionId: sar.decisionId,
      narrative: sar.narrative,
    };
  }
  if (invocation.kind === "drafting") return { kind: "drafting" };
  if (invocation.kind === "failed") return { kind: "request-failed" };
  if (invocation.kind === "served") return servedState(invocation.response);
  if (sar !== undefined) {
    return {
      kind: "on-record",
      decisionId: sar.decisionId,
      rulingInForce: sar.rulingInForce,
    };
  }
  return isTerminal(status)
    ? { kind: "never-drafted" }
    : { kind: "requestable" };
}

function servedState(response: AmlSarDraftResponse): SarPanelState {
  if (response.outcome !== "COMPLETED") {
    return {
      kind: "no-answer",
      decisionId: response.decisionId,
      outcome: response.outcome,
    };
  }
  if (response.narrative === undefined) {
    return { kind: "refused", decisionId: response.decisionId };
  }
  return {
    kind: "grounded",
    decisionId: response.decisionId,
    narrative: response.narrative,
  };
}
