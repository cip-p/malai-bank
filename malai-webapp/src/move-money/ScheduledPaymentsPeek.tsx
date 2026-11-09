import { Link } from "react-router-dom";
import type {
  DirectDebitResponse,
  StandingOrderResponse,
} from "@/api/generated/types.gen";
import { useDirectDebits } from "./useDirectDebits";
import { useStandingOrders } from "./useStandingOrders";
import { DirectDebitRow } from "./DirectDebitRow";
import { StandingOrderRow } from "./StandingOrderRow";
import { sortByNextDate } from "./sortByNextDate";

type Props = {
  accountUid: string;
  now?: Date;
};

export function ScheduledPaymentsPeek({ accountUid, now }: Props) {
  const dd = useDirectDebits();
  const so = useStandingOrders();
  const effectiveNow = now ?? new Date();

  return (
    <section className="mt-6 rounded-xl border border-border bg-card p-6 shadow-xs">
      <h2 className="text-lg font-semibold">
        Scheduled payments on this account
      </h2>

      <div className="mt-4">
        <h3 className="text-sm font-medium">Direct Debits</h3>
        <div className="mt-2">
          {renderDdBody(
            dd.directDebits,
            dd.isPending,
            dd.isError,
            accountUid,
            effectiveNow,
          )}
        </div>
        <div className="mt-3">
          <Link
            to="/move/direct-debits"
            className="text-sm text-primary underline-offset-4 hover:underline focus-visible:underline"
          >
            View all direct debits
          </Link>
        </div>
      </div>

      <div className="mt-6">
        <h3 className="text-sm font-medium">Standing Orders</h3>
        <div className="mt-2">
          {renderSoBody(
            so.standingOrders,
            so.isPending,
            so.isError,
            accountUid,
            effectiveNow,
          )}
        </div>
        <div className="mt-3">
          <Link
            to="/move/standing-orders"
            className="text-sm text-primary underline-offset-4 hover:underline focus-visible:underline"
          >
            View all standing orders
          </Link>
        </div>
      </div>
    </section>
  );
}

function renderDdBody(
  directDebits: DirectDebitResponse[] | null,
  isPending: boolean,
  isError: boolean,
  accountUid: string,
  now: Date,
) {
  if (isPending) return <DdSkeleton />;
  if (isError) {
    return (
      <p data-testid="peek-dd-error" className="text-sm text-muted-foreground">
        Couldn’t load direct debits
      </p>
    );
  }
  const filtered = (directDebits ?? []).filter(
    (d) => d.accountId === accountUid && d.status !== "CANCELLED",
  );
  if (filtered.length === 0) {
    return (
      <p data-testid="peek-dd-empty" className="text-sm text-muted-foreground">
        No direct debits on this account.
      </p>
    );
  }

  const sorted = sortByNextDate(
    filtered.map((d) => ({
      ...d,
      nextDate: d.nextCollectionAt ?? null,
      name: d.creditorName,
    })),
  );

  return (
    <ul data-testid="peek-dd-list" className="divide-y divide-border">
      {sorted.map((d) => (
        <DirectDebitRow key={d.directDebitId} dd={d} now={now} />
      ))}
    </ul>
  );
}

function renderSoBody(
  standingOrders: StandingOrderResponse[] | null,
  isPending: boolean,
  isError: boolean,
  accountUid: string,
  now: Date,
) {
  if (isPending) return <SoSkeleton />;
  if (isError) {
    return (
      <p data-testid="peek-so-error" className="text-sm text-muted-foreground">
        Couldn’t load standing orders
      </p>
    );
  }
  const filtered = (standingOrders ?? []).filter(
    (s) => s.accountId === accountUid && s.status !== "CANCELLED",
  );
  if (filtered.length === 0) {
    return (
      <p data-testid="peek-so-empty" className="text-sm text-muted-foreground">
        No standing orders on this account.
      </p>
    );
  }

  const sorted = sortByNextDate(
    filtered.map((s) => ({
      ...s,
      nextDate: s.nextPaymentAt ?? null,
      name: s.payeeName,
    })),
  );

  return (
    <ul data-testid="peek-so-list" className="divide-y divide-border">
      {sorted.map((s) => (
        <StandingOrderRow key={s.standingOrderId} so={s} now={now} />
      ))}
    </ul>
  );
}

function DdSkeleton() {
  return (
    <div data-testid="peek-dd-skeleton" aria-busy="true" className="space-y-2">
      <span className="sr-only">Loading direct debits</span>
      <div className="h-10 rounded-md bg-muted/40 motion-safe:animate-pulse" />
      <div className="h-10 rounded-md bg-muted/40 motion-safe:animate-pulse" />
    </div>
  );
}

function SoSkeleton() {
  return (
    <div data-testid="peek-so-skeleton" aria-busy="true" className="space-y-2">
      <span className="sr-only">Loading standing orders</span>
      <div className="h-10 rounded-md bg-muted/40 motion-safe:animate-pulse" />
      <div className="h-10 rounded-md bg-muted/40 motion-safe:animate-pulse" />
    </div>
  );
}
