import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import type { AmlCaseStatus, AmlCaseSummary } from "@/api/generated/types.gen";
import { Button } from "@/components/ui/button";
import {
  dataTableBodyClassName,
  dataTableCellClassName,
  dataTableClassName,
  dataTableHeadClassName,
  dataTableRowClassName,
} from "@/components/dataTable";
import { ScrollRegion } from "@/components/ScrollRegion";
import { CASE_STATUS_LABEL, CaseStatusTag } from "@/ops/caseTags";
import { formatDecisionAge } from "@/ops/formatDecisionAge";
import { formatDecisionTimestamp } from "@/ops/formatDecisionTimestamp";
import { useAmlCases } from "@/ops/useAmlCases";
import { useFocusOnMount } from "@/lib/useFocusOnMount";
import { cn } from "@/lib/utils";
import { usePageTitle } from "@/lib/usePageTitle";

const CASE_STATUS_CHIPS: { status: AmlCaseStatus; testId: string }[] = [
  { status: "OPEN", testId: "cases-filter-open" },
  { status: "INVESTIGATING", testId: "cases-filter-investigating" },
  { status: "CLOSED_DISMISSED", testId: "cases-filter-dismissed" },
  { status: "CLOSED_REPORTED", testId: "cases-filter-reported" },
];

export function CasesPage() {
  usePageTitle("Cases");
  const headingRef = useFocusOnMount<HTMLHeadingElement>();
  const [status, setStatus] = useState<AmlCaseStatus | null>(null);
  const { cases, depths, total, isPending, isError, refetch } =
    useAmlCases(status);

  return (
    <section className="mx-auto w-full max-w-6xl px-6 py-6">
      <h1
        ref={headingRef}
        tabIndex={-1}
        className="text-2xl font-semibold tracking-tight outline-none"
      >
        Cases
      </h1>
      <p className="mt-1 text-sm text-muted-foreground">
        One investigation per account. Monitoring opens a Case when a pattern is
        detected and links every Alert it raises to it.
      </p>
      <div
        role="group"
        aria-label="Case status"
        data-testid="cases-filter"
        className="mt-4 flex flex-wrap gap-2"
      >
        {CASE_STATUS_CHIPS.map((chip) => (
          <StatusChip
            key={chip.status}
            status={chip.status}
            testId={chip.testId}
            depth={depths[chip.status]}
            selected={chip.status === status}
            onSelect={() =>
              setStatus((current) =>
                current === chip.status ? null : chip.status,
              )
            }
          />
        ))}
        {status === null ? null : (
          <Button
            type="button"
            size="sm"
            variant="ghost"
            data-testid="cases-filter-all"
            onClick={() => setStatus(null)}
          >
            Show all cases
            <span className="text-xs font-normal tabular-nums">{total}</span>
          </Button>
        )}
      </div>
      <div className="mt-6">
        {renderBody(cases, isPending, isError, status, refetch)}
      </div>
    </section>
  );
}

function StatusChip({
  status,
  testId,
  depth,
  selected,
  onSelect,
}: {
  status: AmlCaseStatus;
  testId: string;
  depth: number;
  selected: boolean;
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
      {CASE_STATUS_LABEL[status]}
      <span className="text-xs font-normal tabular-nums">{depth}</span>
    </Button>
  );
}

function renderBody(
  cases: AmlCaseSummary[] | null,
  isPending: boolean,
  isError: boolean,
  status: AmlCaseStatus | null,
  retry: () => void,
) {
  if (isPending) return <CasesSkeleton />;
  if (isError) return <CasesError retry={retry} />;
  const rows = cases ?? [];
  if (rows.length === 0) return <CasesEmpty status={status} />;
  return <CasesTable rows={rows} />;
}

function CasesTable({ rows }: { rows: AmlCaseSummary[] }) {
  return (
    <ScrollRegion label="AML cases">
      <table data-testid="cases-table" className={dataTableClassName}>
        <caption className="sr-only">
          AML cases in this view, newest opened first
        </caption>
        <thead className={dataTableHeadClassName}>
          <tr className="border-b border-border text-left text-muted-foreground">
            <th scope="col" className="py-2 pr-4 font-medium">
              Case
            </th>
            <th scope="col" className="py-2 pr-4 font-medium">
              Account
            </th>
            <th scope="col" className="py-2 pr-4 font-medium">
              Status
            </th>
            <th scope="col" className="py-2 pr-4 text-right font-medium">
              Alerts
            </th>
            <th scope="col" className="py-2 pr-4 font-medium">
              Opened
            </th>
            <th scope="col" className="py-2 font-medium">
              Updated
            </th>
          </tr>
        </thead>
        <tbody className={dataTableBodyClassName}>
          {rows.map((row) => (
            <CaseRow key={row.caseId} row={row} />
          ))}
        </tbody>
      </table>
    </ScrollRegion>
  );
}

