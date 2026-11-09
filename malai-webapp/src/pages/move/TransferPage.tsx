import { useId, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useForm } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";
import {
  StepFlow,
  useAutoAdvance,
  useStepFlow,
  type AutoAdvanceHandlers,
} from "@/components/StepFlow";
import { Money } from "@/accounts/Money";
import { usePageTitle } from "@/lib/usePageTitle";
import { useAccounts } from "@/accounts/useAccounts";
import { useIdentifiers } from "@/accounts/useIdentifiers";
import { AmountInput } from "@/move-money/AmountInput";
import { AccountNumberInput } from "@/move-money/AccountNumberInput";
import { SortCodeInput } from "@/move-money/SortCodeInput";
import { CopaVerdictPanel } from "@/move-money/CopaVerdictPanel";
import { copaNextStep, type CopaUiState } from "@/move-money/copaNextStep";
import { parseAmount } from "@/move-money/parseAmount";
import { useTransfer } from "@/move-money/useTransfer";
import { usePayees } from "@/move-money/usePayees";
import { useConfirmationOfPayee } from "@/move-money/useConfirmationOfPayee";
import { useCreatePayee } from "@/move-money/useCreatePayee";
import { PaymentOutcome } from "@/move-money/PaymentOutcome";
import type {
  AccountResponse,
  PayeeResponse,
  PaymentResponse,
} from "@/api/generated/types.gen";

type DestinationMode = "my-accounts" | "saved-payee" | "new-payee";

const REFERENCE_MAX = 500;
const REFERENCE_COUNTER_THRESHOLD = 400;
const NAME_MAX = 140;
const SORT_CODE_PATTERN = /^\d{2}-\d{2}-\d{2}$/;

