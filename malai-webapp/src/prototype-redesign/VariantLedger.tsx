import { useState } from "react";
import {
  ArrowUpRight,
  ChevronRight,
  LogOut,
  Search,
  Settings,
  Users,
} from "lucide-react";
import type { AccountResponse, UserResponse } from "@/api/generated/types.gen";
import { useAccounts } from "@/accounts/useAccounts";
import { useBalance } from "@/accounts/useBalance";
import { useIdentifiers } from "@/accounts/useIdentifiers";
import { useTransactions } from "@/accounts/useTransactions";
import { Money } from "@/accounts/Money";
import { usePayees } from "@/move-money/usePayees";
import { CategoryIcon } from "@/lib/CategoryIcon";
import { HeaderWordmark } from "@/theming/HeaderWordmark";
import { cn } from "@/lib/utils";
import { initialsOf, usePrototypeNav } from "./prototypeNav";

type Props = {
  user: UserResponse;
};

export function VariantLedger({ user }: Props) {
  const { screen } = usePrototypeNav();
  if (screen === "login") return <LedgerLogin user={user} />;
  return <LedgerWorkspace user={user} screen={screen} />;
}

function LedgerLogin({ user }: { user: UserResponse }) {
  const { navigate } = usePrototypeNav();
  return (
    <div className="flex min-h-full flex-col items-center justify-center bg-background p-6">
      <div className="w-full max-w-sm">
        <HeaderWordmark className="h-7 w-auto text-primary" />
        <h1 className="mt-10 text-2xl font-semibold tracking-tight">Sign in</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          We&apos;ll email you a one-time code. No passwords here.
        </p>
        <form
          className="mt-8 space-y-3"
          onSubmit={(e) => {
            e.preventDefault();
            navigate("home");
          }}
        >
          <input
            type="email"
            defaultValue={user.email}
            aria-label="Email"
            className="h-11 w-full rounded-lg border border-input bg-card px-3 text-sm outline-none focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/40"
          />
          <button
            type="submit"
            className="h-11 w-full rounded-lg bg-primary text-sm font-medium text-primary-foreground outline-none hover:bg-primary/90 focus-visible:ring-2 focus-visible:ring-ring/50"
          >
            Continue with email
          </button>
        </form>
        <p className="mt-6 text-center text-xs text-muted-foreground">
          New here?{" "}
          <button
            type="button"
            onClick={() => navigate("home")}
            className="font-medium text-primary underline-offset-4 hover:underline"
          >
            Open an account in minutes
          </button>
        </p>
      </div>
    </div>
  );
}

function LedgerWorkspace({
  user,
  screen,
}: {
  user: UserResponse;
  screen: "home" | "pay";
}) {
  const { accounts } = useAccounts();
  const { accountUid, navigate } = usePrototypeNav();
  const selected =
    accounts?.find((a) => a.accountUid === accountUid) ?? accounts?.[0] ?? null;

  return (
    <div className="flex min-h-full bg-background">
      <aside className="sticky top-0 flex h-screen w-72 shrink-0 flex-col border-r border-border bg-card">
        <div className="flex h-14 items-center border-b border-border px-5">
          <HeaderWordmark className="h-5 w-auto text-primary" />
        </div>
        <nav className="space-y-0.5 p-3">
          <SidebarLink
            active={screen === "home"}
            label="Overview"
            onClick={() => navigate("home")}
            icon={<ChevronRight aria-hidden="true" className="size-4" />}
          />
          <SidebarLink
            active={screen === "pay"}
            label="Pay someone"
            onClick={() => navigate("pay")}
            icon={<ArrowUpRight aria-hidden="true" className="size-4" />}
          />
          <SidebarLink
            active={false}
            label="Payees"
            onClick={() => navigate("pay")}
            icon={<Users aria-hidden="true" className="size-4" />}
          />
        </nav>
        <div className="mt-2 flex-1 overflow-y-auto px-3">
          <p className="px-2 pb-1 text-xs font-medium uppercase tracking-wide text-muted-foreground">
            Accounts
          </p>
          <ul className="space-y-0.5">
            {(accounts ?? []).map((account) => (
              <li key={account.accountUid}>
                <SidebarAccount
                  account={account}
                  active={selected?.accountUid === account.accountUid}
                  onSelect={() => navigate("home", account.accountUid)}
                />
              </li>
            ))}
          </ul>
        </div>
        <div className="flex items-center gap-2.5 border-t border-border p-4">
          <span className="flex size-8 items-center justify-center rounded-full bg-accent text-xs font-semibold text-accent-foreground">
            {initialsOf(`${user.firstName ?? ""} ${user.lastName}`)}
          </span>
          <span className="min-w-0 flex-1">
            <span className="block truncate text-sm font-medium">
              {user.firstName} {user.lastName}
            </span>
            <span className="block truncate text-xs text-muted-foreground">
              {user.email}
            </span>
          </span>
          <Settings
            aria-hidden="true"
            className="size-4 text-muted-foreground"
          />
          <LogOut aria-hidden="true" className="size-4 text-muted-foreground" />
        </div>
      </aside>
      <main className="min-w-0 flex-1">
        {screen === "pay" ? (
          <LedgerPay />
        ) : selected ? (
          <LedgerAccountPane account={selected} />
        ) : null}
      </main>
    </div>
  );
}

