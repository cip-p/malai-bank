import { useEffect, useState, type ReactElement } from "react";
import { Link } from "react-router-dom";
import { LoaderCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { SuccessTick } from "@/components/SuccessTick";
import { Money } from "@/accounts/Money";
import { useFocusOnMount } from "@/lib/useFocusOnMount";
import type { PaymentResponse, PaymentState } from "@/api/generated/types.gen";
import { usePaymentStatus } from "./usePaymentStatus";
import { formatReturnReason } from "./formatReturnReason";

const SLOW_NOTE_AFTER_MS = 15_000;

type Props = {
  initial: PaymentResponse;
  sourceUid: string;
  sourceAccountName: string;
  destinationLabel: string;
};

type OutcomeProps = {
  payment: PaymentResponse;
  sourceUid: string;
  sourceAccountName: string;
  destinationLabel: string;
};

export function PaymentOutcome({
  initial,
  sourceUid,
  sourceAccountName,
  destinationLabel,
}: Props) {
  const { payment } = usePaymentStatus(initial);
  const Outcome = OUTCOME_BY_STATE[payment.state];

  return (
    <Outcome
      payment={payment}
      sourceUid={sourceUid}
      sourceAccountName={sourceAccountName}
      destinationLabel={destinationLabel}
    />
  );
}

function PendingOutcome({
  payment,
  sourceUid,
  sourceAccountName,
  destinationLabel,
}: OutcomeProps) {
  const headingRef = useFocusOnMount<HTMLHeadingElement>();
  const [slow, setSlow] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => setSlow(true), SLOW_NOTE_AFTER_MS);
    return () => clearTimeout(timer);
  }, []);

  return (
    <section
      data-testid="payment-pending"
      role="status"
      aria-label="Sending payment"
      className="flex flex-col items-center gap-6 py-10 text-center"
    >
      <LoaderCircle
        aria-hidden="true"
        className="size-12 text-primary motion-safe:animate-spin"
      />
      <div className="space-y-2">
        <h1
          ref={headingRef}
          tabIndex={-1}
          className="text-2xl font-semibold tracking-tight outline-none"
        >
          Sending your money…
        </h1>
        <p className="text-sm text-muted-foreground">
          <Money minorUnits={payment.amountMinorUnits} /> to {destinationLabel}
        </p>
        {slow ? (
          <p
            data-testid="payment-pending-slow"
            className="text-sm text-muted-foreground"
          >
            This is taking longer than usual. The money is reserved and we’ll
            keep checking.
          </p>
        ) : null}
      </div>
      <Button asChild variant="outline">
        <Link
          to={sourceUid ? `/accounts/${sourceUid}` : "/"}
          data-testid="payment-pending-back"
        >
          Back to {sourceAccountName}
        </Link>
      </Button>
    </section>
  );
}

function SettledOutcome({
  payment,
  sourceUid,
  sourceAccountName,
  destinationLabel,
}: OutcomeProps) {
  const headingRef = useFocusOnMount<HTMLHeadingElement>();
  return (
    <section
      data-testid="transfer-success"
      aria-label="Transfer sent"
      className="flex flex-col items-center gap-6 py-10 text-center"
    >
      <SuccessTick />
      <div className="space-y-2">
        <h1
          ref={headingRef}
          tabIndex={-1}
          className="text-2xl font-semibold tracking-tight outline-none"
        >
          Transfer sent
        </h1>
        <p className="text-sm text-muted-foreground">
          <Money minorUnits={payment.amountMinorUnits} /> to {destinationLabel}
        </p>
      </div>
      <Button asChild>
        <Link
          to={sourceUid ? `/accounts/${sourceUid}` : "/"}
          data-testid="transfer-success-back"
        >
          Back to {sourceAccountName}
        </Link>
      </Button>
    </section>
  );
}

