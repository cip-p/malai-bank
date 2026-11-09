import type {
  HumanDispositionKind,
  MdrModelDecisionView,
} from "@/api/generated/types.gen";
import { cn } from "@/lib/utils";

export type MdrOutcome = MdrModelDecisionView["outcome"];

const OUTCOME_LABEL: Record<MdrOutcome, string> = {
  COMPLETED: "Completed",
  TIMED_OUT: "Timed out",
  ERRORED: "Errored",
};

const OUTCOME_TONE: Record<MdrOutcome, string> = {
  COMPLETED: "border-border text-foreground",
  TIMED_OUT: "border-destructive/40 text-destructive",
  ERRORED: "border-destructive/40 text-destructive",
};

const RULING_LABEL: Record<HumanDispositionKind, string> = {
  ACCEPT: "Accept",
  EDIT: "Edit",
  REJECT: "Reject",
};

export function OutcomeTag({
  outcome,
  testId,
}: {
  outcome: MdrOutcome;
  testId: string;
}) {
  return (
    <Tag
      testId={testId}
      className={OUTCOME_TONE[outcome]}
      label={OUTCOME_LABEL[outcome]}
    />
  );
}

export function RulingTag({
  ruling,
  testId,
}: {
  ruling: HumanDispositionKind | undefined;
  testId: string;
}) {
  if (!ruling) {
    return (
      <Tag
        testId={testId}
        className="border-dashed border-border text-muted-foreground"
        label="Awaiting ruling"
      />
    );
  }
  return (
    <Tag
      testId={testId}
      className="border-border text-foreground"
      label={RULING_LABEL[ruling]}
    />
  );
}

function Tag({
  testId,
  className,
  label,
}: {
  testId: string;
  className: string;
  label: string;
}) {
  return (
    <span
      data-testid={testId}
      className={cn(
        "inline-flex items-center rounded-sm border px-1.5 py-0.5 text-xs font-medium whitespace-nowrap",
        className,
      )}
    >
      {label}
    </span>
  );
}