function CaseRow({ row }: { row: AmlCaseSummary }) {
  const navigate = useNavigate();
  const to = `/ops/cases/${row.caseId}`;

  return (
    <tr
      data-testid="case-row"
      onClick={() => void navigate(to)}
      className={cn(dataTableRowClassName, "cursor-pointer hover:bg-muted/40")}
    >
      <td data-label="Case" className={cn(dataTableCellClassName, "py-3 pr-4")}>
        <Link
          to={to}
          data-testid="case-link"
          onClick={(event) => event.stopPropagation()}
          className="underline decoration-border underline-offset-4 outline-none hover:decoration-foreground focus-visible:decoration-foreground"
        >
          <span
            data-testid="case-id"
            title={row.caseId}
            className="font-mono text-xs font-medium whitespace-nowrap"
          >
            {row.caseId.slice(0, 8)}
          </span>
        </Link>
      </td>
      <td
        data-label="Account"
        className={cn(dataTableCellClassName, "py-3 pr-4")}
      >
        <span
          data-testid="case-account"
          title={row.accountId}
          className="font-mono text-xs whitespace-nowrap text-muted-foreground"
        >
          {row.accountId.slice(0, 8)}
        </span>
      </td>
      <td
        data-label="Status"
        className={cn(dataTableCellClassName, "py-3 pr-4")}
      >
        <CaseStatusTag status={row.status} testId="case-status" />
      </td>
      <td
        data-testid="case-alert-count"
        data-label="Alerts"
        className={cn(
          dataTableCellClassName,
          "py-3 pr-4 text-right tabular-nums",
        )}
      >
        {row.alertCount}
      </td>
      <td
        data-testid="case-opened"
        data-label="Opened"
        className={cn(dataTableCellClassName, "py-3 pr-4 whitespace-nowrap")}
      >
        <span className="font-medium">{formatDecisionAge(row.openedAt)}</span>
        <span className="mt-0.5 block text-xs text-muted-foreground tabular-nums">
          {formatDecisionTimestamp(row.openedAt)}
        </span>
      </td>
      <td
        data-testid="case-updated"
        title={formatDecisionTimestamp(row.updatedAt)}
        data-label="Updated"
        className={cn(
          dataTableCellClassName,
          "py-3 whitespace-nowrap text-muted-foreground",
        )}
      >
        {formatDecisionAge(row.updatedAt)}
      </td>
    </tr>
  );
}

function CasesEmpty({ status }: { status: AmlCaseStatus | null }) {
  return (
    <p
      data-testid="cases-empty"
      className="rounded-2xl border border-dashed border-border px-6 py-8 text-center text-sm text-muted-foreground"
    >
      {status === null
        ? "No investigations. Monitoring opens a Case the moment it finds a pattern."
        : `No cases ${CASE_STATUS_LABEL[status].toLowerCase()}.`}
    </p>
  );
}

function CasesSkeleton() {
  return (
    <div data-testid="cases-skeleton" aria-busy="true" className="space-y-2">
      <span className="sr-only">Loading the cases</span>
      <div className="h-10 rounded-md bg-muted/40 motion-safe:animate-pulse" />
      <div className="h-10 rounded-md bg-muted/40 motion-safe:animate-pulse" />
      <div className="h-10 rounded-md bg-muted/40 motion-safe:animate-pulse" />
    </div>
  );
}

function CasesError({ retry }: { retry: () => void }) {
  return (
    <div
      data-testid="cases-error"
      role="alert"
      className="space-y-3 rounded-2xl border border-dashed border-border px-6 py-8 text-center"
    >
      <p className="text-sm text-muted-foreground">
        Couldn&rsquo;t load the cases.
      </p>
      <Button type="button" data-testid="cases-retry" onClick={retry}>
        Try again
      </Button>
    </div>
  );
}