export function TransferPage() {
  usePageTitle("Send money");
  const [searchParams] = useSearchParams();
  const fromParam = searchParams.get("from") ?? "";
  const initialNewPayeePrefill = useMemo(() => {
    const destSortCode = searchParams.get("destSortCode");
    const destAccountNumber = searchParams.get("destAccountNumber");
    const destName = searchParams.get("destName");
    if (!destSortCode || !destAccountNumber || !destName) return null;
    return {
      sortCode: destSortCode,
      accountNumber: destAccountNumber,
      name: destName,
    };
  }, [searchParams]);

  const { accounts, isPending: accountsLoading } = useAccounts();
  const accountList = useMemo(() => accounts ?? [], [accounts]);

  const [sourceOverride, setSourceOverride] = useState<string | null>(null);
  const sourceUid = useMemo(() => {
    if (sourceOverride !== null) return sourceOverride;
    if (accountList.length === 0) return "";
    const match = accountList.find((a) => a.accountUid === fromParam);
    return match?.accountUid ?? accountList[0]!.accountUid;
  }, [sourceOverride, accountList, fromParam]);

  const [preferredMode, setPreferredMode] = useState<DestinationMode | null>(
    null,
  );
  const [destUid, setDestUid] = useState<string>("");
  const [selectedPayeeId, setSelectedPayeeId] = useState<string>("");
  const [newPayeeName, setNewPayeeName] = useState(
    initialNewPayeePrefill?.name ?? "",
  );
  const [newPayeeSortCode, setNewPayeeSortCode] = useState(
    initialNewPayeePrefill?.sortCode ?? "",
  );
  const [newPayeeAccountNumber, setNewPayeeAccountNumber] = useState(
    initialNewPayeePrefill?.accountNumber ?? "",
  );
  const [saveAsPayee, setSaveAsPayee] = useState(false);
  const [amount, setAmount] = useState("");
  const [reference, setReference] = useState("");
  const [copPressedThrough, setCopPressedThrough] = useState(false);
  const [sent, setSent] = useState<{
    payment: PaymentResponse;
    sourceAccountName: string;
    destinationLabel: string;
  } | null>(null);

  const form = useForm({ defaultValues: {} });

  const { identifiers: destIdentifiers } = useIdentifiers(destUid);
  const destAccount = accountList.find((a) => a.accountUid === destUid) ?? null;
  const sourceAccount =
    accountList.find((a) => a.accountUid === sourceUid) ?? null;

  const otherAccounts = useMemo(
    () => accountList.filter((a) => a.accountUid !== sourceUid),
    [accountList, sourceUid],
  );

  const {
    payees,
    isPending: payeesLoading,
    isError: payeesError,
  } = usePayees();
  const selectedPayee =
    (payees ?? []).find((p) => p.payeeId === selectedPayeeId) ?? null;

  const showMyAccounts = !accountsLoading && accountList.length >= 2;
  const defaultMode: DestinationMode =
    initialNewPayeePrefill !== null
      ? "new-payee"
      : showMyAccounts
        ? "my-accounts"
        : (payees ?? []).length > 0
          ? "saved-payee"
          : "new-payee";
  const mode: DestinationMode = preferredMode ?? defaultMode;

  const parsedAmount = parseAmount(amount);
  const amountValid = parsedAmount.ok;
  const referenceTooLong = reference.length > REFERENCE_MAX;

  const newPayeeNameValid =
    newPayeeName.trim().length > 0 && newPayeeName.length <= NAME_MAX;
  const newPayeeSortCodeValid = SORT_CODE_PATTERN.test(newPayeeSortCode);
  const newPayeeAccountNumberValid = /^\d{8}$/.test(newPayeeAccountNumber);
  const newPayeeFieldsValid =
    newPayeeNameValid && newPayeeSortCodeValid && newPayeeAccountNumberValid;

  const duplicateSavedPayee = useMemo(() => {
    if (mode !== "new-payee") return null;
    if (!newPayeeSortCodeValid || !newPayeeAccountNumberValid) return null;
    return (
      (payees ?? []).find(
        (p) =>
          p.sortCode === newPayeeSortCode &&
          p.accountNumber === newPayeeAccountNumber,
      ) ?? null
    );
  }, [
    mode,
    payees,
    newPayeeSortCode,
    newPayeeAccountNumber,
    newPayeeSortCodeValid,
    newPayeeAccountNumberValid,
  ]);
  const newPayeeBlockedByDuplicate =
    saveAsPayee && duplicateSavedPayee !== null;

  const myAccountsReady =
    Boolean(destUid) && Boolean(destIdentifiers) && mode === "my-accounts";
  const savedPayeeReady = mode === "saved-payee" && selectedPayee !== null;
  const newPayeeReady =
    mode === "new-payee" && newPayeeFieldsValid && !newPayeeBlockedByDuplicate;

  const destinationReady = myAccountsReady || savedPayeeReady || newPayeeReady;

  function handleModeChange(next: DestinationMode) {
    setPreferredMode(next);
    setDestUid("");
    setSelectedPayeeId("");
    setCopPressedThrough(false);
  }

  const destinationLabel = resolveDestinationLabel({
    mode,
    destAccount,
    selectedPayee,
    newPayeeName,
  });

  const { transfer, isPending, insufficientFunds, reset } = useTransfer({
    onSuccess: (response) => {
      setSent({
        payment: response,
        sourceAccountName: sourceAccount?.name ?? "your account",
        destinationLabel,
      });
    },
  });

  function fireTransferNow() {
    if (!parsedAmount.ok) return;
    let toSortCode: string | undefined;
    let toAccountNumber: string | undefined;
    if (mode === "my-accounts") {
      toSortCode = destIdentifiers?.bankIdentifier;
      toAccountNumber = destIdentifiers?.accountIdentifier;
    } else if (mode === "saved-payee" && selectedPayee) {
      toSortCode = selectedPayee.sortCode;
      toAccountNumber = selectedPayee.accountNumber;
    } else if (mode === "new-payee") {
      toSortCode = newPayeeSortCode;
      toAccountNumber = newPayeeAccountNumber;
    }
    if (!toSortCode || !toAccountNumber) return;
    transfer({
      fromAccountUid: sourceUid,
      toSortCode,
      toAccountNumber,
      amountMinorUnits: parsedAmount.minorUnits,
      description: reference.length > 0 ? reference : undefined,
      copVerdictOverride: copPressedThrough,
      toName: resolveBeneficiaryName({
        mode,
        destAccount,
        selectedPayee,
        newPayeeName,
      }),
    });
  }

  const { createPayee, isPending: isSavingPayee } = useCreatePayee({
    navigateOnSuccess: false,
    onSuccess: () => {
      fireTransferNow();
    },
  });

  if (sent) {
    return (
      <PaymentOutcome
        initial={sent.payment}
        sourceUid={sourceUid}
        sourceAccountName={sent.sourceAccountName}
        destinationLabel={sent.destinationLabel}
      />
    );
  }

  return (
    <StepFlow
      form={form}
      ariaLabel="Send money"
      steps={[
        {
          id: "source",
          title: "Choose source",
          autoAdvanceOnSelection: true,
          render: () => (
            <SourceStep
              accountList={accountList}
              accountsLoading={accountsLoading}
              sourceUid={sourceUid}
              onSelect={(uid) => {
                setSourceOverride(uid);
                if (uid === destUid) setDestUid("");
              }}
            />
          ),
        },
        {
          id: "destination",
          title: "Choose destination",
          autoAdvanceOnSelection: mode !== "new-payee",
          render: () => (
            <DestinationStep
              mode={mode}
              onModeChange={handleModeChange}
              showMyAccounts={showMyAccounts}
              otherAccounts={otherAccounts}
              destUid={destUid}
              onDestUidChange={setDestUid}
              payees={payees ?? null}
              payeesLoading={payeesLoading}
              payeesError={payeesError}
              selectedPayeeId={selectedPayeeId}
              onSelectedPayeeIdChange={setSelectedPayeeId}
              newPayeeName={newPayeeName}
              onNewPayeeNameChange={setNewPayeeName}
              newPayeeSortCode={newPayeeSortCode}
              onNewPayeeSortCodeChange={setNewPayeeSortCode}
              newPayeeAccountNumber={newPayeeAccountNumber}
              onNewPayeeAccountNumberChange={setNewPayeeAccountNumber}
              saveAsPayee={saveAsPayee}
              onSaveAsPayeeChange={setSaveAsPayee}
              duplicateSavedPayee={duplicateSavedPayee}
              destinationReady={destinationReady}
              onCopPressedThroughChange={setCopPressedThrough}
            />
          ),
        },
        {
          id: "amount",
          title: "Enter amount",
          render: () => (
            <AmountStep
              amount={amount}
              onAmountChange={(next) => {
                if (insufficientFunds) reset();
                setAmount(next);
              }}
              reference={reference}
              onReferenceChange={setReference}
              amountValid={amountValid}
              referenceTooLong={referenceTooLong}
            />
          ),
        },
        {
          id: "confirm",
          title: "Review and send",
          render: () => (
            <ConfirmStep
              sourceAccount={sourceAccount}
              destinationLabel={destinationLabel}
              amountMinorUnits={parsedAmount.ok ? parsedAmount.minorUnits : 0}
              reference={reference}
              isPending={isPending || isSavingPayee}
              insufficientFunds={insufficientFunds}
              onSend={() => {
                if (mode === "new-payee" && saveAsPayee) {
                  createPayee({
                    name: newPayeeName,
                    sortCode: newPayeeSortCode,
                    accountNumber: newPayeeAccountNumber,
                  });
                  return;
                }
                fireTransferNow();
              }}
            />
          ),
        },
      ]}
    />
  );
}

