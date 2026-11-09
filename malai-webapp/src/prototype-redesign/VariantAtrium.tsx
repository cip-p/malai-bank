import { useState } from "react";
import type { AccountResponse, UserResponse } from "@/api/generated/types.gen";
import { useAccounts } from "@/accounts/useAccounts";
import { useBalance } from "@/accounts/useBalance";
import { useTransactions } from "@/accounts/useTransactions";
import { Money } from "@/accounts/Money";
import { usePayees } from "@/move-money/usePayees";
import { formatCurrency } from "@/lib/formatCurrency";
import { HeaderWordmark } from "@/theming/HeaderWordmark";
import { cn } from "@/lib/utils";
import { greetingFor, usePrototypeNav } from "./prototypeNav";

type Props = {
  user: UserResponse;
};

export function VariantAtrium({ user }: Props) {
  const { screen } = usePrototypeNav();
  if (screen === "login") return <AtriumLogin user={user} />;
  return <AtriumShell user={user} screen={screen} />;
}

function AtriumLogin({ user }: { user: UserResponse }) {
  const { navigate } = usePrototypeNav();
  return (
    <div className="grid min-h-full lg:grid-cols-[2fr_3fr]">
      <div className="flex flex-col justify-between bg-primary p-10 text-primary-foreground">
        <HeaderWordmark className="h-6 w-auto" />
        <div>
          <p className="max-w-md text-3xl font-semibold leading-tight tracking-tight text-balance">
            Banking, without the noise.
          </p>
          <p className="mt-4 max-w-sm text-sm text-primary-foreground/75">
            One code to your inbox. No passwords, no paperwork, no queues.
          </p>
        </div>
        <p className="text-xs text-primary-foreground/60">
          Authorised and regulated. Eligible deposits protected up to £85,000.
        </p>
      </div>
      <div className="flex items-center justify-center bg-background p-10">
        <form
          className="w-full max-w-xs"
          onSubmit={(e) => {
            e.preventDefault();
            navigate("home");
          }}
        >
          <h1 className="text-xl font-semibold tracking-tight">Sign in</h1>
          <label className="mt-8 block text-xs font-medium uppercase tracking-wide text-muted-foreground">
            Email
            <input
              type="email"
              defaultValue={user.email}
              className="mt-2 h-11 w-full border-0 border-b border-input bg-transparent text-base font-normal tracking-normal normal-case outline-none focus-visible:border-ring"
            />
          </label>
          <button
            type="submit"
            className="mt-10 h-11 w-full rounded-full bg-primary text-sm font-medium text-primary-foreground outline-none hover:bg-primary/90 focus-visible:ring-2 focus-visible:ring-ring/50"
          >
            Continue
          </button>
          <p className="mt-6 text-xs text-muted-foreground">
            First time?{" "}
            <button
              type="button"
              onClick={() => navigate("home")}
              className="font-medium text-primary underline-offset-4 hover:underline"
            >
              Become a member
            </button>
          </p>
        </form>
      </div>
    </div>
  );
}

function AtriumShell({
  user,
  screen,
}: {
  user: UserResponse;
  screen: "home" | "pay";
}) {
  const { accounts } = useAccounts();
  const { accountUid, navigate } = usePrototypeNav();
  const list = accounts ?? [];
  const selected =
    list.find((a) => a.accountUid === accountUid) ?? list[0] ?? null;

  return (
    <div className="grid min-h-full lg:grid-cols-[2fr_3fr]">
      <aside className="flex flex-col justify-between bg-primary p-10 text-primary-foreground lg:sticky lg:top-0 lg:h-screen">
        <HeaderWordmark className="h-6 w-auto" />
        <div>
          <p className="text-sm text-primary-foreground/70">
            {greetingFor(new Date().getHours())},
          </p>
          <p className="mt-1 text-4xl font-semibold tracking-tight">
            {user.firstName ?? user.lastName}
          </p>
          <ul className="mt-10 space-y-1">
            {list.map((account) => (
              <li key={account.accountUid}>
                <AtriumAccountRow
                  account={account}
                  active={selected?.accountUid === account.accountUid}
                  onSelect={() => navigate("home", account.accountUid)}
                />
              </li>
            ))}
          </ul>
        </div>
        <div className="flex gap-5 text-xs text-primary-foreground/60">
          <span>Settings</span>
          <span>Help</span>
          <span>Sign out</span>
        </div>
      </aside>
      <main className="bg-background">
        <nav className="flex gap-8 border-b border-border px-10 pt-8 text-xs font-medium uppercase tracking-widest">
          <AtriumTab
            label="Overview"
            active={screen === "home"}
            onClick={() => navigate("home")}
          />
          <AtriumTab
            label="Move money"
            active={screen === "pay"}
            onClick={() => navigate("pay")}
          />
          <AtriumTab label="Profile" active={false} onClick={() => {}} />
        </nav>
        {screen === "pay" ? (
          <AtriumPay accounts={list} />
        ) : selected ? (
          <AtriumStatement account={selected} />
        ) : null}
      </main>
    </div>
  );
}

