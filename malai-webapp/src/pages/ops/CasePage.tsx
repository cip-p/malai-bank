import { Link, useParams } from "react-router-dom";
import type {
  AmlAlertView,
  AmlCaseDetailResponse,
  AmlEvidencePayment,
  Rail,
} from "@/api/generated/types.gen";
import { Button } from "@/components/ui/button";
import {
  dataTableBodyClassName,
  dataTableCellClassName,
  dataTableClassName,
  dataTableHeadClassName,
  dataTableRowClassName,
} from "@/components/dataTable";
import { ScrollRegion } from "@/components/ScrollRegion";
import { CopyButton } from "@/components/CopyButton";
import { CaseStatusTag, TYPOLOGY_LABEL } from "@/ops/caseTags";
import { CloseActions } from "@/ops/CloseActions";
import { RulingTag } from "@/ops/decisionTags";
import { SarPanel } from "@/ops/SarPanel";
import { formatDecisionAge } from "@/ops/formatDecisionAge";
import { formatDecisionTimestamp } from "@/ops/formatDecisionTimestamp";
import { useAmlCase } from "@/ops/useAmlCase";
import { formatCurrency } from "@/lib/formatCurrency";
import { cn } from "@/lib/utils";
import { useFocusOnMount } from "@/lib/useFocusOnMount";
import { usePageTitle } from "@/lib/usePageTitle";

const RAIL_LABEL: Record<Rail, string> = {
  BOOK_TRANSFER: "Book transfer",
  FPS: "Faster Payments",
  SWIFT: "SWIFT",
  BACS_DIRECT_CREDIT: "Bacs direct credit",
};

const DIRECTION_LABEL: Record<AmlEvidencePayment["direction"], string> = {
  OUTBOUND: "Outbound",
  INBOUND: "Inbound",
};

const HASH_PREFIX = 12;

export function CasePage() {
  const { caseId = "" } = useParams<{ caseId: string }>();
  const { detail, isPending, isNotFound, isError, refetch } =
    useAmlCase(caseId);
  usePageTitle(
    isNotFound
      ? "Case not found"
      : detail
        ? `Case · ${detail.caseId.slice(0, 8)}`
        : "Case",
  );

  if (isNotFound) return <CaseNotFound />;

  return (
    <section className="mx-auto w-full max-w-5xl px-6 py-6">
      <CasesBackLink />
      <CaseHeading text="Case" />
      {isPending ? (
        <CaseSkeleton />
      ) : isError ? (
        <CaseError retry={refetch} />
      ) : detail ? (
        <CaseRecord detail={detail} />
      ) : null}
    </section>
  );
}

function CasesBackLink() {
  return (
    <Link
      to="/ops/cases"
      data-testid="case-back-link"
      className="text-sm text-muted-foreground hover:underline focus-visible:underline"
    >
      ← Cases
    </Link>
  );
}

function CaseHeading({ text }: { text: string }) {
  const headingRef = useFocusOnMount<HTMLHeadingElement>();
  return (
    <h1
      ref={headingRef}
      tabIndex={-1}
      className="mt-3 text-2xl font-semibold tracking-tight outline-none"
    >
      {text}
    </h1>
  );
}

function CaseNotFound() {
  return (
    <section className="mx-auto w-full max-w-5xl px-6 py-6">
      <CasesBackLink />
      <CaseHeading text="Case not found" />
      <p
        data-testid="case-not-found"
        className="mt-2 max-w-md text-sm text-muted-foreground"
      >
        No investigation with this id exists on the record. The link may be
        wrong, or it may point at a different environment.
      </p>
    </section>
  );
}

function CaseSkeleton() {
  return (
    <div
      data-testid="case-skeleton"
      aria-busy="true"
      className="mt-6 space-y-3"
    >
      <span className="sr-only">Loading the case</span>
      <div className="h-6 w-1/2 rounded-md bg-muted/40 motion-safe:animate-pulse" />
      <div className="h-32 rounded-md bg-muted/40 motion-safe:animate-pulse" />
      <div className="h-32 rounded-md bg-muted/40 motion-safe:animate-pulse" />
    </div>
  );
}

