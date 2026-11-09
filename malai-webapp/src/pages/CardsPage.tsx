import { Link } from "react-router-dom";
import { CreditCard, Plus } from "lucide-react";
import type { CardResponse } from "@/api/generated/types.gen";
import { useAccounts } from "@/accounts/useAccounts";
import { useUser } from "@/auth/useUser";
import { displayNameOf } from "@/components/AppShell";
import { CardArt } from "@/cards/CardArt";
import { useCards } from "@/cards/useCards";
import { StatusPanel } from "@/components/StatusPanel";
import { useFocusOnMount } from "@/lib/useFocusOnMount";
import { usePageTitle } from "@/lib/usePageTitle";

export function CardsPage() {
  usePageTitle("Cards");
  const headingRef = useFocusOnMount<HTMLHeadingElement>();
  const { cards, isPending, isError } = useCards();
  const { accounts } = useAccounts();
  const { user } = useUser();

  const cardholderName = user ? displayNameOf(user) : undefined;
  const accountList = accounts ?? [];
  const hasCreditCardAccount = accountList.some(
    (account) => account.accountType === "CREDIT_CARD",
  );

  return (
    <div className="mx-auto w-full max-w-3xl p-6">
      <section aria-labelledby="cards-heading">
        <h1
          id="cards-heading"
          ref={headingRef}
          tabIndex={-1}
          className="text-2xl font-semibold tracking-tight outline-none"
        >
          Cards
        </h1>

        {isPending ? (
          <CardsSkeleton />
        ) : isError || !cards ? (
          <CardsError />
        ) : (
          <>
            {cards.length === 0 ? <NoCards /> : null}
            <ul className="mt-6 grid grid-cols-1 gap-6 sm:grid-cols-2">
              {cards.map((card) => (
                <CardTile
                  key={card.cardId}
                  card={card}
                  accountName={
                    accountList.find((a) => a.accountUid === card.accountId)
                      ?.name
                  }
                  cardholderName={cardholderName}
                />
              ))}
              {!hasCreditCardAccount ? <ApplyTile /> : null}
            </ul>
          </>
        )}
      </section>
    </div>
  );
}

const STATUS_LABELS: Record<CardResponse["status"], string> = {
  ACTIVE: "Active",
  FROZEN: "Frozen",
  CANCELLED: "Cancelled",
};

function CardTile({
  card,
  accountName,
  cardholderName,
}: {
  card: CardResponse;
  accountName: string | undefined;
  cardholderName: string | undefined;
}) {
  return (
    <li>
      <Link
        to={`/cards/${card.cardId}`}
        data-testid="card-tile"
        className="group block rounded-2xl outline-none focus-visible:ring-2 focus-visible:ring-ring/50"
      >
        <CardArt
          variant={card.variant}
          status={card.status}
          pan={card.maskedPan}
          expiry={card.expiry}
          cardholderName={cardholderName}
          className="motion-safe:transition-transform group-hover:scale-[1.01]"
        />
        <p className="mt-2 flex items-center justify-between px-1 text-sm">
          <span data-testid="card-tile-label" className="truncate font-medium">
            {card.variant === "CREDIT" ? "Credit card" : "Debit card"}
            {accountName ? ` · ${accountName}` : ""}
          </span>
          <span
            data-testid="card-tile-status"
            className="shrink-0 text-muted-foreground"
          >
            {STATUS_LABELS[card.status]}
          </span>
        </p>
      </Link>
    </li>
  );
}

function ApplyTile() {
  return (
    <li>
      <Link
        to="/cards/apply"
        data-testid="apply-credit-card-tile"
        className="flex aspect-[1.586/1] w-full max-w-sm flex-col items-center justify-center gap-2 rounded-2xl border border-dashed border-border bg-card text-sm font-medium text-muted-foreground shadow-xs outline-none hover:border-ring/40 hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring/50 motion-safe:transition-colors"
      >
        <Plus aria-hidden="true" className="size-6" />
        Apply for a credit card
      </Link>
    </li>
  );
}

function NoCards() {
  return (
    <div
      data-testid="cards-empty"
      className="mt-6 flex flex-col items-center gap-3 rounded-xl border border-border bg-card p-8 text-center shadow-xs"
    >
      <CreditCard aria-hidden="true" className="size-8 text-muted-foreground" />
      <p className="font-medium">No cards yet</p>
      <p className="text-sm text-muted-foreground">
        A debit card arrives automatically with your current account.
      </p>
    </div>
  );
}

function CardsError() {
  return (
    <StatusPanel
      testId="cards-error"
      role="alert"
      className="mt-6"
      title="We couldn’t load your cards"
      body="Please try again in a moment."
    />
  );
}

function CardsSkeleton() {
  return (
    <div
      data-testid="cards-skeleton"
      aria-busy="true"
      className="mt-6 grid grid-cols-1 gap-6 sm:grid-cols-2"
    >
      <span className="sr-only">Loading cards</span>
      <div className="aspect-[1.586/1] w-full max-w-sm rounded-2xl bg-muted/40 motion-safe:animate-pulse" />
      <div className="aspect-[1.586/1] w-full max-w-sm rounded-2xl bg-muted/40 motion-safe:animate-pulse" />
    </div>
  );
}
