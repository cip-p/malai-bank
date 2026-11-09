import { useId, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useForm } from "react-hook-form";
import { format } from "date-fns";
import type {
  AccountResponse,
  Frequency,
  PayeeResponse,
  StandingOrderResponse,
} from "@/api/generated/types.gen";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";
import { StepFlow, useStepFlow } from "@/components/StepFlow";
import { SuccessTick } from "@/components/SuccessTick";
import { useFocusOnMount } from "@/lib/useFocusOnMount";
import { usePageTitle } from "@/lib/usePageTitle";
import { Money } from "@/accounts/Money";
import { useAccounts } from "@/accounts/useAccounts";
import { usePayees } from "@/move-money/usePayees";
import { useCreateStandingOrder } from "@/move-money/useCreateStandingOrder";
import { AmountInput } from "@/move-money/AmountInput";
import { parseAmount } from "@/move-money/parseAmount";
import { formatFrequency } from "@/move-money/formatFrequency";

type Props = { now?: Date };

const REFERENCE_MAX = 80;
const FREQUENCIES: Frequency[] = ["WEEKLY", "MONTHLY", "QUARTERLY", "ANNUAL"];

function payeeDisplayName(payee: PayeeResponse): string {
  return payee.nickname ?? payee.name;
}

export function NewStandingOrderPage({ now }: Props = {}) {
  usePageTitle("New standing order");
  const todayIso = format(now ?? new Date(), "yyyy-MM-dd");

  const {
    payees,
    isPending: payeesLoading,
    isError: payeesError,
  } = usePayees();
  const { accounts, isPending: accountsLoading } = useAccounts();
  const accountList = useMemo(() => accounts ?? [], [accounts]);

  const [selectedPayeeId, setSelectedPayeeId] = useState("");
  const [sourceOverride, setSourceOverride] = useState<string | null>(null);
  const [amount, setAmount] = useState("");
  const [reference, setReference] = useState("");
  const [frequency, setFrequency] = useState<Frequency>("MONTHLY");
  const [startDate, setStartDate] = useState(todayIso);
  const [saved, setSaved] = useState<StandingOrderResponse | null>(null);

  const sourceUid = useMemo(() => {
    if (sourceOverride !== null) return sourceOverride;
    return accountList[0]?.accountUid ?? "";
  }, [sourceOverride, accountList]);

  const selectedPayee =
    (payees ?? []).find((p) => p.payeeId === selectedPayeeId) ?? null;
  const sourceAccount =
    accountList.find((a) => a.accountUid === sourceUid) ?? null;

  const parsed = parseAmount(amount);
  const referenceTooLong = reference.length > REFERENCE_MAX;
  const startDateValid = startDate.length > 0 && startDate >= todayIso;
  const detailsValid = parsed.ok && !referenceTooLong && startDateValid;

  const form = useForm({ defaultValues: {} });

  const { createStandingOrder, isPending } = useCreateStandingOrder({
    navigateOnSuccess: false,
    onSuccess: (order) => setSaved(order),
  });

  function fireCreate() {
    if (!parsed.ok || !sourceUid || !selectedPayeeId) return;
    createStandingOrder({
      accountId: sourceUid,
      payeeId: selectedPayeeId,
      amountMinorUnits: parsed.minorUnits,
      currency: "GBP",
      frequency,
      startDate,
      ...(reference.length > 0 ? { reference } : {}),
    });
  }

  if (saved) {
    return <SuccessScreen order={saved} />;
  }

  return (
    <StepFlow
      form={form}
      ariaLabel="New standing order"
      steps={[
        {
          id: "payee",
          title: "Choose payee",
          render: () => (
            <PayeeStep
              payees={payees}
              isPending={payeesLoading}
              isError={payeesError}
              selectedPayeeId={selectedPayeeId}
              onSelect={setSelectedPayeeId}
            />
          ),
        },
        {
          id: "source",
          title: "Choose source",
          render: () => (
            <SourceStep
              accounts={accountList}
              accountsLoading={accountsLoading}
              sourceUid={sourceUid}
              onSelect={setSourceOverride}
            />
          ),
        },
        {
          id: "details",
          title: "Details",
          render: () => (
            <DetailsStep
              amount={amount}
              onAmountChange={setAmount}
              reference={reference}
              onReferenceChange={setReference}
              frequency={frequency}
              onFrequencyChange={setFrequency}
              startDate={startDate}
              onStartDateChange={setStartDate}
              minDate={todayIso}
              detailsValid={detailsValid}
              referenceTooLong={referenceTooLong}
              startDateValid={startDateValid}
            />
          ),
        },
        {
          id: "review",
          title: "Review",
          render: () => (
            <ReviewStep
              selectedPayee={selectedPayee}
              sourceAccount={sourceAccount}
              amountMinorUnits={parsed.ok ? parsed.minorUnits : 0}
              reference={reference}
              frequency={frequency}
              startDate={startDate}
              isPending={isPending}
              onConfirm={fireCreate}
            />
          ),
        },
      ]}
    />
  );
}