function SourceStep({
  accountList,
  accountsLoading,
  sourceUid,
  onSelect,
}: {
  accountList: AccountResponse[];
  accountsLoading: boolean;
  sourceUid: string;
  onSelect: (accountUid: string) => void;
}) {
  const { next, autoAdvanceOnSelection } = useStepFlow();
  const advance = useAutoAdvance(autoAdvanceOnSelection);
  const showContinue = !autoAdvanceOnSelection || !sourceUid;
  return (
    <div className="space-y-6">
      <h1
        tabIndex={-1}
        className="text-2xl font-semibold tracking-tight outline-none"
      >
        Where from?
      </h1>
      {accountsLoading ? (
        <p className="text-sm text-muted-foreground">Loading accounts…</p>
      ) : null}
      {accountList.length > 0 ? (
        <div
          role="radiogroup"
          aria-label="From"
          data-testid="source-list"
          className="space-y-2"
        >
          {accountList.map((a) => (
            <SourceCard
              key={a.accountUid}
              account={a}
              checked={a.accountUid === sourceUid}
              onChange={() => onSelect(a.accountUid)}
              autoAdvance={advance}
            />
          ))}
        </div>
      ) : null}
      {showContinue ? (
        <Button type="button" onClick={next} disabled={!sourceUid}>
          Choose source
        </Button>
      ) : null}
    </div>
  );
}