function AtriumTab({
  label,
  active,
  onClick,
}: {
  label: string;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "border-b-2 border-transparent pb-3 text-muted-foreground outline-none hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring/50",
        active && "border-primary text-foreground",
      )}
    >
      {label}
    </button>
  );
}

function AtriumAccountRow({
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
        "flex w-full items-baseline justify-between gap-6 border-b border-primary-foreground/15 py-3 text-left outline-none focus-visible:ring-2 focus-visible:ring-primary-foreground/40",
        active ? "opacity-100" : "opacity-75 hover:opacity-100",
      )}
    >
      <span className="text-sm font-medium">{account.name}</span>
      <span className="text-lg font-semibold tracking-tight tabular-nums">
        {balance ? (
          formatCurrency(balance.amountMinorUnits, balance.currency)
        ) : (
          <span aria-hidden="true">—</span>
        )}
      </span>
    </button>
  );
}

function AtriumStatement({ account }: { account: AccountResponse }) {
  const { transactions } = useTransactions(account.accountUid);

  return (
    <div className="px-10 py-10">
      <h1 className="text-xs font-medium uppercase tracking-widest text-muted-foreground">
        {account.name} — recent activity
      </h1>
      <ul className="mt-6">
        {(transactions ?? []).map((tx) => (
          <li
            key={tx.entryId}
            className="group flex items-baseline gap-6 border-b border-border/70 py-4"
          >
            <span className="w-20 shrink-0 text-xs text-muted-foreground tabular-nums">
              {new Date(tx.postedAt).toLocaleDateString("en-GB", {
                day: "numeric",
                month: "short",
              })}
            </span>
            <span className="min-w-0 flex-1">
              <span className="block truncate text-base">
                {tx.merchantName ?? tx.description ?? "Transaction"}
              </span>
              {tx.category ? (
                <span className="mt-0.5 block text-xs text-muted-foreground capitalize">
                  {tx.category.toLowerCase().replaceAll("_", " ")}
                </span>
              ) : null}
            </span>
            <span className="shrink-0 text-base font-medium tabular-nums">
              <Money minorUnits={tx.amountMinorUnits} signed="always" />
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function AtriumPay({ accounts }: { accounts: AccountResponse[] }) {
  const { payees } = usePayees();
  const [payeeId, setPayeeId] = useState("");
  const [from, setFrom] = useState("");

  return (
    <div className="max-w-xl px-10 py-10">
      <h1 className="text-xs font-medium uppercase tracking-widest text-muted-foreground">
        Move money
      </h1>
      <label className="mt-8 block text-xs font-medium uppercase tracking-wide text-muted-foreground">
        From
        <select
          value={from}
          onChange={(e) => setFrom(e.target.value)}
          className="mt-2 h-11 w-full border-0 border-b border-input bg-transparent text-base font-normal tracking-normal normal-case text-foreground outline-none focus-visible:border-ring"
        >
          <option value="">Choose an account</option>
          {accounts.map((account) => (
            <option key={account.accountUid} value={account.accountUid}>
              {account.name}
            </option>
          ))}
        </select>
      </label>
      <label className="mt-6 block text-xs font-medium uppercase tracking-wide text-muted-foreground">
        To
        <select
          value={payeeId}
          onChange={(e) => setPayeeId(e.target.value)}
          className="mt-2 h-11 w-full border-0 border-b border-input bg-transparent text-base font-normal tracking-normal normal-case text-foreground outline-none focus-visible:border-ring"
        >
          <option value="">Choose a payee</option>
          {(payees ?? []).map((payee) => (
            <option key={payee.payeeId} value={payee.payeeId}>
              {payee.name} · {payee.sortCode} {payee.accountNumber}
            </option>
          ))}
        </select>
      </label>
      <label className="mt-6 block text-xs font-medium uppercase tracking-wide text-muted-foreground">
        Amount
        <input
          inputMode="decimal"
          placeholder="£0.00"
          className="mt-2 h-14 w-full border-0 border-b border-input bg-transparent text-3xl font-semibold tracking-tight normal-case outline-none placeholder:text-muted-foreground/80 focus-visible:border-ring"
        />
      </label>
      <button
        type="button"
        disabled
        className="mt-10 h-11 rounded-full bg-muted px-8 text-sm font-medium text-muted-foreground"
      >
        Review transfer
      </button>
      <p className="mt-3 text-xs text-muted-foreground italic">
        Prototype — payments are read-only here
      </p>
    </div>
  );
}