function PayeeStep({
  payees,
  isPending,
  isError,
  selectedPayeeId,
  onSelect,
}: {
  payees: PayeeResponse[] | null;
  isPending: boolean;
  isError: boolean;
  selectedPayeeId: string;
  onSelect: (payeeId: string) => void;
}) {
  const { next } = useStepFlow();

  return (
    <div className="space-y-6">
      <h1
        tabIndex={-1}
        className="text-2xl font-semibold tracking-tight outline-none"
      >
        Who is the standing order to?
      </h1>
      <p className="text-sm text-muted-foreground">
        A standing order pays a saved payee. Their details are copied onto the
        order when you set it up.
      </p>

      <PayeePicker
        payees={payees}
        isPending={isPending}
        isError={isError}
        selectedPayeeId={selectedPayeeId}
        onSelect={onSelect}
      />

      <Button type="button" onClick={next} disabled={!selectedPayeeId}>
        Continue
      </Button>
    </div>
  );
}

function PayeePicker({
  payees,
  isPending,
  isError,
  selectedPayeeId,
  onSelect,
}: {
  payees: PayeeResponse[] | null;
  isPending: boolean;
  isError: boolean;
  selectedPayeeId: string;
  onSelect: (payeeId: string) => void;
}) {
  if (isPending) {
    return (
      <div
        data-testid="so-payee-skeleton"
        aria-busy="true"
        className="space-y-2"
      >
        <span className="sr-only">Loading payees</span>
        <div className="h-12 rounded-md bg-muted/40 motion-safe:animate-pulse" />
        <div className="h-12 rounded-md bg-muted/40 motion-safe:animate-pulse" />
        <div className="h-12 rounded-md bg-muted/40 motion-safe:animate-pulse" />
      </div>
    );
  }
  if (isError) {
    return (
      <p data-testid="so-payee-error" className="text-sm text-muted-foreground">
        Couldn’t load payees
      </p>
    );
  }
  const list = payees ?? [];
  if (list.length === 0) {
    return (
      <div data-testid="so-payee-empty" className="space-y-3">
        <p className="text-sm text-muted-foreground">
          You need a saved payee before you can set up a standing order.
        </p>
        <Button asChild variant="outline">
          <Link to="/move/payees/new" data-testid="so-payee-add">
            Add a payee
          </Link>
        </Button>
      </div>
    );
  }
  const sorted = [...list].sort((a, b) =>
    payeeDisplayName(a).localeCompare(payeeDisplayName(b)),
  );
  return (
    <div className="space-y-3">
      <div
        data-testid="so-payee-list"
        role="radiogroup"
        aria-label="Payees"
        className="space-y-2"
      >
        {sorted.map((p) => {
          const checked = p.payeeId === selectedPayeeId;
          const display = payeeDisplayName(p);
          const secondary = p.nickname ? p.name : null;
          return (
            <label
              key={p.payeeId}
              className={cn(
                "flex cursor-pointer items-start gap-3 rounded-lg border border-border p-3",
                checked && "border-primary bg-primary/5",
              )}
            >
              <input
                type="radio"
                name="so-payee"
                value={p.payeeId}
                checked={checked}
                onChange={() => onSelect(p.payeeId)}
                className="mt-1"
              />
              <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                <span className="truncate text-sm font-medium">{display}</span>
                {secondary ? (
                  <span className="truncate text-xs text-muted-foreground">
                    {secondary}
                  </span>
                ) : null}
                <span className="mt-0.5 text-xs tabular-nums text-muted-foreground">
                  {p.sortCode} · {p.accountNumber}
                </span>
              </span>
            </label>
          );
        })}
      </div>
      <Button asChild variant="link" className="h-auto p-0">
        <Link to="/move/payees/new" data-testid="so-payee-add">
          Add a new payee
        </Link>
      </Button>
    </div>
  );
}