function SourceCard({
  account,
  checked,
  onChange,
  autoAdvance,
}: {
  account: AccountResponse;
  checked: boolean;
  onChange: () => void;
  autoAdvance: AutoAdvanceHandlers;
}) {
  return (
    <label
      className={cn(
        "flex cursor-pointer items-center gap-3 rounded-lg border border-border p-3",
        checked && "border-primary bg-primary/5",
      )}
    >
      <input
        type="radio"
        name="source-account"
        value={account.accountUid}
        checked={checked}
        onChange={onChange}
        {...autoAdvance}
      />
      <span className="text-sm font-medium">{account.name}</span>
    </label>
  );
}

function DestinationStep({
  mode,
  onModeChange,
  showMyAccounts,
  otherAccounts,
  destUid,
  onDestUidChange,
  payees,
  payeesLoading,
  payeesError,
  selectedPayeeId,
  onSelectedPayeeIdChange,
  newPayeeName,
  onNewPayeeNameChange,
  newPayeeSortCode,
  onNewPayeeSortCodeChange,
  newPayeeAccountNumber,
  onNewPayeeAccountNumberChange,
  saveAsPayee,
  onSaveAsPayeeChange,
  duplicateSavedPayee,
  destinationReady,
  onCopPressedThroughChange,
}: {
  mode: DestinationMode;
  onModeChange: (next: DestinationMode) => void;
  showMyAccounts: boolean;
  otherAccounts: AccountResponse[];
  destUid: string;
  onDestUidChange: (uid: string) => void;
  payees: PayeeResponse[] | null;
  payeesLoading: boolean;
  payeesError: boolean;
  selectedPayeeId: string;
  onSelectedPayeeIdChange: (id: string) => void;
  newPayeeName: string;
  onNewPayeeNameChange: (next: string) => void;
  newPayeeSortCode: string;
  onNewPayeeSortCodeChange: (next: string) => void;
  newPayeeAccountNumber: string;
  onNewPayeeAccountNumberChange: (next: string) => void;
  saveAsPayee: boolean;
  onSaveAsPayeeChange: (next: boolean) => void;
  duplicateSavedPayee: PayeeResponse | null;
  destinationReady: boolean;
  onCopPressedThroughChange: (pressedThrough: boolean) => void;
}) {
  const { next, autoAdvanceOnSelection } = useStepFlow();
  const advance = useAutoAdvance(autoAdvanceOnSelection);
  const showContinue = mode === "new-payee";
  const [copaPanel, setCopaPanel] = useState<CopaUiState | null>(null);

  const { confirmPayee, isPending: isConfirming } = useConfirmationOfPayee({
    onSuccess: (response) => {
      const result = copaNextStep(response, "transfer");
      if (result.kind === "MATCH") {
        setCopaPanel(null);
        next();
        return;
      }
      setCopaPanel(result);
    },
  });

  function handleContinue() {
    if (!destinationReady) return;
    if (mode === "new-payee") {
      setCopaPanel(null);
      onCopPressedThroughChange(false);
      confirmPayee({
        name: newPayeeName,
        sortCode: newPayeeSortCode,
        accountNumber: newPayeeAccountNumber,
      });
      return;
    }
    next();
  }

  function handleModeChange(nextMode: DestinationMode) {
    onModeChange(nextMode);
    setCopaPanel(null);
  }

  return (
    <div className="space-y-6">
      <h1
        tabIndex={-1}
        className="text-2xl font-semibold tracking-tight outline-none"
      >
        Where to?
      </h1>

      <DestinationModeSegmented
        value={mode}
        onChange={handleModeChange}
        showMyAccounts={showMyAccounts}
      />

      {mode === "my-accounts" ? (
        <MyAccountsBody
          accounts={otherAccounts}
          selectedUid={destUid}
          onSelect={onDestUidChange}
          autoAdvance={advance}
        />
      ) : null}

      {mode === "saved-payee" ? (
        <SavedPayeeBody
          payees={payees}
          isPending={payeesLoading}
          isError={payeesError}
          selectedPayeeId={selectedPayeeId}
          onSelect={onSelectedPayeeIdChange}
          autoAdvance={advance}
        />
      ) : null}

      {mode === "new-payee" ? (
        <NewPayeeBody
          name={newPayeeName}
          onNameChange={onNewPayeeNameChange}
          sortCode={newPayeeSortCode}
          onSortCodeChange={onNewPayeeSortCodeChange}
          accountNumber={newPayeeAccountNumber}
          onAccountNumberChange={onNewPayeeAccountNumberChange}
          saveAsPayee={saveAsPayee}
          onSaveAsPayeeChange={onSaveAsPayeeChange}
          duplicateSavedPayee={duplicateSavedPayee}
        />
      ) : null}

      {copaPanel ? (
        <CopaVerdictPanel
          state={copaPanel}
          enteredName={newPayeeName}
          onAccept={() => {
            onCopPressedThroughChange(true);
            setCopaPanel(null);
            next();
          }}
          onUseOnRecord={() => {
            if (copaPanel.kind !== "CLOSE_MATCH") return;
            onNewPayeeNameChange(copaPanel.onRecordName);
            onCopPressedThroughChange(false);
            setCopaPanel(null);
            next();
          }}
          onEdit={() => {
            onCopPressedThroughChange(false);
            setCopaPanel(null);
          }}
          onSendAnyway={() => {
            onCopPressedThroughChange(true);
            setCopaPanel(null);
            next();
          }}
        />
      ) : null}

      {showContinue ? (
        <Button
          type="button"
          onClick={handleContinue}
          disabled={!destinationReady || isConfirming || Boolean(copaPanel)}
        >
          {isConfirming ? "Verifying…" : "Verify payee"}
        </Button>
      ) : null}
    </div>
  );
}

