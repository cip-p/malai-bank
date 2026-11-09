import { useId, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import type { PaymentResponse } from "@/api/generated/types.gen";
import { useActiveAccountHolder } from "@/account-holder/useActiveAccountHolder";
import { Money } from "@/accounts/Money";
import { useAccount } from "@/accounts/useAccount";
import { useAccounts } from "@/accounts/useAccounts";
import { useIdentifiers } from "@/accounts/useIdentifiers";
import { invalidateCreditAccountData } from "@/cards/invalidateCreditAccountData";
import { useCreditSummary } from "@/cards/useCreditSummary";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { nativeSelectClassName } from "@/components/ui/native-select";
import { AmountInput } from "@/move-money/AmountInput";
import { parseAmount } from "@/move-money/parseAmount";
import { PaymentOutcome } from "@/move-money/PaymentOutcome";
import { useTransfer } from "@/move-money/useTransfer";
import { useFocusOnMount } from "@/lib/useFocusOnMount";
import { usePageTitle } from "@/lib/usePageTitle";

type PresetKey = "minimum" | "statement" | "custom";

export function PayCardPage() {
  const { accountUid = "" } = useParams<{ accountUid: string }>();
  usePageTitle("Pay card");
  const headingRef = useFocusOnMount<HTMLHeadingElement>();
  const queryClient = useQueryClient();

  const holder = useActiveAccountHolder();
  const activeHolderUid =
    holder.status === "ready" ? holder.activeHolder.accountHolderUid : "";

  const { account } = useAccount(accountUid);
  const { accounts } = useAccounts();
  const { summary, isPending: summaryPending } = useCreditSummary(accountUid);
  const identifiersQuery = useIdentifiers(accountUid);

  const sources = useMemo(
    () =>
      (accounts ?? []).filter(
        (a) => a.accountUid !== accountUid && a.accountType !== "CREDIT_CARD",
      ),
    [accounts, accountUid],
  );

  const [sourceOverride, setSourceOverride] = useState<string | null>(null);
  const sourceUid =
    sourceOverride ?? (sources.length > 0 ? sources[0]!.accountUid : "");
  const source = sources.find((a) => a.accountUid === sourceUid);

  const minimum = summary?.minimumPaymentMinorUnits ?? 0;
  const statement = summary?.statementBalanceMinorUnits ?? 0;

  const [presetOverride, setPresetOverride] = useState<PresetKey | null>(null);
  const preset: PresetKey =
    presetOverride ?? (minimum > 0 ? "minimum" : "custom");
  const [customAmount, setCustomAmount] = useState("");

  const parsedCustom = parseAmount(customAmount);
  const amountMinorUnits =
    preset === "minimum"
      ? minimum
      : preset === "statement"
        ? statement
        : parsedCustom.ok
          ? parsedCustom.minorUnits
          : 0;

  const [payment, setPayment] = useState<PaymentResponse | null>(null);

  const { transfer, isPending, insufficientFunds } = useTransfer({
    onSuccess: (response) => {
      invalidateCreditAccountData(queryClient, activeHolderUid, accountUid);
      setPayment(response);
    },
  });

  const sourceId = useId();

  if (payment) {
    return (
      <div className="mx-auto w-full max-w-3xl p-6">
        <PaymentOutcome
          initial={payment}
          sourceUid={sourceUid}
          sourceAccountName={source?.name ?? "your account"}
          destinationLabel={account?.name ?? "your credit card"}
        />
      </div>
    );
  }

  const identifiers = identifiersQuery.identifiers;
  const primary = identifiers?.accountIdentifiers[0];
  const toSortCode = identifiers?.bankIdentifier ?? primary?.bankIdentifier;
  const toAccountNumber =
    identifiers?.accountIdentifier ?? primary?.accountIdentifier;

  const canSubmit =
    !isPending &&
    amountMinorUnits > 0 &&
    source !== undefined &&
    toSortCode !== undefined &&
    toAccountNumber !== undefined;

  function handleSubmit() {
    if (!canSubmit || !toSortCode || !toAccountNumber) return;
    transfer({
      fromAccountUid: sourceUid,
      toSortCode,
      toAccountNumber,
      amountMinorUnits,
      description: "Credit card payment",
    });
  }

  return (
    <div className="mx-auto w-full max-w-3xl p-6">
      <Link
        to={`/accounts/${accountUid}`}
        className="text-sm text-muted-foreground hover:underline focus-visible:underline"
      >
        ← {account?.name ?? "Account"}
      </Link>

      <section aria-labelledby="pay-card-heading" className="mt-4">
        <h1
          id="pay-card-heading"
          ref={headingRef}
          tabIndex={-1}
          className="text-2xl font-semibold tracking-tight outline-none"
        >
          Pay card
        </h1>

        {account && account.accountType !== "CREDIT_CARD" ? (
          <p
            data-testid="pay-card-not-credit"
            className="mt-6 text-sm text-muted-foreground"
          >
            This account isn’t a credit card.
          </p>
        ) : summaryPending ? (
          <PayCardSkeleton />
        ) : sources.length === 0 ? (
          <p
            data-testid="pay-card-no-source"
            className="mt-6 text-sm text-muted-foreground"
          >
            You need a current account to pay from.
          </p>
        ) : (
          <div className="mt-6 space-y-6 rounded-xl border border-border bg-card p-6 shadow-xs">
            {summary ? (
              <p className="text-sm text-muted-foreground">
                {summary.owedMinorUnits < 0 ? (
                  <>
                    {account?.name ?? "This card"} is{" "}
                    <span className="font-medium text-foreground">
                      <Money
                        minorUnits={-summary.owedMinorUnits}
                        currency={summary.currency}
                      />
                    </span>{" "}
                    in credit.
                  </>
                ) : (
                  <>
                    You owe{" "}
                    <span className="font-medium text-foreground">
                      <Money
                        minorUnits={summary.owedMinorUnits}
                        currency={summary.currency}
                      />
                    </span>
                    {" on "}
                    {account?.name ?? "this card"}.
                  </>
                )}
              </p>
            ) : null}

            <fieldset className="space-y-2">
              <legend className="text-sm font-medium">Amount</legend>
              <div role="radiogroup" aria-label="Amount" className="space-y-2">
                {minimum > 0 ? (
                  <PresetOption
                    name="pay-card-preset"
                    value="minimum"
                    checked={preset === "minimum"}
                    onSelect={() => setPresetOverride("minimum")}
                    testId="pay-card-preset-minimum"
                    label="Minimum payment"
                    amountMinorUnits={minimum}
                  />
                ) : null}
                {statement > 0 ? (
                  <PresetOption
                    name="pay-card-preset"
                    value="statement"
                    checked={preset === "statement"}
                    onSelect={() => setPresetOverride("statement")}
                    testId="pay-card-preset-statement"
                    label="Statement balance"
                    amountMinorUnits={statement}
                  />
                ) : null}
                <PresetOption
                  name="pay-card-preset"
                  value="custom"
                  checked={preset === "custom"}
                  onSelect={() => setPresetOverride("custom")}
                  testId="pay-card-preset-custom"
                  label="Custom amount"
                />
              </div>
              {preset === "custom" ? (
                <AmountInput
                  aria-label="Custom amount"
                  data-testid="pay-card-custom-amount"
                  value={customAmount}
                  onChange={setCustomAmount}
                />
              ) : null}
            </fieldset>

            {sources.length > 1 ? (
              <div className="space-y-1.5">
                <Label htmlFor={sourceId}>Pay from</Label>
                <select
                  id={sourceId}
                  data-testid="pay-card-source"
                  value={sourceUid}
                  onChange={(e) => setSourceOverride(e.target.value)}
                  className={nativeSelectClassName}
                >
                  {sources.map((a) => (
                    <option key={a.accountUid} value={a.accountUid}>
                      {a.name}
                    </option>
                  ))}
                </select>
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">
                Paying from{" "}
                <span className="font-medium text-foreground">
                  {source?.name}
                </span>
              </p>
            )}

            {insufficientFunds ? (
              <p
                role="alert"
                data-testid="pay-card-insufficient-funds"
                className="text-sm text-destructive"
              >
                There isn’t enough in {source?.name ?? "that account"} to cover
                this payment.
              </p>
            ) : null}

            <Button
              type="button"
              data-testid="pay-card-submit"
              disabled={!canSubmit}
              onClick={handleSubmit}
            >
              {isPending ? (
                "Paying…"
              ) : amountMinorUnits > 0 ? (
                <>
                  Pay&nbsp;
                  <Money minorUnits={amountMinorUnits} />
                </>
              ) : (
                "Pay"
              )}
            </Button>
          </div>
        )}
      </section>
    </div>
  );
}

function PresetOption({
  name,
  value,
  checked,
  onSelect,
  testId,
  label,
  amountMinorUnits,
}: {
  name: string;
  value: string;
  checked: boolean;
  onSelect: () => void;
  testId: string;
  label: string;
  amountMinorUnits?: number;
}) {
  return (
    <label className="flex cursor-pointer items-center justify-between gap-3 rounded-md border border-border px-3 py-2.5 text-sm pointer-coarse:py-3 has-checked:border-ring has-checked:bg-muted/40">
      <span className="flex items-center gap-2">
        <input
          type="radio"
          name={name}
          value={value}
          checked={checked}
          onChange={onSelect}
          data-testid={testId}
        />
        {label}
      </span>
      {amountMinorUnits !== undefined ? (
        <span className="font-medium">
          <Money minorUnits={amountMinorUnits} />
        </span>
      ) : null}
    </label>
  );
}

function PayCardSkeleton() {
  return (
    <div
      data-testid="pay-card-skeleton"
      aria-busy="true"
      className="mt-6 space-y-3"
    >
      <span className="sr-only">Loading credit summary</span>
      <div className="h-10 w-full rounded-md bg-muted/40 motion-safe:animate-pulse" />
      <div className="h-10 w-full rounded-md bg-muted/40 motion-safe:animate-pulse" />
      <div className="h-10 w-2/3 rounded-md bg-muted/40 motion-safe:animate-pulse" />
    </div>
  );
}
