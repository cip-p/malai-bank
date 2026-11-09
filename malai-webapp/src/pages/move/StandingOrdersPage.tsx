import { useState } from "react";
import { Link } from "react-router-dom";
import { Repeat } from "lucide-react";
import type {
  AccountResponse,
  PayeeResponse,
  StandingOrderResponse,
} from "@/api/generated/types.gen";
import { Button } from "@/components/ui/button";
import { useAccounts } from "@/accounts/useAccounts";
import { useStandingOrders } from "@/move-money/useStandingOrders";
import { usePayees } from "@/move-money/usePayees";
import { useCancelStandingOrder } from "@/move-money/useCancelStandingOrder";
import { StandingOrderRow } from "@/move-money/StandingOrderRow";
import { CancelStandingOrderDialog } from "@/move-money/CancelStandingOrderDialog";
import { sortByNextDate } from "@/move-money/sortByNextDate";
import { StatusPanel } from "@/components/StatusPanel";
import { useFocusOnMount } from "@/lib/useFocusOnMount";
import { usePageTitle } from "@/lib/usePageTitle";

type Props = { now?: Date };

const EMPTY_HEADLINE = "No active standing orders";
const EMPTY_BODY =
  "A standing order is a recurring payment you set up from your account on a fixed schedule.";

export function StandingOrdersPage({ now }: Props = {}) {
  usePageTitle("Standing Orders");
  const headingRef = useFocusOnMount<HTMLHeadingElement>();
  const { standingOrders, isPending, isError } = useStandingOrders();
  const { accounts } = useAccounts();
  const { payees } = usePayees();
  const { cancelStandingOrder } = useCancelStandingOrder();
  const [pendingCancel, setPendingCancel] =
    useState<StandingOrderResponse | null>(null);
  const effectiveNow = now ?? new Date();

  return (
    <section>
      <div className="flex items-start justify-between gap-4">
        <h1
          ref={headingRef}
          tabIndex={-1}
          className="text-2xl font-semibold tracking-tight outline-none"
        >
          Standing Orders
        </h1>
        <Button asChild data-testid="so-header-add">
          <Link to="/move/standing-orders/new">New standing order</Link>
        </Button>
      </div>
      <div className="mt-6">
        {renderBody(
          standingOrders,
          isPending,
          isError,
          accounts,
          payees,
          effectiveNow,
          setPendingCancel,
        )}
      </div>
      {pendingCancel ? (
        <CancelStandingOrderDialog
          open
          standingOrder={pendingCancel}
          onCancel={() => setPendingCancel(null)}
          onConfirm={() => {
            cancelStandingOrder(pendingCancel.standingOrderId);
            setPendingCancel(null);
          }}
        />
      ) : null}
    </section>
  );
}

function renderBody(
  standingOrders: StandingOrderResponse[] | null,
  isPending: boolean,
  isError: boolean,
  accounts: AccountResponse[] | null,
  payees: PayeeResponse[] | null,
  now: Date,
  onCancel: (so: StandingOrderResponse) => void,
) {
  if (isPending) return <StandingOrdersSkeleton />;
  if (isError) {
    return (
      <StatusPanel
        testId="so-error"
        role="alert"
        title="Couldn’t load standing orders"
        body="Please try again in a moment."
      />
    );
  }
  const items = (standingOrders ?? []).filter(
    (so) => so.status !== "CANCELLED",
  );
  if (items.length === 0) {
    return (
      <div
        data-testid="so-empty"
        className="flex flex-col items-center gap-4 rounded-2xl border border-dashed border-border px-6 py-12 text-center"
      >
        <Repeat
          aria-hidden="true"
          className="h-10 w-10 text-muted-foreground"
        />
        <div className="space-y-1.5">
          <p className="text-base font-medium">{EMPTY_HEADLINE}</p>
          <p className="max-w-md text-sm text-muted-foreground">{EMPTY_BODY}</p>
        </div>
        <Button asChild data-testid="so-empty-add">
          <Link to="/move/standing-orders/new">New standing order</Link>
        </Button>
      </div>
    );
  }

  const accountsList = accounts ?? [];
  const showChip = accountsList.length >= 2;
  const nameByUid = new Map(accountsList.map((a) => [a.accountUid, a.name]));
  const knownPayeeIds = new Set((payees ?? []).map((p) => p.payeeId));

  const sorted = sortByNextDate(
    items.map((so) => ({
      ...so,
      nextDate: so.nextPaymentAt ?? null,
      name: so.payeeName,
    })),
  );

  return (
    <ul data-testid="so-list" className="divide-y divide-border">
      {sorted.map((so) => (
        <StandingOrderRow
          key={so.standingOrderId}
          so={so}
          now={now}
          accountLabel={
            showChip ? (nameByUid.get(so.accountId) ?? undefined) : undefined
          }
          payeeHref={
            so.payeeId && knownPayeeIds.has(so.payeeId)
              ? `/move/payees#${so.payeeId}`
              : undefined
          }
          onCancel={onCancel}
        />
      ))}
    </ul>
  );
}

function StandingOrdersSkeleton() {
  return (
    <div data-testid="so-skeleton" aria-busy="true" className="space-y-3">
      <span className="sr-only">Loading standing orders</span>
      <div className="h-12 rounded-md bg-muted/40 motion-safe:animate-pulse" />
      <div className="h-12 rounded-md bg-muted/40 motion-safe:animate-pulse" />
      <div className="h-12 rounded-md bg-muted/40 motion-safe:animate-pulse" />
    </div>
  );
}