function SidebarLink({
  active,
  label,
  icon,
  onClick,
}: {
  active: boolean;
  label: string;
  icon: React.ReactNode;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2 text-sm font-medium text-muted-foreground outline-none hover:bg-muted hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring/50",
        active && "bg-accent text-accent-foreground",
      )}
    >
      {icon}
      {label}
    </button>
  );
}

function SidebarAccount({
  account,
  active,
  onSelect,
}: {
  account: AccountResponse;
  active: boolean;
  onSelect: () => void;
}) {
  const { balance } = useBalance(account.accountUid);
  return (
    <button
      type="button"
      onClick={onSelect}
      className={cn(
        "flex w-full items-center justify-between gap-2 rounded-lg px-2.5 py-2 text-sm outline-none hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring/50",
        active && "bg-accent",
      )}
    >
      <span className="truncate font-medium">{account.name}</span>
      <span className="shrink-0 text-xs text-muted-foreground tabular-nums">
        {balance ? <Money minorUnits={balance.amountMinorUnits} /> : "—"}
      </span>
    </button>
  );
}

function LedgerAccountPane({ account }: { account: AccountResponse }) {
  const { balance } = useBalance(account.accountUid);
  const { identifiers } = useIdentifiers(account.accountUid);
  const { transactions } = useTransactions(account.accountUid);
  const { navigate } = usePrototypeNav();
  const [query, setQuery] = useState("");

  const sortCode =
    identifiers?.bankIdentifier ??
    identifiers?.accountIdentifiers[0]?.bankIdentifier ??
    "";
  const accountNumber =
    identifiers?.accountIdentifier ??
    identifiers?.accountIdentifiers[0]?.accountIdentifier ??
    "";

  const filtered = (transactions ?? []).filter((tx) =>
    (tx.merchantName ?? tx.description ?? "")
      .toLowerCase()
      .includes(query.toLowerCase()),
  );

  return (
    <div>
      <div className="sticky top-0 z-10 border-b border-border bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/80">
        <div className="flex flex-wrap items-end justify-between gap-4 px-8 pt-6 pb-4">
          <div>
            <h1 className="text-xl font-semibold tracking-tight">
              {account.name}
            </h1>
            <p className="mt-0.5 text-xs text-muted-foreground tabular-nums">
              {sortCode} · {accountNumber} · {account.currency}
            </p>
          </div>
          <div className="text-right">
            <p className="text-xs text-muted-foreground">Available</p>
            <p className="text-2xl font-semibold tracking-tight tabular-nums">
              {balance ? <Money minorUnits={balance.amountMinorUnits} /> : "—"}
            </p>
          </div>
        </div>
        <div className="flex items-center gap-3 px-8 pb-3">
          <label className="flex h-8 flex-1 items-center gap-2 rounded-lg border border-input bg-card px-2.5">
            <Search
              aria-hidden="true"
              className="size-3.5 text-muted-foreground"
            />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search transactions"
              className="w-full bg-transparent text-sm outline-none placeholder:text-muted-foreground"
            />
          </label>
          <button
            type="button"
            onClick={() => navigate("pay")}
            className="h-8 rounded-lg bg-primary px-3 text-sm font-medium text-primary-foreground outline-none hover:bg-primary/90 focus-visible:ring-2 focus-visible:ring-ring/50"
          >
            Pay someone
          </button>
        </div>
      </div>
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-border text-left text-xs uppercase tracking-wide text-muted-foreground">
            <th className="py-2 pl-8 pr-3 font-medium">Date</th>
            <th className="px-3 py-2 font-medium">Description</th>
            <th className="px-3 py-2 text-right font-medium">Amount</th>
            <th className="py-2 pr-8 pl-3 text-right font-medium">Balance</th>
          </tr>
        </thead>
        <tbody>
          {filtered.map((tx) => (
            <tr
              key={tx.entryId}
              className="border-b border-border/60 hover:bg-muted/40"
            >
              <td className="py-2.5 pl-8 pr-3 whitespace-nowrap text-muted-foreground tabular-nums">
                {new Date(tx.postedAt).toLocaleDateString("en-GB", {
                  day: "2-digit",
                  month: "short",
                })}
              </td>
              <td className="px-3 py-2.5">
                <span className="flex items-center gap-2.5">
                  <span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-muted text-muted-foreground">
                    <CategoryIcon category={tx.category} className="size-3.5" />
                  </span>
                  <span className="min-w-0">
                    <span className="block truncate font-medium">
                      {tx.merchantName ?? tx.description ?? "Transaction"}
                    </span>
                  </span>
                </span>
              </td>
              <td className="px-3 py-2.5 text-right tabular-nums">
                <Money minorUnits={tx.amountMinorUnits} signed="always" />
              </td>
              <td className="py-2.5 pr-8 pl-3 text-right text-muted-foreground tabular-nums">
                <Money minorUnits={tx.runningBalanceMinorUnits} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function LedgerPay() {
  const { payees } = usePayees();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const selected = (payees ?? []).find((p) => p.payeeId === selectedId) ?? null;

  return (
    <div className="grid min-h-full grid-cols-1 lg:grid-cols-[2fr_3fr]">
      <div className="border-r border-border">
        <div className="border-b border-border px-8 pt-6 pb-4">
          <h1 className="text-xl font-semibold tracking-tight">Pay someone</h1>
          <p className="mt-0.5 text-xs text-muted-foreground">
            Choose a saved payee or add a new one
          </p>
        </div>
        <ul className="p-4">
          {(payees ?? []).map((payee) => (
            <li key={payee.payeeId}>
              <button
                type="button"
                onClick={() => setSelectedId(payee.payeeId)}
                className={cn(
                  "flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left outline-none hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring/50",
                  selectedId === payee.payeeId && "bg-accent",
                )}
              >
                <span className="flex size-9 items-center justify-center rounded-full bg-secondary text-xs font-semibold text-secondary-foreground">
                  {initialsOf(payee.name)}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-medium">
                    {payee.name}
                  </span>
                  <span className="block text-xs text-muted-foreground tabular-nums">
                    {payee.sortCode} · {payee.accountNumber}
                  </span>
                </span>
              </button>
            </li>
          ))}
        </ul>
      </div>
      <div className="flex items-start justify-center p-8">
        <div className="w-full max-w-sm rounded-xl border border-border bg-card p-6 shadow-xs">
          <h2 className="text-sm font-medium text-muted-foreground">
            {selected ? `Paying ${selected.name}` : "Select a payee to start"}
          </h2>
          <p className="mt-4 text-4xl font-semibold tracking-tight tabular-nums">
            £0.00
          </p>
          <input
            placeholder="Reference (optional)"
            className="mt-6 h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/40"
          />
          <button
            type="button"
            disabled
            className="mt-4 h-10 w-full rounded-lg bg-muted text-sm font-medium text-muted-foreground"
          >
            Review payment
          </button>
          <p className="mt-3 text-center text-xs text-muted-foreground italic">
            Prototype — payments are read-only here
          </p>
        </div>
      </div>
    </div>
  );
}