function ReturnedOutcome({
  payment,
  sourceUid,
  sourceAccountName,
  destinationLabel,
}: OutcomeProps) {
  const headingRef = useFocusOnMount<HTMLHeadingElement>();
  return (
    <section
      data-testid="payment-returned"
      role="alert"
      aria-label="Payment returned"
      className="flex flex-col items-center gap-6 py-10 text-center motion-safe:animate-in motion-safe:fade-in-0 motion-safe:duration-300 motion-safe:ease-arrive"
    >
      <div className="space-y-2">
        <h1
          ref={headingRef}
          tabIndex={-1}
          className="text-2xl font-semibold tracking-tight outline-none"
        >
          Payment returned
        </h1>
        <p className="text-sm text-muted-foreground">
          <Money minorUnits={payment.amountMinorUnits} /> to {destinationLabel}{" "}
          couldn’t be delivered.
        </p>
        <p data-testid="payment-returned-reason" className="text-sm">
          {formatReturnReason(payment.returnReason)}
        </p>
        <p className="text-sm text-muted-foreground">
          The money is back in {sourceAccountName}.
        </p>
      </div>
      <Button asChild variant="outline">
        <Link
          to={sourceUid ? `/accounts/${sourceUid}` : "/"}
          data-testid="payment-returned-back"
        >
          Back to {sourceAccountName}
        </Link>
      </Button>
    </section>
  );
}

function ReferredOutcome({
  payment,
  sourceUid,
  sourceAccountName,
  destinationLabel,
}: OutcomeProps) {
  const headingRef = useFocusOnMount<HTMLHeadingElement>();
  return (
    <section
      data-testid="payment-referred"
      role="status"
      aria-label="Payment being checked"
      className="flex flex-col items-center gap-6 py-10 text-center"
    >
      <LoaderCircle
        aria-hidden="true"
        className="size-12 text-primary motion-safe:animate-spin"
      />
      <div className="space-y-2">
        <h1
          ref={headingRef}
          tabIndex={-1}
          className="text-2xl font-semibold tracking-tight outline-none"
        >
          We’re checking this payment
        </h1>
        <p className="text-sm text-muted-foreground">
          <Money minorUnits={payment.amountMinorUnits} /> to {destinationLabel}
        </p>
        <p className="text-sm text-muted-foreground">
          The money is set aside and hasn’t left {sourceAccountName} yet. We’ll
          update this page as soon as we’re done.
        </p>
      </div>
      <Button asChild variant="outline">
        <Link
          to={sourceUid ? `/accounts/${sourceUid}` : "/"}
          data-testid="payment-referred-back"
        >
          Back to {sourceAccountName}
        </Link>
      </Button>
    </section>
  );
}

function DeclinedOutcome({
  payment,
  sourceUid,
  sourceAccountName,
  destinationLabel,
}: OutcomeProps) {
  const headingRef = useFocusOnMount<HTMLHeadingElement>();
  return (
    <section
      data-testid="payment-declined"
      role="alert"
      aria-label="Payment not made"
      className="flex flex-col items-center gap-6 py-10 text-center motion-safe:animate-in motion-safe:fade-in-0 motion-safe:duration-300 motion-safe:ease-arrive"
    >
      <div className="space-y-2">
        <h1
          ref={headingRef}
          tabIndex={-1}
          className="text-2xl font-semibold tracking-tight outline-none"
        >
          We didn’t make this payment
        </h1>
        <p className="text-sm text-muted-foreground">
          <Money minorUnits={payment.amountMinorUnits} /> to {destinationLabel}
        </p>
        <p className="text-sm text-muted-foreground">
          The money is still in {sourceAccountName}. Please get in touch if you
          have any questions.
        </p>
      </div>
      <Button asChild variant="outline">
        <Link
          to={sourceUid ? `/accounts/${sourceUid}` : "/"}
          data-testid="payment-declined-back"
        >
          Back to {sourceAccountName}
        </Link>
      </Button>
    </section>
  );
}

const OUTCOME_BY_STATE: Record<
  PaymentState,
  (props: OutcomeProps) => ReactElement
> = {
  PENDING: PendingOutcome,
  SETTLED: SettledOutcome,
  RETURNED: ReturnedOutcome,
  REFERRED: ReferredOutcome,
  DECLINED: DeclinedOutcome,
};
