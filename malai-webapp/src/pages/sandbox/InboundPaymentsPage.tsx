import { useId, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import type {
  CounterpartyResponse,
  SandboxRail,
} from "@/api/generated/types.gen";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { nativeSelectClassName } from "@/components/ui/native-select";
import { StatusPanel } from "@/components/StatusPanel";
import { useFocusOnMount } from "@/lib/useFocusOnMount";
import { usePageTitle } from "@/lib/usePageTitle";
import { useAccounts } from "@/accounts/useAccounts";
import { useCounterparties } from "@/sandbox/useCounterparties";
import { useInboundPaymentMutation } from "@/sandbox/useInboundPaymentMutation";
import { AmountInput } from "@/move-money/AmountInput";
import { parseAmount } from "@/move-money/parseAmount";

const REFERENCE_MAX = 500;

function counterpartyKey(row: {
  sortCode: string;
  accountNumber: string;
}): string {
  return `${row.sortCode}|${row.accountNumber}`;
}

function resolveInitialOriginatorKey(
  rows: CounterpartyResponse[],
  params: URLSearchParams,
): string {
  if (rows.length === 0) return "";
  const sortCode = params.get("originatorSortCode");
  const accountNumber = params.get("originatorAccountNumber");
  const name = params.get("originatorName");
  if (sortCode && accountNumber && name) {
    const match = rows.find(
      (r) =>
        r.sortCode === sortCode &&
        r.accountNumber === accountNumber &&
        r.nameOnRecord === name,
    );
    if (match) return counterpartyKey(match);
  }
  return counterpartyKey(rows[0]!);
}

export function InboundPaymentsPage() {
  usePageTitle("Inbound payments");
  const headingRef = useFocusOnMount<HTMLHeadingElement>();
  const [searchParams] = useSearchParams();

  const {
    counterparties,
    isPending: counterpartiesLoading,
    isError: counterpartiesFailed,
  } = useCounterparties();
  const {
    accounts,
    isPending: accountsLoading,
    isError: accountsFailed,
  } = useAccounts();
  const loadFailed = counterpartiesFailed || accountsFailed;
  const counterpartyList = useMemo(
    () => counterparties ?? [],
    [counterparties],
  );
  const accountList = useMemo(() => accounts ?? [], [accounts]);

  const initialOriginatorKey = useMemo(
    () => resolveInitialOriginatorKey(counterpartyList, searchParams),
    [counterpartyList, searchParams],
  );
  const [originatorOverride, setOriginatorOverride] = useState<string | null>(
    null,
  );
  const originatorKey = originatorOverride ?? initialOriginatorKey;

  const [creditedOverride, setCreditedOverride] = useState<string | null>(null);
  const creditedAccountId =
    creditedOverride ??
    (accountList.length > 0 ? accountList[0]!.accountUid : "");

  const [rail, setRail] = useState<SandboxRail>("FPS");
  const [amount, setAmount] = useState("");
  const [reference, setReference] = useState("");

  const { submit, isPending } = useInboundPaymentMutation({
    onSuccess: () => {
      setAmount("");
      setReference("");
    },
  });

  const selectedCounterparty = counterpartyList.find(
    (r) => counterpartyKey(r) === originatorKey,
  );

  const parsed = parseAmount(amount);
  const canSubmit =
    !isPending &&
    parsed.ok &&
    selectedCounterparty !== undefined &&
    creditedAccountId.length > 0 &&
    !counterpartiesLoading &&
    !accountsLoading;

  const amountId = useId();
  const referenceId = useId();
  const originatorId = useId();
  const creditedId = useId();

  function handleSubmit() {
    if (!canSubmit || !parsed.ok || !selectedCounterparty) return;
    submit({
      rail,
      creditedAccountId,
      originatorSortCode: selectedCounterparty.sortCode,
      originatorAccountNumber: selectedCounterparty.accountNumber,
      originatorName: selectedCounterparty.nameOnRecord,
      amountMinorUnits: parsed.minorUnits,
      reference: reference.length > 0 ? reference : undefined,
    });
  }

  return (
    <section data-testid="inbound-payments-form">
      <h1
        ref={headingRef}
        tabIndex={-1}
        className="text-2xl font-semibold tracking-tight outline-none"
      >
        Inbound payments
      </h1>
      <p className="mt-3 text-sm text-muted-foreground">
        Simulate an inbound payment landing on one of your accounts from a
        catalogue counterparty.
      </p>

      {loadFailed ? (
        <StatusPanel
          testId="inbound-error"
          role="alert"
          className="mt-6"
          title="We couldn’t load the counterparties and accounts this form needs."
          body="Please try again in a moment."
        />
      ) : (
        <div className="mt-6 space-y-6">
          <div className="space-y-1.5">
            <Label htmlFor={originatorId}>Originator</Label>
            <select
              id={originatorId}
              data-testid="inbound-originator"
              value={originatorKey}
              onChange={(e) => setOriginatorOverride(e.target.value)}
              className={nativeSelectClassName}
            >
              {counterpartyList.map((row) => (
                <option key={counterpartyKey(row)} value={counterpartyKey(row)}>
                  {row.nameOnRecord} · {row.sortCode} · {row.accountNumber}
                </option>
              ))}
            </select>
          </div>

          {accountList.length > 1 ? (
            <div className="space-y-1.5">
              <Label htmlFor={creditedId}>Credited account</Label>
              <select
                id={creditedId}
                data-testid="inbound-credited-account"
                value={creditedAccountId}
                onChange={(e) => setCreditedOverride(e.target.value)}
                className={nativeSelectClassName}
              >
                {accountList.map((account) => (
                  <option key={account.accountUid} value={account.accountUid}>
                    {account.name}
                  </option>
                ))}
              </select>
            </div>
          ) : null}

          <fieldset className="space-y-2">
            <legend className="text-sm font-medium">Rail</legend>
            <div
              role="radiogroup"
              aria-label="Rail"
              data-testid="inbound-rail"
              className="flex gap-4"
            >
              <label className="flex items-center gap-2 text-sm pointer-coarse:min-h-11">
                <input
                  type="radio"
                  name="inbound-rail"
                  value="FPS"
                  checked={rail === "FPS"}
                  onChange={() => setRail("FPS")}
                />
                FPS
              </label>
              <label className="flex items-center gap-2 text-sm pointer-coarse:min-h-11">
                <input
                  type="radio"
                  name="inbound-rail"
                  value="BACS"
                  checked={rail === "BACS"}
                  onChange={() => setRail("BACS")}
                />
                BACS
              </label>
            </div>
          </fieldset>

          <div className="space-y-1.5">
            <Label htmlFor={amountId}>Amount</Label>
            <AmountInput id={amountId} value={amount} onChange={setAmount} />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor={referenceId}>Reference (optional)</Label>
            <Input
              id={referenceId}
              type="text"
              autoComplete="off"
              maxLength={REFERENCE_MAX}
              value={reference}
              onChange={(e) => setReference(e.target.value)}
            />
          </div>

          <Button
            type="button"
            data-testid="inbound-submit"
            onClick={handleSubmit}
            disabled={!canSubmit}
          >
            {isPending ? "Sending…" : "Receive payment"}
          </Button>
        </div>
      )}
    </section>
  );
}
