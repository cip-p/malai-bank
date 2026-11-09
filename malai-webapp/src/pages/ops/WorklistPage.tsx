import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import type { MdrDecisionSummary } from "@/api/generated/types.gen";
import { Button } from "@/components/ui/button";
import {
  dataTableBodyClassName,
  dataTableCellClassName,
  dataTableClassName,
  dataTableHeadClassName,
  dataTableRowClassName,
} from "@/components/dataTable";
import { ScrollRegion } from "@/components/ScrollRegion";
import { OutcomeTag } from "@/ops/decisionTags";
import { formatCostMicros } from "@/ops/formatCost";
import { formatDecisionAge } from "@/ops/formatDecisionAge";
import { formatDecisionTimestamp } from "@/ops/formatDecisionTimestamp";
import { useMdrDecisions } from "@/ops/useMdrDecisions";
import { useWorklistCounts } from "@/ops/useWorklistCounts";
import {
  WORKLIST_VIEWS,
  worklistFilter,
  type WorklistView,
} from "@/ops/worklistViews";
import { verdictHighlight } from "@/ops/verdictHighlight";
import { useFocusOnMount } from "@/lib/useFocusOnMount";
import { cn } from "@/lib/utils";
import { usePageTitle } from "@/lib/usePageTitle";

export function WorklistPage() {
  usePageTitle("Worklist");
  const headingRef = useFocusOnMount<HTMLHeadingElement>();
  const [view, setView] = useState<WorklistView>("AWAITING_RULING");
  const { decisions, isPending, isError, refetch } = useMdrDecisions(
    worklistFilter(view),
  );
  const counts = useWorklistCounts();

  return (
    <section className="mx-auto w-full max-w-6xl px-6 py-6">
      <h1
        ref={headingRef}
        tabIndex={-1}
        className="text-2xl font-semibold tracking-tight outline-none"
      >
        Worklist
      </h1>
      <div
        role="group"
        aria-label="Worklist view"
        data-testid="worklist-filter"
        className="mt-4 flex flex-wrap gap-2"
      >
        {WORKLIST_VIEWS.map((choice) => (
          <ViewChip
            key={choice.view}
            label={choice.label}
            testId={choice.testId}
            selected={choice.view === view}
            count={counts[choice.view]}
            onSelect={() => setView(choice.view)}
          />
        ))}
      </div>
      <div className="mt-6">
        {renderBody(decisions, isPending, isError, view, refetch)}
      </div>
    </section>
  );
}

function ViewChip({
  label,
  testId,
  selected,
  count,
  onSelect,
}: {
  label: string;
  testId: string;
  selected: boolean;
  count: number | undefined;
  onSelect: () => void;
}) {
  return (
    <Button
      type="button"
      size="sm"
      variant={selected ? "default" : "outline"}
      aria-pressed={selected}
      data-testid={testId}
      onClick={onSelect}
    >
      {label}
      {count === undefined ? null : (
        <span className="text-xs font-normal tabular-nums">{count}</span>
      )}
    </Button>
  );
}

function renderBody(
  decisions: MdrDecisionSummary[] | null,
  isPending: boolean,
  isError: boolean,
  view: WorklistView,
  retry: () => void,
) {
  if (isPending) return <WorklistSkeleton />;
  if (isError) return <WorklistError retry={retry} />;
  const rows = ordered(decisions ?? [], view);
  if (rows.length === 0) return <WorklistEmpty view={view} />;
  return <WorklistTable rows={rows} />;
}

function ordered(
  rows: MdrDecisionSummary[],
  view: WorklistView,
): MdrDecisionSummary[] {
  if (view !== "AWAITING_RULING") return rows;
  return [...rows].sort((a, b) => a.seq - b.seq);
}

function WorklistTable({ rows }: { rows: MdrDecisionSummary[] }) {
  return (
    <ScrollRegion label="Model decisions">
      <table data-testid="worklist-table" className={dataTableClassName}>
        <caption className="sr-only">
          Model decisions in this view, with the verdict each reached
        </caption>
        <thead className={dataTableHeadClassName}>
          <tr className="border-b border-border text-left text-muted-foreground">
            <th scope="col" className="py-2 pr-4 font-medium">
              When
            </th>
            <th scope="col" className="py-2 pr-4 font-medium">
              Correlation
            </th>
            <th scope="col" className="py-2 pr-4 font-medium">
              Verdict
            </th>
            <th scope="col" className="py-2 pr-4 font-medium">
              Outcome
            </th>
            <th scope="col" className="py-2 pr-4 text-right font-medium">
              Rulings
            </th>
            <th scope="col" className="py-2 pr-4 font-medium">
              Model
            </th>
            <th scope="col" className="py-2 pr-4 text-right font-medium">
              Latency
            </th>
            <th scope="col" className="py-2 text-right font-medium">
              Cost
            </th>
          </tr>
        </thead>
        <tbody className={dataTableBodyClassName}>
          {rows.map((row) => (
            <WorklistRow key={row.decisionId} row={row} />
          ))}
        </tbody>
      </table>
    </ScrollRegion>
  );
}

