import type { MdrDecisionFilter } from "./useMdrDecisions";

export type WorklistView =
  | "AWAITING_RULING"
  | "ACCEPT"
  | "EDIT"
  | "REJECT"
  | "ADVISORY";

export const WORKLIST_VIEWS: {
  view: WorklistView;
  label: string;
  testId: string;
}[] = [
  {
    view: "AWAITING_RULING",
    label: "Awaiting ruling",
    testId: "worklist-filter-awaiting",
  },
  { view: "ACCEPT", label: "Accept", testId: "worklist-filter-accept" },
  { view: "EDIT", label: "Edit", testId: "worklist-filter-edit" },
  { view: "REJECT", label: "Reject", testId: "worklist-filter-reject" },
  { view: "ADVISORY", label: "Advisory", testId: "worklist-filter-advisory" },
];

export function worklistFilter(view: WorklistView): MdrDecisionFilter {
  if (view === "ADVISORY") return { advisory: true };
  return { rulingState: view, advisory: false };
}