function CaseError({ retry }: { retry: () => void }) {
  return (
    <div
      data-testid="case-error"
      role="alert"
      className="mt-6 space-y-3 rounded-2xl border border-dashed border-border px-6 py-8 text-center"
    >
      <p className="text-sm text-muted-foreground">
        Couldn&rsquo;t load the case.
      </p>
      <Button type="button" data-testid="case-retry" onClick={retry}>
        Try again
      </Button>
    </div>
  );
}

function CaseRecord({ detail }: { detail: AmlCaseDetailResponse }) {
  return (
    <div className="mt-2">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
        <span
          data-testid="case-id"
          title={detail.caseId}
          className="flex items-center gap-1 font-mono text-xs text-muted-foreground"
        >
          case {detail.caseId.slice(0, 8)}
          <CopyButton value={detail.caseId} label="Copy case id" />
        </span>
        <CaseStatusTag status={detail.status} testId="case-status" />
        <span
          data-testid="case-account"
          title={detail.accountId}
          className="font-mono text-xs text-muted-foreground"
        >
          account {detail.accountId.slice(0, 8)}
        </span>
      </div>
      <dl className="mt-4 grid grid-cols-1 gap-x-6 gap-y-3 rounded-lg border border-border px-4 py-3 text-sm sm:grid-cols-2">
        <div data-testid="case-opened" className="min-w-0">
          <dt className="text-xs font-medium text-muted-foreground">Opened</dt>
          <dd className="mt-0.5">
            <span className="font-medium">
              {formatDecisionAge(detail.openedAt)}
            </span>
            <span className="mt-0.5 block text-xs text-muted-foreground tabular-nums">
              {formatDecisionTimestamp(detail.openedAt)}
            </span>
          </dd>
        </div>
        <div data-testid="case-updated" className="min-w-0">
          <dt className="text-xs font-medium text-muted-foreground">Updated</dt>
          <dd className="mt-0.5">
            <span className="font-medium">
              {formatDecisionAge(detail.updatedAt)}
            </span>
            <span className="mt-0.5 block text-xs text-muted-foreground tabular-nums">
              {formatDecisionTimestamp(detail.updatedAt)}
            </span>
          </dd>
        </div>
      </dl>
      <AlertsSection alerts={detail.alerts} caseId={detail.caseId} />
      <SarPanel detail={detail} />
      <CloseActions detail={detail} />
    </div>
  );
}

