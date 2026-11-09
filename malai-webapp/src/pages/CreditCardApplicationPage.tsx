import { useId, useState } from "react";
import { Link } from "react-router-dom";
import type { CreditCardApplicationResponse } from "@/api/generated/types.gen";
import { useUser } from "@/auth/useUser";
import { displayNameOf } from "@/components/AppShell";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { nativeSelectClassName } from "@/components/ui/native-select";
import { SuccessTick } from "@/components/SuccessTick";
import { CardArt } from "@/cards/CardArt";
import { useCreditCardApplication } from "@/cards/useCreditCardApplication";
import { Money } from "@/accounts/Money";
import { useFocusOnMount } from "@/lib/useFocusOnMount";
import { usePageTitle } from "@/lib/usePageTitle";

const CREDIT_LIMIT_MINOR_UNITS = 250_000;

const EMPLOYMENT_OPTIONS = [
  "Employed full-time",
  "Employed part-time",
  "Self-employed",
  "Retired",
  "Student",
];

const INCOME_OPTIONS = [
  "Under £20,000",
  "£20,000 – £39,999",
  "£40,000 – £59,999",
  "£60,000 or more",
];

export function CreditCardApplicationPage() {
  usePageTitle("Apply for a credit card");
  const headingRef = useFocusOnMount<HTMLHeadingElement>();
  const { user } = useUser();

  const [employment, setEmployment] = useState(EMPLOYMENT_OPTIONS[0]!);
  const [income, setIncome] = useState(INCOME_OPTIONS[1]!);
  const [confirmed, setConfirmed] = useState(false);
  const [approved, setApproved] =
    useState<CreditCardApplicationResponse | null>(null);

  const { apply, isPending, alreadyExists } = useCreditCardApplication({
    onSuccess: setApproved,
  });

  const employmentId = useId();
  const incomeId = useId();
  const confirmId = useId();

  if (approved) {
    return (
      <Approved
        response={approved}
        cardholderName={user ? displayNameOf(user) : undefined}
      />
    );
  }

  return (
    <div className="mx-auto w-full max-w-3xl p-6">
      <Link
        to="/cards"
        className="text-sm text-muted-foreground hover:underline focus-visible:underline"
      >
        ← Cards
      </Link>

      <section aria-labelledby="apply-heading" className="mt-4">
        <h1
          id="apply-heading"
          ref={headingRef}
          tabIndex={-1}
          className="text-2xl font-semibold tracking-tight outline-none"
        >
          Apply for a credit card
        </h1>
        <p className="mt-3 text-sm text-muted-foreground">
          The malai credit card comes with a{" "}
          <Money minorUnits={CREDIT_LIMIT_MINOR_UNITS} /> credit limit, a
          monthly statement, and no fees. You’ll get an instant decision.
        </p>

        {alreadyExists ? (
          <div
            data-testid="apply-already-exists"
            className="mt-6 rounded-xl border border-border bg-card p-6 shadow-xs"
          >
            <p className="font-medium">You already have a malai credit card</p>
            <p className="mt-1 text-sm text-muted-foreground">
              One credit card per profile — yours is ready in Cards.
            </p>
            <Button asChild variant="outline" className="mt-4">
              <Link to="/cards" data-testid="apply-view-cards">
                View your cards
              </Link>
            </Button>
          </div>
        ) : (
          <div className="mt-6 space-y-6 rounded-xl border border-border bg-card p-6 shadow-xs">
            <div className="space-y-1.5">
              <Label htmlFor={employmentId}>Employment status</Label>
              <select
                id={employmentId}
                data-testid="apply-employment"
                value={employment}
                onChange={(e) => setEmployment(e.target.value)}
                className={nativeSelectClassName}
              >
                {EMPLOYMENT_OPTIONS.map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                ))}
              </select>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor={incomeId}>Annual income</Label>
              <select
                id={incomeId}
                data-testid="apply-income"
                value={income}
                onChange={(e) => setIncome(e.target.value)}
                className={nativeSelectClassName}
              >
                {INCOME_OPTIONS.map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                ))}
              </select>
            </div>

            <label
              htmlFor={confirmId}
              className="flex items-start gap-2 text-sm"
            >
              <input
                id={confirmId}
                type="checkbox"
                data-testid="apply-confirm"
                checked={confirmed}
                onChange={(e) => setConfirmed(e.target.checked)}
                className="mt-0.5"
              />
              I confirm these details are accurate and I’d like to open a credit
              card account.
            </label>

            <Button
              type="button"
              data-testid="apply-submit"
              disabled={!confirmed || isPending}
              onClick={apply}
            >
              {isPending ? "Checking…" : "Apply now"}
            </Button>
          </div>
        )}
      </section>
    </div>
  );
}

function Approved({
  response,
  cardholderName,
}: {
  response: CreditCardApplicationResponse;
  cardholderName: string | undefined;
}) {
  const headingRef = useFocusOnMount<HTMLHeadingElement>();
  const { account, card } = response;

  return (
    <div className="mx-auto w-full max-w-3xl p-6">
      <section
        data-testid="apply-approved"
        aria-label="Application approved"
        className="flex flex-col items-center gap-6 py-10 text-center"
      >
        <SuccessTick />
        <div className="space-y-2">
          <h1
            ref={headingRef}
            tabIndex={-1}
            className="text-2xl font-semibold tracking-tight outline-none"
          >
            You’re approved
          </h1>
          <p className="text-sm text-muted-foreground">
            {account.name} is open with a{" "}
            <Money minorUnits={CREDIT_LIMIT_MINOR_UNITS} /> limit, and your new
            card is ready to spend on.
          </p>
        </div>
        <CardArt
          variant={card.variant}
          status={card.status}
          pan={card.maskedPan}
          expiry={card.expiry}
          cardholderName={cardholderName}
        />
        <div className="flex flex-wrap justify-center gap-2">
          <Button asChild>
            <Link to={`/cards/${card.cardId}`} data-testid="apply-view-card">
              View your card
            </Link>
          </Button>
          <Button asChild variant="outline">
            <Link
              to={`/accounts/${account.accountUid}`}
              data-testid="apply-view-account"
            >
              Go to your account
            </Link>
          </Button>
        </div>
      </section>
    </div>
  );
}
