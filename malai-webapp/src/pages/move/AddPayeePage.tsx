import { useId, useState } from "react";
import {
  Controller,
  useForm,
  useFormContext,
  type UseFormReturn,
} from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Link, useSearchParams } from "react-router-dom";
import { zPayeeRequest } from "@/api/generated/zod.gen";
import type {
  ConfirmationOfPayeeResponse,
  PayeeRequest,
  PayeeResponse,
} from "@/api/generated/types.gen";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { StepFlow, useStepFlow } from "@/components/StepFlow";
import { SuccessTick } from "@/components/SuccessTick";
import { useFocusOnMount } from "@/lib/useFocusOnMount";
import { usePageTitle } from "@/lib/usePageTitle";
import { useConfirmationOfPayee } from "@/move-money/useConfirmationOfPayee";
import { useCreatePayee } from "@/move-money/useCreatePayee";
import { copaNextStep, type CopaUiState } from "@/move-money/copaNextStep";
import { CopaVerdictPanel } from "@/move-money/CopaVerdictPanel";
import { SortCodeInput } from "@/move-money/SortCodeInput";
import { AccountNumberInput } from "@/move-money/AccountNumberInput";

export function AddPayeePage() {
  usePageTitle("Add payee");
  const [searchParams] = useSearchParams();
  const form = useForm<PayeeRequest>({
    resolver: zodResolver(zPayeeRequest),
    shouldFocusError: true,
    defaultValues: {
      name: searchParams.get("name") ?? "",
      sortCode: searchParams.get("sortCode") ?? "",
      accountNumber: searchParams.get("accountNumber") ?? "",
      nickname: "",
    },
  });

  const [saved, setSaved] = useState<PayeeResponse | null>(null);
  const { createPayee, isPending: isCreating } = useCreatePayee({
    navigateOnSuccess: false,
    onSuccess: (payee) => setSaved(payee),
  });

  function fireCreate() {
    const values = form.getValues();
    const body: PayeeRequest = {
      name: values.name,
      sortCode: values.sortCode,
      accountNumber: values.accountNumber,
    };
    if (values.nickname && values.nickname.length > 0) {
      body.nickname = values.nickname;
    }
    createPayee(body);
  }

  if (saved) {
    return <SuccessScreen payee={saved} />;
  }

  return (
    <StepFlow
      form={form}
      ariaLabel="Add payee"
      steps={[
        {
          id: "name",
          title: "Name",
          render: () => <NameStep />,
        },
        {
          id: "details",
          title: "Bank details",
          render: () => <DetailsStep />,
        },
        {
          id: "confirm",
          title: "Confirm",
          render: () => (
            <ConfirmStep
              form={form}
              isCreating={isCreating}
              fireCreate={fireCreate}
            />
          ),
        },
      ]}
    />
  );
}

function NameStep() {
  const { next } = useStepFlow();
  const form = useFormContext<PayeeRequest>();
  const nameId = useId();
  const nicknameId = useId();
  const nameError = form.formState.errors.name?.message;
  const nicknameError = form.formState.errors.nickname?.message;

  async function handleContinue() {
    const valid = await form.trigger(["name", "nickname"]);
    if (valid) next();
  }

  return (
    <div className="space-y-6">
      <h1
        tabIndex={-1}
        className="text-2xl font-semibold tracking-tight outline-none"
      >
        Who is the payee?
      </h1>
      <p className="text-sm text-muted-foreground">
        We’ll verify the name against the account before saving.
      </p>

      <div className="space-y-1.5">
        <Label htmlFor={nameId}>Name on the account</Label>
        <Input
          id={nameId}
          type="text"
          autoComplete="off"
          aria-invalid={nameError ? true : undefined}
          aria-describedby={nameError ? `${nameId}-error` : undefined}
          {...form.register("name")}
        />
        {nameError ? (
          <p id={`${nameId}-error`} className="text-sm text-destructive">
            {nameError}
          </p>
        ) : null}
      </div>

      <div className="space-y-1.5">
        <Label htmlFor={nicknameId}>Nickname (optional)</Label>
        <Input
          id={nicknameId}
          type="text"
          autoComplete="off"
          aria-invalid={nicknameError ? true : undefined}
          aria-describedby={nicknameError ? `${nicknameId}-error` : undefined}
          {...form.register("nickname")}
        />
        {nicknameError ? (
          <p id={`${nicknameId}-error`} className="text-sm text-destructive">
            {nicknameError}
          </p>
        ) : null}
      </div>

      <Button type="button" onClick={handleContinue}>
        Continue
      </Button>
    </div>
  );
}