function AlertsSection({
  alerts,
  caseId,
}: {
  alerts: AmlAlertView[];
  caseId: string;
}) {
  return (
    <section aria-labelledby="case-alerts-heading" className="mt-8">
      <h2 id="case-alerts-heading" className="text-lg font-semibold">
        Alerts
      </h2>
      <p className="mt-1 text-sm text-muted-foreground">
        What monitoring found, oldest first, with the settled payments each
        pattern was found over. Ruling happens on the Alert&rsquo;s decision.
      </p>
      {alerts.length === 0 ? (
        <p
          data-testid="case-alerts-empty"
          className="mt-3 rounded-lg border border-dashed border-border px-4 py-3 text-sm text-muted-foreground"
        >
          No Alerts are linked to this Case.
        </p>
      ) : (
        <ul className="mt-3 space-y-4">
          {alerts.map((alert) => (
            <li key={alert.decisionId}>
              <AlertCard alert={alert} caseId={caseId} />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function AlertCard({ alert, caseId }: { alert: AmlAlertView; caseId: string }) {
  return (
    <article
      data-testid="case-alert"
      className="rounded-lg border border-border px-4 py-3"
    >
      <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
        <span data-testid="alert-typology" className="font-medium">
          {TYPOLOGY_LABEL[alert.typology]}
        </span>
        <span
          data-testid="alert-risk-score"
          className="text-xs text-muted-foreground tabular-nums"
        >
          risk {alert.riskScore}
        </span>
        <RulingTag ruling={alert.rulingInForce} testId="alert-ruling" />
        <span
          data-testid="alert-raised"
          className="text-xs text-muted-foreground tabular-nums"
        >
          {formatDecisionTimestamp(alert.raisedAt)}
        </span>
        <Link
          to={`/ops/cases/${caseId}/decisions/${alert.decisionId}`}
          data-testid="alert-decision-link"
          className="ml-auto text-sm underline decoration-border underline-offset-4 outline-none hover:decoration-foreground focus-visible:decoration-foreground"
        >
          Open decision
        </Link>
      </div>
      <EvidenceTable evidence={alert.evidence} />
    </article>
  );
}

function EvidenceTable({ evidence }: { evidence: AmlEvidencePayment[] }) {
  if (evidence.length === 0) {
    return (
      <p
        data-testid="evidence-empty"
        className="mt-3 rounded-lg border border-dashed border-border px-4 py-3 text-sm text-muted-foreground"
      >
        No evidence payments are recorded on this Alert.
      </p>
    );
  }

  return (
    <ScrollRegion label="Evidence payments" className="mt-3">
      <table data-testid="evidence-table" className={dataTableClassName}>
        <caption className="sr-only">
          The settled payments this Alert was raised over
        </caption>
        <thead className={dataTableHeadClassName}>
          <tr className="border-b border-border text-left text-muted-foreground">
            <th scope="col" className="py-2 pr-4 font-medium">
              Rail
            </th>
            <th scope="col" className="py-2 pr-4 font-medium">
              Direction
            </th>
            <th scope="col" className="py-2 pr-4 text-right font-medium">
              Amount
            </th>
            <th scope="col" className="py-2 pr-4 font-medium">
              Counterparty
            </th>
            <th scope="col" className="py-2 pr-4 font-medium">
              Made
            </th>
            <th scope="col" className="py-2 font-medium">
              Content hash
            </th>
          </tr>
        </thead>
        <tbody className={dataTableBodyClassName}>
          {evidence.map((payment) => (
            <EvidenceRow key={payment.paymentId} payment={payment} />
          ))}
        </tbody>
      </table>
    </ScrollRegion>
  );
}

function EvidenceRow({ payment }: { payment: AmlEvidencePayment }) {
  return (
    <tr data-testid="evidence-row" className={dataTableRowClassName}>
      <td
        data-testid="evidence-rail"
        data-label="Rail"
        className={cn(dataTableCellClassName, "py-2.5 pr-4 whitespace-nowrap")}
      >
        {RAIL_LABEL[payment.rail]}
      </td>
      <td
        data-testid="evidence-direction"
        data-label="Direction"
        className={cn(
          dataTableCellClassName,
          "py-2.5 pr-4 whitespace-nowrap text-muted-foreground",
        )}
      >
        {DIRECTION_LABEL[payment.direction]}
      </td>
      <td
        data-testid="evidence-amount"
        data-label="Amount"
        className={cn(
          dataTableCellClassName,
          "py-2.5 pr-4 text-right font-medium whitespace-nowrap tabular-nums",
        )}
      >
        {formatCurrency(payment.amountMinorUnits, payment.currency)}
      </td>
      <td
        data-testid="evidence-counterparty"
        data-label="Counterparty"
        className={cn(dataTableCellClassName, "py-2.5 pr-4")}
      >
        <Counterparty payment={payment} />
      </td>
      <td
        data-testid="evidence-created"
        data-label="Made"
        className={cn(
          dataTableCellClassName,
          "py-2.5 pr-4 text-xs whitespace-nowrap text-muted-foreground tabular-nums",
        )}
      >
        {formatDecisionTimestamp(payment.createdAt)}
      </td>
      <td
        data-label="Content hash"
        className={cn(dataTableCellClassName, "py-2.5")}
      >
        <span className="flex items-center gap-1">
          <span
            data-testid="evidence-hash"
            title={payment.contentHash}
            className="font-mono text-xs text-muted-foreground"
          >
            {payment.contentHash.slice(0, HASH_PREFIX)}
          </span>
          <CopyButton value={payment.contentHash} label="Copy content hash" />
        </span>
      </td>
    </tr>
  );
}

function Counterparty({ payment }: { payment: AmlEvidencePayment }) {
  const identifiers = [
    payment.beneficiarySortCode,
    payment.beneficiaryAccountNo,
    payment.beneficiaryIban,
    payment.beneficiaryBic,
  ].filter((value): value is string => value !== undefined);

  if (payment.beneficiaryName === undefined && identifiers.length === 0) {
    return <span className="text-muted-foreground">—</span>;
  }

  return (
    <span className="block">
      {payment.beneficiaryName === undefined ? null : (
        <span className="block">{payment.beneficiaryName}</span>
      )}
      {identifiers.length === 0 ? null : (
        <span className="block font-mono text-xs break-all text-muted-foreground">
          {identifiers.join(" · ")}
        </span>
      )}
    </span>
  );
}
