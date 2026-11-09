import type { AmlCaseStatus, AmlTypology } from "@/api/generated/types.gen";
import { cn } from "@/lib/utils";

export const CASE_STATUS_LABEL: Record<AmlCaseStatus, string> = {
  OPEN: "Open",
  INVESTIGATING: "Investigating",
  CLOSED_DISMISSED: "Closed dismissed",
  CLOSED_REPORTED: "Closed reported",
};

export const TYPOLOGY_LABEL: Record<AmlTypology, string> = {
  STRUCTURING: "Structuring",
  RAPID_IN_OUT: "Rapid in-out",
  HIGH_RISK_CORRIDOR: "High-risk corridor",
};

const CASE_STATUS_TONE: Record<AmlCaseStatus, string> = {
  OPEN: "border-border text-foreground",
  INVESTIGATING: "border-ring text-foreground",
  CLOSED_DISMISSED: "border-dashed border-border text-muted-foreground",
  CLOSED_REPORTED: "border-destructive/40 text-destructive",
};

export function isTerminal(status: AmlCaseStatus): boolean {
  return status === "CLOSED_DISMISSED" || status === "CLOSED_REPORTED";
}

export function CaseStatusTag({
  status,
  testId,
}: {
  status: AmlCaseStatus;
  testId: string;
}) {
  return (
    <span
      data-testid={testId}
      className={cn(
        "inline-flex items-center rounded-sm border px-1.5 py-0.5 text-xs font-medium whitespace-nowrap",
        CASE_STATUS_TONE[status],
      )}
    >
      {CASE_STATUS_LABEL[status]}
    </span>
  );
}
