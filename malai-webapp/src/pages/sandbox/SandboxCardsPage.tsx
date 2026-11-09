import { useId, useState } from "react";
import { CheckCircle2, XCircle } from "lucide-react";
import { useAccounts } from "@/accounts/useAccounts";
import { Money } from "@/accounts/Money";
import { formatSchemeDeclineCode } from "@/cards/formatDeclineReason";
import { lastFourOf } from "@/cards/formatPan";
import { useCards } from "@/cards/useCards";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { nativeSelectClassName } from "@/components/ui/native-select";
import { AmountInput } from "@/move-money/AmountInput";
import { parseAmount } from "@/move-money/parseAmount";
import { StatusPanel } from "@/components/StatusPanel";
import { useFocusOnMount } from "@/lib/useFocusOnMount";
import { usePageTitle } from "@/lib/usePageTitle";
import { useCardMerchants } from "@/sandbox/useCardMerchants";
import { useCardPresentmentMutation } from "@/sandbox/useCardPresentmentMutation";
import { useCardPurchaseMutation } from "@/sandbox/useCardPurchaseMutation";
import { useCardRefundMutation } from "@/sandbox/useCardRefundMutation";
import { useRecentCardPurchasesStore } from "@/sandbox/useRecentCardPurchasesStore";

export function SandboxCardsPage() {
  usePageTitle("Card purchases");
  const headingRef = useFocusOnMount<HTMLHeadingElement>();

  return (
    <section data-testid="sandbox-cards">
      <h1
        ref={headingRef}
        tabIndex={-1}
        className="text-2xl font-semibold tracking-tight outline-none"
      >
        Card purchases
      </h1>
      <p className="mt-3 text-sm text-muted-foreground">
        Drive the demo card scheme: authorise a purchase at a fictional
        merchant, settle pending captures, or send a merchant refund.
      </p>

      <div className="mt-6 space-y-10">
        <PurchaseSection />
        <PresentmentSection />
        <RefundSection />
      </div>
    </section>
  );
}

function PurchaseSection() {
  const { cards, isPending: cardsLoading, isError: cardsFailed } = useCards();
  const { accounts } = useAccounts();
  const { merchants, isPending: merchantsLoading } = useCardMerchants();
  const { submit, isPending, outcome } = useCardPurchaseMutation();

  const cardList = (cards ?? []).filter((c) => c.status !== "CANCELLED");
  const merchantList = merchants ?? [];

  const [cardOverride, setCardOverride] = useState<string | null>(null);
  const cardId =
    cardOverride ?? (cardList.length > 0 ? cardList[0]!.cardId : "");
  const card = cardList.find((c) => c.cardId === cardId);

  const [merchantOverride, setMerchantOverride] = useState<string | null>(null);
  const merchantId =
    merchantOverride ??
    (merchantList.length > 0 ? merchantList[0]!.merchantId : "");
  const merchant = merchantList.find((m) => m.merchantId === merchantId);

  const [amount, setAmount] = useState("");
  const parsed = parseAmount(amount);

  const canSubmit =
    !isPending &&
    parsed.ok &&
    card !== undefined &&
    merchant !== undefined &&
    !cardsLoading &&
    !merchantsLoading;

  const cardSelectId = useId();
  const merchantSelectId = useId();
  const amountId = useId();

  function accountNameOf(accountId: string): string {
    return accounts?.find((a) => a.accountUid === accountId)?.name ?? "Account";
  }

  function handleSubmit() {
    if (!canSubmit || !parsed.ok || !card || !merchant) return;
    submit({
      cardId: card.cardId,
      merchantId: merchant.merchantId,
      merchantName: merchant.name,
      amountMinorUnits: parsed.minorUnits,
      accountUid: card.accountId,
    });
  }

  return (
    <section aria-label="Trigger a card purchase" className="space-y-6">
      <h2 className="text-lg font-semibold">Trigger a purchase</h2>

      {cardsFailed ? (
        <StatusPanel
          testId="sandbox-cards-error"
          role="alert"
          title="We couldn’t load your cards"
          body="Please try again in a moment."
        />
      ) : cardList.length === 0 && !cardsLoading ? (
        <p
          data-testid="sandbox-cards-none"
          className="text-sm text-muted-foreground"
        >
          No active cards to spend on.
        </p>
      ) : (
        <>
          <div className="space-y-1.5">
            <Label htmlFor={cardSelectId}>Card</Label>
            <select
              id={cardSelectId}
              data-testid="card-purchase-card"
              value={cardId}
              onChange={(e) => setCardOverride(e.target.value)}
              className={nativeSelectClassName}
            >
              {cardList.map((c) => (
                <option key={c.cardId} value={c.cardId}>
                  {c.variant === "CREDIT" ? "Credit" : "Debit"} ····
                  {lastFourOf(c.maskedPan)} · {accountNameOf(c.accountId)}
                  {c.status === "FROZEN" ? " (frozen)" : ""}
                </option>
              ))}
            </select>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor={merchantSelectId}>Merchant</Label>
            <select
              id={merchantSelectId}
              data-testid="card-purchase-merchant"
              value={merchantId}
              onChange={(e) => setMerchantOverride(e.target.value)}
              className={nativeSelectClassName}
            >
              {merchantList.map((m) => (
                <option key={m.merchantId} value={m.merchantId}>
                  {m.name}
                </option>
              ))}
            </select>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor={amountId}>Amount</Label>
            <AmountInput
              id={amountId}
              data-testid="card-purchase-amount"
              value={amount}
              onChange={setAmount}
            />
          </div>

          <Button
            type="button"
            data-testid="card-purchase-submit"
            onClick={handleSubmit}
            disabled={!canSubmit}
          >
            {isPending ? "Authorising…" : "Make purchase"}
          </Button>

          {outcome ? (
            <div
              data-testid="card-purchase-outcome"
              data-outcome={outcome.outcome}
              className="flex items-center gap-2 rounded-xl border border-border bg-card p-4 text-sm shadow-xs"
            >
              {outcome.outcome === "APPROVED" ? (
                <>
                  <CheckCircle2
                    aria-hidden="true"
                    className="size-5 text-positive"
                  />
                  <span className="font-medium">Approved</span>
                  <span className="text-muted-foreground">
                    — the purchase is pending in the account feed.
                  </span>
                </>
              ) : (
                <>
                  <XCircle
                    aria-hidden="true"
                    className="size-5 text-destructive"
                  />
                  <span className="font-medium">Declined</span>
                  <span
                    data-testid="card-purchase-decline-reason"
                    className="text-muted-foreground"
                  >
                    — {formatSchemeDeclineCode(outcome.declineReasonCode)}
                  </span>
                </>
              )}
            </div>
          ) : null}
        </>
      )}
    </section>
  );
}

