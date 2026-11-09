import { useState } from "react";
import { Link } from "react-router-dom";
import { UsersRound } from "lucide-react";
import type { PayeeResponse } from "@/api/generated/types.gen";
import { Button } from "@/components/ui/button";
import { StatusPanel } from "@/components/StatusPanel";
import { useFocusOnMount } from "@/lib/useFocusOnMount";
import { usePageTitle } from "@/lib/usePageTitle";
import { usePayees } from "@/move-money/usePayees";
import { useStandingOrders } from "@/move-money/useStandingOrders";
import { useDeletePayee } from "@/move-money/useDeletePayee";
import { PayeeRow } from "@/move-money/PayeeRow";
import { DeletePayeeDialog } from "@/move-money/DeletePayeeDialog";
import { findStandingOrdersByPayee } from "@/move-money/findStandingOrdersByPayee";

const EMPTY_HEADLINE = "No saved payees yet";
const EMPTY_BODY =
  "Save someone you pay regularly so you don’t have to re-enter their details next time.";

export function PayeesPage() {
  usePageTitle("Payees");
  const headingRef = useFocusOnMount<HTMLHeadingElement>();
  const { payees, isPending, isError } = usePayees();
  const { standingOrders } = useStandingOrders();
  const { deletePayee } = useDeletePayee();
  const [pendingDelete, setPendingDelete] = useState<PayeeResponse | null>(
    null,
  );

  const linkedStandingOrders = pendingDelete
    ? findStandingOrdersByPayee(standingOrders ?? [], pendingDelete.payeeId)
    : [];

  return (
    <section>
      <div className="flex items-start justify-between gap-4">
        <h1
          ref={headingRef}
          tabIndex={-1}
          className="text-2xl font-semibold tracking-tight outline-none"
        >
          Payees
        </h1>
        <Button asChild data-testid="payees-header-add">
          <Link to="/move/payees/new">Add payee</Link>
        </Button>
      </div>
      <div className="mt-6">
        {renderBody(payees, isPending, isError, setPendingDelete)}
      </div>
      {pendingDelete ? (
        <DeletePayeeDialog
          open
          payee={pendingDelete}
          linkedStandingOrders={linkedStandingOrders}
          onCancel={() => setPendingDelete(null)}
          onConfirm={() => {
            deletePayee(pendingDelete.payeeId);
            setPendingDelete(null);
          }}
        />
      ) : null}
    </section>
  );
}

function renderBody(
  payees: PayeeResponse[] | null,
  isPending: boolean,
  isError: boolean,
  onDelete: (payee: PayeeResponse) => void,
) {
  if (isPending) return <PayeesSkeleton />;
  if (isError) {
    return (
      <StatusPanel
        testId="payees-error"
        role="alert"
        title="Couldn’t load payees"
        body="Please try again in a moment."
      />
    );
  }
  const items = payees ?? [];
  if (items.length === 0) {
    return (
      <div
        data-testid="payees-empty"
        className="flex flex-col items-center gap-4 rounded-2xl border border-dashed border-border px-6 py-12 text-center"
      >
        <UsersRound
          aria-hidden="true"
          className="h-10 w-10 text-muted-foreground"
        />
        <div className="space-y-1.5">
          <p className="text-base font-medium">{EMPTY_HEADLINE}</p>
          <p className="max-w-md text-sm text-muted-foreground">{EMPTY_BODY}</p>
        </div>
        <Button asChild data-testid="payees-empty-add">
          <Link to="/move/payees/new">Add payee</Link>
        </Button>
      </div>
    );
  }

  const sorted = [...items].sort((a, b) =>
    (a.nickname ?? a.name).localeCompare(b.nickname ?? b.name),
  );

  return (
    <ul data-testid="payees-list" className="divide-y divide-border">
      {sorted.map((p) => (
        <PayeeRow key={p.payeeId} payee={p} onDelete={onDelete} />
      ))}
    </ul>
  );
}

function PayeesSkeleton() {
  return (
    <div data-testid="payees-skeleton" aria-busy="true" className="space-y-3">
      <span className="sr-only">Loading payees</span>
      <div className="h-12 rounded-md bg-muted/40 motion-safe:animate-pulse" />
      <div className="h-12 rounded-md bg-muted/40 motion-safe:animate-pulse" />
      <div className="h-12 rounded-md bg-muted/40 motion-safe:animate-pulse" />
    </div>
  );
}