function AmountStep({
  amount,
  onAmountChange,
  reference,
  onReferenceChange,
  amountValid,
  referenceTooLong,
}: {
  amount: string;
  onAmountChange: (next: string) => void;
  reference: string;
  onReferenceChange: (next: string) => void;
  amountValid: boolean;
  referenceTooLong: boolean;
}) {
  const { next } = useStepFlow();
  const amountId = useId();
  const referenceId = useId();

  return (
    <div className="space-y-6">
      <h1
        tabIndex={-1}
        className="text-2xl font-semibold tracking-tight outline-none"
      >
        How much?
      </h1>

      <div className="space-y-1.5">
        <Label htmlFor={amountId}>Amount</Label>
        <AmountInput id={amountId} value={amount} onChange={onAmountChange} />
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
        {reference.length > REFERENCE_COUNTER_THRESHOLD ? (
          <p
            data-testid="reference-counter"
            className="text-xs text-muted-foreground"
          >
            {reference.length} / {REFERENCE_MAX}
          </p>
        ) : null}
      </div>

      <Button
        type="button"
        onClick={next}
        disabled={!amountValid || referenceTooLong}
      >
        Review
      </Button>
    </div>
  );
}

function ConfirmStep({
  sourceAccount,
  destinationLabel,
  amountMinorUnits,
  reference,
  isPending,
  insufficientFunds,
  onSend,
}: {
  sourceAccount: AccountResponse | null;
  destinationLabel: string;
  amountMinorUnits: number;
  reference: string;
  isPending: boolean;
  insufficientFunds: boolean;
  onSend: () => void;
}) {
  return (
    <div className="space-y-6">
      <h1
        tabIndex={-1}
        className="text-2xl font-semibold tracking-tight outline-none"
      >
        Review and send
      </h1>

      <dl
        data-testid="transfer-confirmation"
        className="grid gap-3 rounded-lg border border-border p-4 text-sm"
      >
        <div className="grid grid-cols-3 gap-2">
          <dt className="text-muted-foreground">From</dt>
          <dd className="col-span-2">{sourceAccount?.name ?? ""}</dd>
        </div>
        <div className="grid grid-cols-3 gap-2">
          <dt className="text-muted-foreground">To</dt>
          <dd className="col-span-2">{destinationLabel}</dd>
        </div>
        <div className="grid grid-cols-3 gap-2">
          <dt className="text-muted-foreground">Amount</dt>
          <dd className="col-span-2">
            <Money minorUnits={amountMinorUnits} />
          </dd>
        </div>
        {reference.length > 0 ? (
          <div className="grid grid-cols-3 gap-2">
            <dt className="text-muted-foreground">Reference</dt>
            <dd className="col-span-2">{reference}</dd>
          </div>
        ) : null}
      </dl>

      {insufficientFunds ? (
        <p
          role="alert"
          data-testid="transfer-insufficient-funds"
          className="text-sm text-destructive"
        >
          Not enough money in this account.
        </p>
      ) : null}

      <Button
        type="button"
        data-testid="transfer-send"
        onClick={onSend}
        disabled={isPending}
      >
        {isPending ? "Sending…" : "Send money"}
      </Button>
    </div>
  );
}