function PresentmentSection() {
  const { presentNow, isPending } = useCardPresentmentMutation();

  return (
    <section aria-label="Present pending captures" className="space-y-3">
      <h2 className="text-lg font-semibold">Present now</h2>
      <p className="text-sm text-muted-foreground">
        Settle every pending card purchase immediately instead of waiting for
        the scheme’s presentment delay.
      </p>
      <Button
        type="button"
        variant="outline"
        data-testid="card-present-now"
        onClick={presentNow}
        disabled={isPending}
      >
        {isPending ? "Presenting…" : "Present pending captures"}
      </Button>
    </section>
  );
}

function RefundSection() {
  const purchases = useRecentCardPurchasesStore((s) => s.purchases);
  const { refund, isPending } = useCardRefundMutation();
  const [manualId, setManualId] = useState("");
  const manualInputId = useId();

  return (
    <section aria-label="Trigger a merchant refund" className="space-y-3">
      <h2 className="text-lg font-semibold">Refund a purchase</h2>
      <p className="text-sm text-muted-foreground">
        Refunds need a settled purchase — trigger one above, present it, then
        refund it here.
      </p>

      {purchases.length > 0 ? (
        <ul data-testid="card-refund-list" className="space-y-2">
          {purchases.map((purchase) => (
            <li
              key={purchase.authorisationId}
              data-testid="card-refund-row"
              className="flex items-center justify-between gap-3 rounded-xl border border-border bg-card p-4 text-sm shadow-xs"
            >
              <span className="min-w-0">
                <span className="block truncate font-medium">
                  {purchase.merchantName ?? "Card purchase"}
                </span>
                <span className="block text-muted-foreground">
                  <Money
                    minorUnits={purchase.amountMinorUnits}
                    currency={purchase.currency}
                    signed="never"
                  />
                </span>
              </span>
              <Button
                type="button"
                variant="outline"
                size="sm"
                data-testid="card-refund-submit"
                disabled={isPending}
                onClick={() =>
                  refund({
                    authorisationId: purchase.authorisationId,
                    accountUid: purchase.accountUid,
                  })
                }
              >
                Refund
              </Button>
            </li>
          ))}
        </ul>
      ) : (
        <p
          data-testid="card-refund-empty"
          className="text-sm text-muted-foreground"
        >
          No purchases triggered this session yet.
        </p>
      )}

      <div className="space-y-1.5 pt-2">
        <Label htmlFor={manualInputId}>Or refund by authorisation id</Label>
        <div className="flex gap-2">
          <Input
            id={manualInputId}
            type="text"
            autoComplete="off"
            data-testid="card-refund-manual-id"
            placeholder="00000000-0000-0000-0000-000000000000"
            value={manualId}
            onChange={(e) => setManualId(e.target.value)}
          />
          <Button
            type="button"
            variant="outline"
            data-testid="card-refund-manual-submit"
            disabled={isPending || manualId.trim().length === 0}
            onClick={() => refund({ authorisationId: manualId.trim() })}
          >
            Refund
          </Button>
        </div>
      </div>
    </section>
  );
}