function DetailsStep() {
  const { next } = useStepFlow();
  const form = useFormContext<PayeeRequest>();
  const sortCodeId = useId();
  const accountNumberId = useId();
  const sortCodeError = form.formState.errors.sortCode?.message;
  const accountNumberError = form.formState.errors.accountNumber?.message;

  async function handleContinue() {
    const valid = await form.trigger(["sortCode", "accountNumber"]);
    if (valid) next();
  }

  return (
    <div className="space-y-6">
      <h1
        tabIndex={-1}
        className="text-2xl font-semibold tracking-tight outline-none"
      >
        Bank details
      </h1>

      <div className="space-y-1.5">
        <Label htmlFor={sortCodeId}>Sort code</Label>
        <Controller
          name="sortCode"
          control={form.control}
          render={({ field }) => (
            <SortCodeInput
              id={sortCodeId}
              value={field.value}
              onChange={field.onChange}
              onBlur={field.onBlur}
              aria-invalid={sortCodeError ? true : undefined}
              aria-describedby={
                sortCodeError ? `${sortCodeId}-error` : undefined
              }
            />
          )}
        />
        {sortCodeError ? (
          <p id={`${sortCodeId}-error`} className="text-sm text-destructive">
            {sortCodeError}
          </p>
        ) : null}
      </div>

      <div className="space-y-1.5">
        <Label htmlFor={accountNumberId}>Account number</Label>
        <Controller
          name="accountNumber"
          control={form.control}
          render={({ field }) => (
            <AccountNumberInput
              id={accountNumberId}
              value={field.value}
              onChange={field.onChange}
              onBlur={field.onBlur}
              aria-invalid={accountNumberError ? true : undefined}
              aria-describedby={
                accountNumberError ? `${accountNumberId}-error` : undefined
              }
            />
          )}
        />
        {accountNumberError ? (
          <p
            id={`${accountNumberId}-error`}
            className="text-sm text-destructive"
          >
            {accountNumberError}
          </p>
        ) : null}
      </div>

      <Button type="button" onClick={handleContinue}>
        Continue
      </Button>
    </div>
  );
}

function ConfirmStep({
  form,
  isCreating,
  fireCreate,
}: {
  form: UseFormReturn<PayeeRequest>;
  isCreating: boolean;
  fireCreate: () => void;
}) {
  const [panel, setPanel] = useState<CopaUiState | null>(null);

  const { confirmPayee, isPending: isConfirming } = useConfirmationOfPayee({
    onSuccess: (response: ConfirmationOfPayeeResponse) => {
      const next = copaNextStep(response, "save-payee");
      if (next.kind === "MATCH") {
        setPanel(null);
        fireCreate();
        return;
      }
      setPanel(next);
    },
  });

  const values = form.getValues();
  const busy = isConfirming || isCreating;

  async function handleSave() {
    setPanel(null);
    const valid = await form.trigger([
      "name",
      "sortCode",
      "accountNumber",
      "nickname",
    ]);
    if (!valid) return;
    const current = form.getValues();
    confirmPayee({
      name: current.name,
      sortCode: current.sortCode,
      accountNumber: current.accountNumber,
    });
  }

  return (
    <div className="space-y-6">
      <h1
        tabIndex={-1}
        className="text-2xl font-semibold tracking-tight outline-none"
      >
        Review and save
      </h1>

      <dl
        data-testid="add-payee-confirmation"
        className="grid gap-3 rounded-lg border border-border p-4 text-sm"
      >
        <div className="grid grid-cols-3 gap-2">
          <dt className="text-muted-foreground">Name</dt>
          <dd className="col-span-2">{values.name}</dd>
        </div>
        {values.nickname && values.nickname.length > 0 ? (
          <div className="grid grid-cols-3 gap-2">
            <dt className="text-muted-foreground">Nickname</dt>
            <dd className="col-span-2">{values.nickname}</dd>
          </div>
        ) : null}
        <div className="grid grid-cols-3 gap-2">
          <dt className="text-muted-foreground">Sort code</dt>
          <dd className="col-span-2 tabular-nums">{values.sortCode}</dd>
        </div>
        <div className="grid grid-cols-3 gap-2">
          <dt className="text-muted-foreground">Account number</dt>
          <dd className="col-span-2 tabular-nums">{values.accountNumber}</dd>
        </div>
      </dl>

      {panel ? (
        <CopaVerdictPanel
          state={panel}
          enteredName={values.name}
          onAccept={() => {
            setPanel(null);
            fireCreate();
          }}
          onUseOnRecord={() => {
            if (panel.kind !== "CLOSE_MATCH") return;
            form.setValue("name", panel.onRecordName, {
              shouldDirty: true,
              shouldValidate: false,
            });
            setPanel(null);
            fireCreate();
          }}
          onEdit={() => {
            setPanel(null);
          }}
          onSendAnyway={() => {
            setPanel(null);
            fireCreate();
          }}
        />
      ) : null}

      <Button
        type="button"
        onClick={handleSave}
        disabled={busy}
        data-testid="add-payee-submit"
      >
        {busy ? "Saving…" : "Save payee"}
      </Button>
    </div>
  );
}

function SuccessScreen({ payee }: { payee: PayeeResponse }) {
  const headingRef = useFocusOnMount<HTMLHeadingElement>();
  return (
    <section
      data-testid="add-payee-success"
      aria-label="Payee saved"
      className="flex flex-col items-center gap-6 py-10 text-center"
    >
      <SuccessTick />
      <div className="space-y-2">
        <h1
          ref={headingRef}
          tabIndex={-1}
          className="text-2xl font-semibold tracking-tight outline-none"
        >
          Payee saved
        </h1>
        <p className="text-sm text-muted-foreground">
          {payee.nickname ?? payee.name} is ready to send to.
        </p>
      </div>
      <Button asChild>
        <Link to="/move/payees" data-testid="add-payee-success-back">
          Back to payees
        </Link>
      </Button>
    </section>
  );
}