function DestinationModeSegmented({
  value,
  onChange,
  showMyAccounts,
}: {
  value: DestinationMode;
  onChange: (next: DestinationMode) => void;
  showMyAccounts: boolean;
}) {
  return (
    <fieldset className="space-y-2">
      <legend className="text-sm font-medium">Destination</legend>
      <div
        role="radiogroup"
        aria-label="Destination"
        className="inline-flex rounded-lg border border-input p-1"
      >
        {showMyAccounts ? (
          <SegmentedRadio
            name="destination-mode"
            value="my-accounts"
            checked={value === "my-accounts"}
            onChange={() => onChange("my-accounts")}
            label="My accounts"
          />
        ) : null}
        <SegmentedRadio
          name="destination-mode"
          value="saved-payee"
          checked={value === "saved-payee"}
          onChange={() => onChange("saved-payee")}
          label="Saved payee"
        />
        <SegmentedRadio
          name="destination-mode"
          value="new-payee"
          checked={value === "new-payee"}
          onChange={() => onChange("new-payee")}
          label="New payee"
        />
      </div>
    </fieldset>
  );
}

function SegmentedRadio({
  name,
  value,
  checked,
  onChange,
  label,
}: {
  name: string;
  value: string;
  checked: boolean;
  onChange: () => void;
  label: string;
}) {
  return (
    <label
      className={cn(
        "cursor-pointer rounded-md px-3 py-1.5 text-sm pointer-coarse:py-3 has-focus-visible:ring-2 has-focus-visible:ring-ring/50",
        checked && "bg-primary text-primary-foreground",
      )}
    >
      <input
        type="radio"
        name={name}
        value={value}
        checked={checked}
        onChange={onChange}
        className="sr-only"
      />
      <span>{label}</span>
    </label>
  );
}

function MyAccountsBody({
  accounts,
  selectedUid,
  onSelect,
  autoAdvance,
}: {
  accounts: AccountResponse[];
  selectedUid: string;
  onSelect: (uid: string) => void;
  autoAdvance: AutoAdvanceHandlers;
}) {
  return (
    <div
      data-testid="my-accounts-list"
      role="radiogroup"
      aria-label="To account"
      className="space-y-2"
    >
      {accounts.map((a) => {
        const checked = a.accountUid === selectedUid;
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
              name="my-accounts-destination"
              value={a.accountUid}
              checked={checked}
              onChange={() => onSelect(a.accountUid)}
              {...autoAdvance}
            />
            <span className="text-sm font-medium">{a.name}</span>
          </label>
        );
      })}
    </div>
  );
}

function payeeDisplayName(payee: PayeeResponse): string {
  return payee.nickname ?? payee.name;
}

function resolveDestinationLabel({
  mode,
  destAccount,
  selectedPayee,
  newPayeeName,
}: {
  mode: DestinationMode;
  destAccount: AccountResponse | null;
  selectedPayee: PayeeResponse | null;
  newPayeeName: string;
}): string {
  if (mode === "my-accounts") return destAccount?.name ?? "";
  if (mode === "saved-payee" && selectedPayee)
    return payeeDisplayName(selectedPayee);
  if (mode === "new-payee") return newPayeeName;
  return "";
}

function resolveBeneficiaryName({
  mode,
  destAccount,
  selectedPayee,
  newPayeeName,
}: {
  mode: DestinationMode;
  destAccount: AccountResponse | null;
  selectedPayee: PayeeResponse | null;
  newPayeeName: string;
}): string {
  if (mode === "my-accounts") return destAccount?.name ?? "";
  if (mode === "saved-payee" && selectedPayee) return selectedPayee.name;
  if (mode === "new-payee") return newPayeeName.trim();
  return "";
}

