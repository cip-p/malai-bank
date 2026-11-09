import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Eye, EyeOff, ShieldAlert, Snowflake, Sun } from "lucide-react";
import type { CardReplacementResponse } from "@/api/generated/types.gen";
import { useAccounts } from "@/accounts/useAccounts";
import { useUser } from "@/auth/useUser";
import { displayNameOf } from "@/components/AppShell";
import { Button } from "@/components/ui/button";
import { SuccessTick } from "@/components/SuccessTick";
import { CardArt } from "@/cards/CardArt";
import { ReportLostStolenDialog } from "@/cards/ReportLostStolenDialog";
import { useCardReveal } from "@/cards/useCardReveal";
import { useCards } from "@/cards/useCards";
import { useFreezeCard } from "@/cards/useFreezeCard";
import { useReplaceCard } from "@/cards/useReplaceCard";
import { useUnfreezeCard } from "@/cards/useUnfreezeCard";
import { StatusPanel } from "@/components/StatusPanel";
import { useFocusOnMount } from "@/lib/useFocusOnMount";
import { usePageTitle } from "@/lib/usePageTitle";

export function CardDetailPage() {
  const { cardId = "" } = useParams<{ cardId: string }>();
  usePageTitle("Card");
  const headingRef = useFocusOnMount<HTMLHeadingElement>();

  const { cards, isPending, isError } = useCards();
  const { accounts } = useAccounts();
  const { user } = useUser();

  const [revealed, setRevealed] = useState(false);
  const reveal = useCardReveal(cardId, revealed);
  const [replacement, setReplacement] =
    useState<CardReplacementResponse | null>(null);
  const [reportOpen, setReportOpen] = useState(false);

  const { freeze, isPending: freezing } = useFreezeCard();
  const { unfreeze, isPending: unfreezing } = useUnfreezeCard();
  const { replace, isPending: replacing } = useReplaceCard({
    onSuccess: (response) => {
      setReportOpen(false);
      setReplacement(response);
    },
  });

  const card = cards?.find((c) => c.cardId === cardId) ?? null;
  const account = accounts?.find((a) => a.accountUid === card?.accountId);
  const fallbackName = user ? displayNameOf(user) : undefined;

  if (replacement) {
    return (
      <ReplacementIssued
        replacement={replacement}
        cardholderName={fallbackName}
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

      {isPending ? (
        <DetailSkeleton />
      ) : isError || !cards ? (
        <DetailError />
      ) : !card ? (
        <CardNotFound />
      ) : (
        <section aria-labelledby="card-detail-heading" className="mt-4">
          <h1
            id="card-detail-heading"
            ref={headingRef}
            tabIndex={-1}
            data-testid="card-detail-heading"
            className="text-2xl font-semibold tracking-tight outline-none"
          >
            {card.variant === "CREDIT" ? "Credit card" : "Debit card"}
          </h1>
          {account ? (
            <p className="mt-1 text-sm text-muted-foreground">
              Linked to{" "}
              <Link
                to={`/accounts/${card.accountId}`}
                data-testid="card-detail-account-link"
                className="font-medium text-foreground hover:underline focus-visible:underline"
              >
                {account.name}
              </Link>
            </p>
          ) : null}

          <div className="mt-6">
            <CardArt
              variant={card.variant}
              status={card.status}
              pan={reveal.detail ? reveal.detail.pan : card.maskedPan}
              expiry={reveal.detail ? reveal.detail.expiry : card.expiry}
              cvv={reveal.detail?.cvv}
              cardholderName={reveal.detail?.cardholderName ?? fallbackName}
            />
          </div>

          {reveal.isError ? (
            <p
              data-testid="card-reveal-error"
              className="mt-3 text-sm text-destructive"
            >
              We couldn’t reveal your card details. Please try again.
            </p>
          ) : null}

          {card.status !== "CANCELLED" ? (
            <div className="mt-6 flex flex-wrap gap-2">
              <Button
                type="button"
                variant="outline"
                data-testid="card-reveal-toggle"
                aria-pressed={revealed}
                disabled={revealed && reveal.isPending}
                onClick={() => setRevealed((r) => !r)}
              >
                {revealed ? (
                  <>
                    <EyeOff aria-hidden="true" />
                    Hide details
                  </>
                ) : (
                  <>
                    <Eye aria-hidden="true" />
                    Reveal details
                  </>
                )}
              </Button>

              {card.status === "ACTIVE" ? (
                <Button
                  type="button"
                  data-testid="card-freeze"
                  disabled={freezing}
                  onClick={() => freeze(card.cardId)}
                >
                  <Snowflake aria-hidden="true" />
                  {freezing ? "Freezing…" : "Freeze card"}
                </Button>
              ) : (
                <Button
                  type="button"
                  data-testid="card-unfreeze"
                  disabled={unfreezing}
                  onClick={() => unfreeze(card.cardId)}
                >
                  <Sun aria-hidden="true" />
                  {unfreezing ? "Unfreezing…" : "Unfreeze card"}
                </Button>
              )}

              <Button
                type="button"
                variant="destructive"
                data-testid="card-report-lost"
                disabled={replacing}
                onClick={() => setReportOpen(true)}
              >
                <ShieldAlert aria-hidden="true" />
                {replacing ? "Replacing…" : "Report lost or stolen"}
              </Button>
            </div>
          ) : (
            <p
              data-testid="card-cancelled-note"
              className="mt-6 text-sm text-muted-foreground"
            >
              This card has been cancelled and can’t be used.
            </p>
          )}

          {card.status === "FROZEN" ? (
            <p
              data-testid="card-frozen-note"
              className="mt-4 text-sm text-muted-foreground"
            >
              While frozen, every purchase on this card is declined. Unfreeze it
              any time.
            </p>
          ) : null}

          <ReportLostStolenDialog
            open={reportOpen}
            onCancel={() => setReportOpen(false)}
            onConfirm={() => replace(card.cardId)}
          />
        </section>
      )}
    </div>
  );
}

function ReplacementIssued({
  replacement,
  cardholderName,
}: {
  replacement: CardReplacementResponse;
  cardholderName: string | undefined;
}) {
  const headingRef = useFocusOnMount<HTMLHeadingElement>();
  const card = replacement.replacementCard;

  return (
    <div className="mx-auto w-full max-w-3xl p-6">
      <section
        data-testid="card-replacement-issued"
        aria-label="Replacement card issued"
        className="flex flex-col items-center gap-6 py-10 text-center"
      >
        <SuccessTick />
        <div className="space-y-2">
          <h1
            ref={headingRef}
            tabIndex={-1}
            className="text-2xl font-semibold tracking-tight outline-none"
          >
            Your replacement card is ready
          </h1>
          <p className="text-sm text-muted-foreground">
            The old card is cancelled. Your new card has a new number and works
            straight away.
          </p>
        </div>
        <CardArt
          variant={card.variant}
          status={card.status}
          pan={card.maskedPan}
          expiry={card.expiry}
          cardholderName={cardholderName}
        />
        <Button asChild>
          <Link
            to={`/cards/${card.cardId}`}
            data-testid="card-replacement-view"
          >
            View new card
          </Link>
        </Button>
      </section>
    </div>
  );
}

function CardNotFound() {
  return (
    <div
      data-testid="card-not-found"
      className="mt-6 rounded-xl border border-border bg-card p-8 text-center shadow-xs"
    >
      <p className="font-medium">We couldn’t find that card</p>
      <p className="mt-1 text-sm text-muted-foreground">
        It may belong to a different profile.
      </p>
    </div>
  );
}

function DetailError() {
  return (
    <StatusPanel
      testId="card-detail-error"
      role="alert"
      className="mt-6"
      title="We couldn’t load this card"
      body="Please try again in a moment."
    />
  );
}

function DetailSkeleton() {
  return (
    <div
      data-testid="card-detail-skeleton"
      aria-busy="true"
      className="mt-4 space-y-4"
    >
      <span className="sr-only">Loading card</span>
      <div className="h-8 w-40 rounded-md bg-muted/40 motion-safe:animate-pulse" />
      <div className="aspect-[1.586/1] w-full max-w-sm rounded-2xl bg-muted/40 motion-safe:animate-pulse" />
    </div>
  );
}
