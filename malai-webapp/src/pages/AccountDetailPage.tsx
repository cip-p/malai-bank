import { Link, useParams } from "react-router-dom";
import { ChartPie } from "lucide-react";
import { useAccount } from "@/accounts/useAccount";
import { AccountHeroCard } from "@/accounts/AccountHeroCard";
import { ForecastCard } from "@/accounts/ForecastCard";
import { TransactionsList } from "@/accounts/TransactionsList";
import { CreditHeroCard } from "@/cards/CreditHeroCard";
import { SendMoneyCta } from "@/move-money/SendMoneyCta";
import { ScheduledPaymentsPeek } from "@/move-money/ScheduledPaymentsPeek";
import { Button } from "@/components/ui/button";
import { usePageTitle } from "@/lib/usePageTitle";
import { AccountNotFoundPage } from "./AccountNotFoundPage";

export function AccountDetailPage() {
  const { accountUid = "" } = useParams<{ accountUid: string }>();
  const { account, status } = useAccount(accountUid);
  usePageTitle(
    account?.name ?? (status === "not-found" ? "Account" : "Current account"),
  );

  return (
    <div className="mx-auto w-full max-w-3xl p-6">
      {status !== "not-found" ? (
        <Link
          to="/"
          className="text-sm text-muted-foreground hover:underline focus-visible:underline"
        >
          ← Accounts
        </Link>
      ) : null}

      {status === "fetching" ? (
        <HeroSkeleton />
      ) : status === "not-found" ? (
        <AccountNotFoundPage />
      ) : account ? (
        account.accountType === "CREDIT_CARD" ? (
          <>
            <CreditHeroCard accountUid={accountUid} />
            <div className="mt-4 flex flex-wrap gap-2">
              <Button asChild variant="outline" data-testid="view-spending-cta">
                <Link to={`/accounts/${accountUid}/spending`}>
                  <ChartPie aria-hidden="true" />
                  Spending
                </Link>
              </Button>
            </div>
            <TransactionsList accountUid={accountUid} />
          </>
        ) : (
          <>
            <AccountHeroCard accountUid={accountUid} />
            <ForecastCard accountUid={accountUid} />
            <div className="mt-4 flex flex-wrap gap-2">
              <SendMoneyCta accountUid={accountUid} />
              <Button asChild variant="outline" data-testid="view-spending-cta">
                <Link to={`/accounts/${accountUid}/spending`}>
                  <ChartPie aria-hidden="true" />
                  Spending
                </Link>
              </Button>
            </div>
            <TransactionsList accountUid={accountUid} />
            <ScheduledPaymentsPeek accountUid={accountUid} />
          </>
        )
      ) : null}
    </div>
  );
}

function HeroSkeleton() {
  return (
    <div
      data-testid="account-detail-skeleton"
      aria-busy="true"
      className="mt-4 space-y-2"
    >
      <span className="sr-only">Loading account</span>
      <div className="h-8 w-48 rounded-md bg-muted/40 motion-safe:animate-pulse" />
      <div className="h-4 w-32 rounded-md bg-muted/40 motion-safe:animate-pulse" />
    </div>
  );
}
