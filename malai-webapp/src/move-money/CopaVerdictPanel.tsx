import { Button } from "@/components/ui/button";
import type { CopaUiState } from "./copaNextStep";

type Props = {
  state: CopaUiState;
  enteredName: string;
  onAccept: () => void;
  onUseOnRecord?: () => void;
  onEdit: () => void;
  onSendAnyway?: () => void;
};

export function CopaVerdictPanel({
  state,
  enteredName,
  onAccept,
  onUseOnRecord,
  onEdit,
  onSendAnyway,
}: Props) {
  if (state.kind === "MATCH") return null;

  if (state.kind === "CLOSE_MATCH") {
    return (
      <div
        data-testid="copa-panel"
        data-verdict="CLOSE_MATCH"
        role="status"
        className="space-y-3 rounded-lg border border-border bg-muted/30 p-4"
      >
        <p data-testid="copa-message" className="text-sm">
          On record as <strong>{state.onRecordName}</strong> — you entered{" "}
          <strong>{enteredName}</strong>
        </p>
        <div className="flex flex-wrap gap-2">
          <Button
            type="button"
            data-testid="copa-use-on-record"
            onClick={onUseOnRecord}
          >
            Use the on-record name
          </Button>
          <Button
            type="button"
            variant="outline"
            data-testid="copa-accept"
            onClick={onAccept}
          >
            Keep what I typed and continue
          </Button>
        </div>
      </div>
    );
  }

  const sendAnywayLabel =
    state.intent === "transfer" ? "Send anyway" : "Save anyway";

  return (
    <div
      data-testid="copa-panel"
      data-verdict="NO_MATCH"
      role="alert"
      className="space-y-3 rounded-lg border border-destructive/40 bg-destructive/10 p-4"
    >
      <p data-testid="copa-message" className="text-sm">
        We couldn’t verify this account. Please double-check the details.
      </p>
      <div className="flex flex-wrap gap-2">
        <Button
          type="button"
          variant="outline"
          data-testid="copa-edit"
          onClick={onEdit}
        >
          Edit details
        </Button>
        <Button
          type="button"
          variant="destructive"
          data-testid="copa-send-anyway"
          onClick={onSendAnyway}
        >
          {sendAnywayLabel}
        </Button>
      </div>
    </div>
  );
}
