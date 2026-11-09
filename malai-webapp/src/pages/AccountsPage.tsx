import { Link, useOutletContext } from "react-router-dom";
import { Send, Settings, Users } from "lucide-react";
import type { AccountResponse, UserResponse } from "@/api/generated/types.gen";
import { useActiveAccountHolder } from "@/account-holder/useActiveAccountHolder";
import { useAccounts } from "@/accounts/useAccounts";
import { AccountCard } from "@/accounts/AccountCard";
import { BalanceVisibilityToggle } from "@/accounts/BalanceVisibilityToggle";
import { useBalanceVisibility } from "@/accounts/useBalanceVisibility";
import { firstNameOf } from "@/components/AppShell";
import { StatusPanel } from "@/components/StatusPanel";
import { useFocusOnMount } from "@/lib/useFocusOnMount";
import { usePageTitle } from "@/lib/usePageTitle";
import { cn } from "@/lib/utils";

type AuthenticatedOutletContext = { user: UserResponse };

export function AccountsPage() {
  const { user } = useOutletContext<AuthenticatedOutletContext>();
  const holder = useActiveAccountHolder();
  const { accounts, isPending, isError } = useAccounts();
  const headingRef = useFocusOnMount<HTMLHeadingElement>();
  const hidden = useBalanceVisibility((s) => s.hidden);
  const toggle = useBalanceVisibility((s) => s.toggle);
  usePageTitle("Accounts");

  const holderName =
    holder.status === "ready" ? holder.activeHolder.accountHolderName : null;

  return (
    <div className="mx-auto w-full max-w-5xl p-6">
      <h1
        ref={headingRef}
        tabIndex={-1}
        className="text-3xl font-semibold tracking-tight break-words outline-none"
      >
        Welcome back, {firstNameOf(user)}
      </h1>
      {holderName ? (
        <p
          data-testid="active-holder-name"
          className="mt-1 text-sm text-muted-foreground"
        >
          {holderName}
        </p>
      ) : null}

      <QuickActions />

      <section
        className="mt-6"
        role="region"
        aria-labelledby="accounts-section-heading"
      >
        <div className="flex items-center justify-between gap-3">
          <h2
            id="accounts-section-heading"
            className="text-sm font-medium text-muted-foreground"
          >
            Accounts
          </h2>
          <BalanceVisibilityToggle
            hidden={hidden}
            onToggle={toggle}
            labels={{ show: "Show balances", hide: "Hide balances" }}
            testId="master-balance-visibility-toggle"
          />
        </div>
        <div className="mt-3">
          {isPending ? (
            <AccountsSkeleton />
          ) : isError ? (
            <AccountsError />
          ) : (
            <AccountsList accounts={accounts ?? []} />
          )}
        </div>
      </section>
    </div>
  );
}

const quickActionBase =
  "group flex flex-col items-center justify-start gap-2 rounded-xl border border-border bg-card p-3 text-center text-sm leading-tight font-medium shadow-xs outline-none hover:border-ring/40 hover:shadow-sm focus-visible:ring-2 focus-visible:ring-ring/50 motion-safe:transition-all md:flex-row md:justify-center md:gap-2.5 md:py-3";
const quickActionIconWrap =
  "inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-primary text-primary-foreground md:h-8 md:w-8";

function QuickActions() {
  return (
    <section
      data-testid="quick-actions"
      aria-labelledby="quick-actions-heading"
      className="mt-6"
    >
      <h2 id="quick-actions-heading" className="sr-only">
        Quick actions
      </h2>
      <div className="grid grid-cols-3 gap-3">
        <Link
          to="/move/send"
          data-testid="quick-action-send"
          className={quickActionBase}
        >
          <span className={quickActionIconWrap}>
            <Send aria-hidden="true" className="size-5 md:size-4" />
          </span>
          <span>Send money</span>
        </Link>
        <Link
          to="/move/payees"
          data-testid="quick-action-payees"
          className={quickActionBase}
        >
          <span className={quickActionIconWrap}>
            <Users aria-hidden="true" className="size-5 md:size-4" />
          </span>
          <span>Manage payees</span>
        </Link>
        <Link
          to="/settings"
          data-testid="quick-action-settings-desktop"
          className={cn(quickActionBase, "hidden md:inline-flex")}
        >
          <span className={quickActionIconWrap}>
            <Settings aria-hidden="true" className="size-5 md:size-4" />
          </span>
          <span>Settings</span>
        </Link>
        <Link
          to="/profile"
          data-testid="quick-action-settings-mobile"
          className={cn(quickActionBase, "md:hidden")}
        >
          <span className={quickActionIconWrap}>
            <Settings aria-hidden="true" className="size-5 md:size-4" />
          </span>
          <span>Settings</span>
        </Link>
      </div>
    </section>
  );
}

function AccountsList({ accounts }: { accounts: AccountResponse[] }) {
  if (accounts.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">No accounts to show.</p>
    );
  }
  return (
    <ul
      data-testid="accounts-list"
      className="grid grid-cols-1 gap-3 md:grid-cols-2"
    >
      {accounts.map((account) => (
        <li key={account.accountUid} data-testid="account-row">
          <AccountCard account={account} />
        </li>
      ))}
    </ul>
  );
}

function AccountsError() {
  return (
    <StatusPanel
      testId="accounts-error"
      role="alert"
      title="We couldn’t load your accounts"
      body="Please try again in a moment."
    />
  );
}

function AccountsSkeleton() {
  return (
    <div data-testid="accounts-skeleton" aria-busy="true" className="space-y-2">
      <span className="sr-only">Loading accounts</span>
      <div className="h-20 rounded-lg border border-border bg-muted/40 motion-safe:animate-pulse" />
      <div className="h-20 rounded-lg border border-border bg-muted/40 motion-safe:animate-pulse" />
      <div className="h-20 rounded-lg border border-border bg-muted/40 motion-safe:animate-pulse" />
    </div>
  );
}
