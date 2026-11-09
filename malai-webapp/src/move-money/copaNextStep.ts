import type { ConfirmationOfPayeeResponse } from "@/api/generated/types.gen";

export type CopaIntent = "transfer" | "save-payee";

export type CopaUiState =
  | { kind: "MATCH" }
  | { kind: "CLOSE_MATCH"; onRecordName: string; intent: CopaIntent }
  | { kind: "NO_MATCH"; intent: CopaIntent };

export function copaNextStep(
  response: ConfirmationOfPayeeResponse,
  intent: CopaIntent,
): CopaUiState {
  if (response.result === "MATCH") return { kind: "MATCH" };
  if (response.result === "NO_MATCH") return { kind: "NO_MATCH", intent };
  return {
    kind: "CLOSE_MATCH",
    onRecordName: response.onRecordName ?? "",
    intent,
  };
}