function SourceStep({
  accounts,
  accountsLoading,
  sourceUid,
  onSelect,
}: {
  accounts: AccountResponse[];
  accountsLoading: boolean;
  sourceUid: string;
  onSelect: (accountUid: string) => void;
}) {
  const { next } = useStepFlow();
  return (
    <div className="space-y-6">
      <h1
        tabIndex={-1}
        className="text-2xl font-semibold tracking-tight outline-none"
      >
        Which account pays it?
      </h1>
      {accountsLoading ? (
        <p className="text-sm text-muted-foreground">Loading accounts…</p>
      ) : null}
      {accounts.length > 0 ? (
        <div
          role="radiogroup"
          aria-label="From"
          data-testid="so-source-list"
          className="space-y-2"
        >
          {accounts.map((a) => {
            const checked = a.accountUid === sourceUid;
            return (
              <label
                key={a.accountUid}
                className={cn(
                  "flex cursor-pointer items-center gap-3 rounded-lg border border-border p-3",
                  checked && "border-primary bg-primary/5",
                )}
              >
                <input
                  type="radio"
                  name="so-source"
                  value={a.accountUid}
                  checked={checked}
                  onChange={() => onSelect(a.accountUid)}
                />
                <span className="text-sm font-medium">{a.name}</span>
              </label>
            );
          })}
        </div>
      ) : null}
      <Button type="button" onClick={next} disabled={!sourceUid}>
        Continue
      </Button>
    </div>
  );
}

function DetailsStep({
  amount,
  onAmountChange,
  reference,
  onReferenceChange,
  frequency,
  onFrequencyChange,
  startDate,
  onStartDateChange,
  minDate,
  detailsValid,
  referenceTooLong,
  startDateValid,
}: {
  amount: string;
  onAmountChange: (next: string) => void;
  reference: string;
  onReferenceChange: (next: string) => void;
  frequency: Frequency;
  onFrequencyChange: (next: Frequency) => void;
  startDate: string;
  onStartDateChange: (next: string) => void;
  minDate: string;
  detailsValid: boolean;
  referenceTooLong: boolean;
  startDateValid: boolean;
}) {
  const { next } = useStepFlow();
  const amountId = useId();
  const referenceId = useId();
  const startDateId = useId();
  const startDateErrorId = useId();
  const showStartDateError = startDate.length > 0 && !startDateValid;

  return (
    <div className="space-y-6">
      <h1
        tabIndex={-1}
        className="text-2xl font-semibold tracking-tight outline-none"
      >
        How much, and how often?
      </h1>

      <div className="space-y-1.5">
        <Label htmlFor={amountId}>Amount</Label>
        <AmountInput id={amountId} value={amount} onChange={onAmountChange} />
      </div>

      <fieldset className="space-y-2">
        <legend className="text-sm font-medium">Frequency</legend>
        <div
          role="radiogroup"
          aria-label="Frequency"
          data-testid="so-frequency-list"
          className="grid grid-cols-2 gap-2"
        >
          {FREQUENCIES.map((f) => {
            const checked = f === frequency;
            return (
              <label
                key={f}
                className={cn(
                  "flex cursor-pointer items-center gap-2 rounded-lg border border-border p-3 text-sm",
                  checked && "border-primary bg-primary/5",
                )}
              >
                <input
                  type="radio"
                  name="so-frequency"
                  value={f}
                  checked={checked}
                  onChange={() => onFrequencyChange(f)}
                />
                <span>{formatFrequency(f)}</span>
              </label>
            );
          })}
        </div>
      </fieldset>

      <div className="space-y-1.5">
        <Label htmlFor={startDateId}>First payment date</Label>
        <Input
          id={startDateId}
          type="date"
          min={minDate}
          value={startDate}
          onChange={(e) => onStartDateChange(e.target.value)}
          aria-invalid={showStartDateError ? true : undefined}
          aria-describedby={showStartDateError ? startDateErrorId : undefined}
        />
        {showStartDateError ? (
          <p id={startDateErrorId} className="text-sm text-destructive">
            Choose today or a later date.
          </p>
        ) : null}
      </div>

      <div className="space-y-1.5">
        <Label htmlFor={referenceId}>Reference (optional)</Label>
        <Input
          id={referenceId}
          type="text"
          autoComplete="off"
          value={reference}
          maxLength={REFERENCE_MAX}
          onChange={(e) => onReferenceChange(e.target.value)}
        />
        {referenceTooLong ? (
          <p className="text-sm text-destructive">
            Keep the reference to {REFERENCE_MAX} characters or fewer.
          </p>
        ) : null}
      </div>

      <Button type="button" onClick={next} disabled={!detailsValid}>
        Review
      </Button>
    </div>
  );
}