function WorklistRow({ row }: { row: MdrDecisionSummary }) {
  const navigate = useNavigate();
  const to = `/ops/decisions/${row.decisionId}`;

  return (
    <tr
      data-testid="worklist-row"
      onClick={() => void navigate(to)}
      className={cn(dataTableRowClassName, "cursor-pointer hover:bg-muted/40")}
    >
      <td
        data-testid="worklist-ts"
        data-label="When"
        className={cn(dataTableCellClassName, "py-3 pr-4 whitespace-nowrap")}
      >
        <Link
          to={to}
          data-testid="worklist-link"
          onClick={(event) => event.stopPropagation()}
          className="font-medium underline decoration-border underline-offset-4 outline-none hover:decoration-foreground focus-visible:decoration-foreground"
        >
          {formatDecisionAge(row.ts)}
        </Link>
        <span className="mt-0.5 block text-xs text-muted-foreground tabular-nums">
          {formatDecisionTimestamp(row.ts)}
        </span>
      </td>
      <td
        data-testid="worklist-correlation"
        data-label="Correlation"
        className={cn(dataTableCellClassName, "py-3 pr-4")}
      >
        <span
          className="block max-w-56 truncate"
          title={`${row.correlationType} · ${row.correlationId}`}
        >
          <span className="text-xs font-medium">{row.correlationType}</span>
          <span className="font-mono text-xs text-muted-foreground">
            {" "}
            · {row.correlationId}
          </span>
        </span>
      </td>
      <td
        data-testid="worklist-verdict"
        data-label="Verdict"
        className={cn(dataTableCellClassName, "py-3 pr-4")}
      >
        <VerdictCell verdict={row.verdict} />
      </td>
      <td
        data-label="Outcome"
        className={cn(dataTableCellClassName, "py-3 pr-4")}
      >
        <OutcomeTag outcome={row.outcome} testId="worklist-outcome" />
      </td>
      <td
        data-testid="worklist-dispositions"
        data-label="Rulings"
        className={cn(
          dataTableCellClassName,
          "py-3 pr-4 text-right tabular-nums",
        )}
      >
        {row.dispositionCount}
      </td>
      <td
        data-testid="worklist-model"
        data-label="Model"
        className={cn(
          dataTableCellClassName,
          "py-3 pr-4 text-xs text-muted-foreground",
        )}
      >
        {row.modelId} · {row.modelVersion}
      </td>
      <td
        data-testid="worklist-latency"
        data-label="Latency"
        className={cn(
          dataTableCellClassName,
          "py-3 pr-4 text-right text-xs whitespace-nowrap text-muted-foreground tabular-nums",
        )}
      >
        {new Intl.NumberFormat("en-GB").format(row.latencyMs)} ms
      </td>
      <td
        data-testid="worklist-cost"
        data-label="Cost"
        className={cn(
          dataTableCellClassName,
          "py-3 text-right text-xs whitespace-nowrap text-muted-foreground tabular-nums",
        )}
      >
        {formatCostMicros(row.costMicros, row.costCurrency)}
      </td>
    </tr>
  );
}

function VerdictCell({ verdict }: { verdict: MdrDecisionSummary["verdict"] }) {
  if (!verdict) return <span className="text-muted-foreground">—</span>;
  const { conclusion, detail } = verdictHighlight(verdict);
  if (conclusion !== null || detail !== null) {
    return (
      <span className="whitespace-nowrap">
        {conclusion !== null ? (
          <span className="font-medium">{conclusion}</span>
        ) : null}
        {detail !== null ? (
          <span className="text-muted-foreground">
            {conclusion !== null ? " · " : ""}
            {detail}
          </span>
        ) : null}
      </span>
    );
  }
  return (
    <span
      className="block max-w-48 truncate font-mono text-xs text-muted-foreground"
      title={JSON.stringify(verdict)}
    >
      {JSON.stringify(verdict)}
    </span>
  );
}

const EMPTY_COPY: Record<WorklistView, string> = {
  AWAITING_RULING: "Nothing awaiting a ruling.",
  ACCEPT: "No decisions with this ruling in force.",
  EDIT: "No decisions with this ruling in force.",
  REJECT: "No decisions with this ruling in force.",
  ADVISORY: "No advisory decisions recorded.",
};

function WorklistEmpty({ view }: { view: WorklistView }) {
  return (
    <p
      data-testid="worklist-empty"
      className="rounded-2xl border border-dashed border-border px-6 py-8 text-center text-sm text-muted-foreground"
    >
      {EMPTY_COPY[view]}
    </p>
  );
}

function WorklistSkeleton() {
  return (
    <div data-testid="worklist-skeleton" aria-busy="true" className="space-y-2">
      <span className="sr-only">Loading the worklist</span>
      <div className="h-10 rounded-md bg-muted/40 motion-safe:animate-pulse" />
      <div className="h-10 rounded-md bg-muted/40 motion-safe:animate-pulse" />
      <div className="h-10 rounded-md bg-muted/40 motion-safe:animate-pulse" />
    </div>
  );
}

function WorklistError({ retry }: { retry: () => void }) {
  return (
    <div
      data-testid="worklist-error"
      role="alert"
      className="space-y-3 rounded-2xl border border-dashed border-border px-6 py-8 text-center"
    >
      <p className="text-sm text-muted-foreground">
        Couldn&rsquo;t load the worklist.
      </p>
      <Button type="button" data-testid="worklist-retry" onClick={retry}>
        Try again
      </Button>
    </div>
  );
}