function NewPayeeBody({
  name,
  onNameChange,
  sortCode,
  onSortCodeChange,
  accountNumber,
  onAccountNumberChange,
  saveAsPayee,
  onSaveAsPayeeChange,
  duplicateSavedPayee,
}: {
  name: string;
  onNameChange: (next: string) => void;
  sortCode: string;
  onSortCodeChange: (next: string) => void;
  accountNumber: string;
  onAccountNumberChange: (next: string) => void;
  saveAsPayee: boolean;
  onSaveAsPayeeChange: (next: boolean) => void;
  duplicateSavedPayee: PayeeResponse | null;
}) {
  const nameId = useId();
  const sortCodeId = useId();
  const accountNumberId = useId();
  const saveAsPayeeId = useId();
  const duplicateMessageId = useId();
  const showDuplicateWarning = saveAsPayee && duplicateSavedPayee !== null;
  return (
    <div data-testid="new-payee-body" className="space-y-4">
      <div className="space-y-1.5">
        <Label htmlFor={nameId}>Name on the account</Label>
        <Input
          id={nameId}
          type="text"
          autoComplete="off"
          maxLength={NAME_MAX}
          value={name}
          onChange={(e) => onNameChange(e.target.value)}
        />
      </div>
      <div className="space-y-1.5">
        <Label htmlFor={sortCodeId}>Sort code</Label>
        <SortCodeInput
          id={sortCodeId}
          value={sortCode}
          onChange={onSortCodeChange}
        />
      </div>
      <div className="space-y-1.5">
        <Label htmlFor={accountNumberId}>Account number</Label>
        <AccountNumberInput
          id={accountNumberId}
          value={accountNumber}
          onChange={onAccountNumberChange}
        />
      </div>
      <div className="space-y-1.5">
        <div className="flex items-center gap-2">
          <input
            id={saveAsPayeeId}
            data-testid="save-as-payee"
            type="checkbox"
            checked={saveAsPayee}
            onChange={(e) => onSaveAsPayeeChange(e.target.checked)}
            aria-describedby={
              showDuplicateWarning ? duplicateMessageId : undefined
            }
            className="size-4"
          />
          <Label htmlFor={saveAsPayeeId} className="font-normal">
            Save as a payee
          </Label>
        </div>
        {showDuplicateWarning ? (
          <p
            id={duplicateMessageId}
            data-testid="new-payee-duplicate"
            className="text-sm text-destructive"
          >
            You already have a payee saved with this sort code and account
            number ({duplicateSavedPayee.nickname ?? duplicateSavedPayee.name}).
            Uncheck “Save as a payee” to send without saving, or pick them from
            Saved payee.
          </p>
        ) : null}
      </div>
    </div>
  );
}

function SavedPayeeBody({
  payees,
  isPending,
  isError,
  selectedPayeeId,
  onSelect,
  autoAdvance,
}: {
  payees: PayeeResponse[] | null;
  isPending: boolean;
  isError: boolean;
  selectedPayeeId: string;
  onSelect: (payeeId: string) => void;
  autoAdvance: AutoAdvanceHandlers;
}) {
  if (isPending) {
    return (
      <div
        data-testid="saved-payee-skeleton"
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
      <p
        data-testid="saved-payee-error"
        className="text-sm text-muted-foreground"
      >
        Couldn’t load payees
      </p>
    );
  }
  const list = payees ?? [];
  if (list.length === 0) {
    return (
      <div data-testid="saved-payee-empty" className="space-y-3">
        <p className="text-sm text-muted-foreground">No saved payees yet.</p>
        <Button asChild variant="outline">
          <Link to="/move/payees/new">Add a payee</Link>
        </Button>
      </div>
    );
  }
  const sorted = [...list].sort((a, b) =>
    payeeDisplayName(a).localeCompare(payeeDisplayName(b)),
  );
  return (
    <div
      data-testid="saved-payee-list"
      role="radiogroup"
      aria-label="Saved payees"
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
              name="saved-payee-destination"
              value={p.payeeId}
              checked={checked}
              onChange={() => onSelect(p.payeeId)}
              className="mt-1"
              {...autoAdvance}
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
  );
}