function ReviewStep({
  selectedPayee,
  sourceAccount,
  amountMinorUnits,
  reference,
  frequency,
  startDate,
  isPending,
  onConfirm,
}: {
  selectedPayee: PayeeResponse | null;
  sourceAccount: AccountResponse | null;
  amountMinorUnits: number;
  reference: string;
  frequency: Frequency;
  startDate: string;
  isPending: boolean;
  onConfirm: () => void;
}) {
  const startDateLabel = startDate
    ? format(new Date(`${startDate}T00:00:00`), "d MMM yyyy")
    : "";
  return (
    <div className="space-y-6">
      <h1
        tabIndex={-1}
        className="text-2xl font-semibold tracking-tight outline-none"
      >
        Review and confirm
      </h1>

      <dl
        data-testid="so-confirmation"
        className="grid gap-3 rounded-lg border border-border p-4 text-sm"
      >
        <div className="grid grid-cols-3 gap-2">
          <dt className="text-muted-foreground">To</dt>
          <dd className="col-span-2">
            {selectedPayee ? payeeDisplayName(selectedPayee) : ""}
          </dd>
        </div>
        <div className="grid grid-cols-3 gap-2">
          <dt className="text-muted-foreground">From</dt>
          <dd className="col-span-2">{sourceAccount?.name ?? ""}</dd>
        </div>
        <div className="grid grid-cols-3 gap-2">
          <dt className="text-muted-foreground">Amount</dt>
          <dd className="col-span-2">
            <Money minorUnits={amountMinorUnits} signed="never" />
          </dd>
        </div>
        <div className="grid grid-cols-3 gap-2">
          <dt className="text-muted-foreground">Frequency</dt>
          <dd className="col-span-2">{formatFrequency(frequency)}</dd>
        </div>
        <div className="grid grid-cols-3 gap-2">
          <dt className="text-muted-foreground">First payment</dt>
          <dd className="col-span-2">{startDateLabel}</dd>
        </div>
        {reference.length > 0 ? (
          <div className="grid grid-cols-3 gap-2">
            <dt className="text-muted-foreground">Reference</dt>
            <dd className="col-span-2">{reference}</dd>
          </div>
        ) : null}
      </dl>

      <Button
        type="button"
        data-testid="so-submit"
        onClick={onConfirm}
        disabled={isPending}
      >
        {isPending ? "Setting up…" : "Set up standing order"}
      </Button>
    </div>
  );
}

function SuccessScreen({ order }: { order: StandingOrderResponse }) {
  const headingRef = useFocusOnMount<HTMLHeadingElement>();
  return (
    <section
      data-testid="so-success"
      aria-label="Standing order set up"
      className="flex flex-col items-center gap-6 py-10 text-center"
    >
      <SuccessTick />
      <div className="space-y-2">
        <h1
          ref={headingRef}
          tabIndex={-1}
          className="text-2xl font-semibold tracking-tight outline-none"
        >
          Standing order set up
        </h1>
        <p className="text-sm text-muted-foreground">
          {order.payeeName} will be paid {formatFrequency(order.frequency)}.
        </p>
      </div>
      <Button asChild>
        <Link to="/move/standing-orders" data-testid="so-success-back">
          Back to standing orders
        </Link>
      </Button>
    </section>
  );
}
