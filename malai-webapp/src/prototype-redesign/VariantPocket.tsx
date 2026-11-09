import { useState } from "react";
import { ArrowDownLeft, ArrowUpRight, House, UserRound } from "lucide-react";
import type { AccountResponse, UserResponse } from "@/api/generated/types.gen";
import { useAccounts } from "@/accounts/useAccounts";
import { useBalance } from "@/accounts/useBalance";
import { useIdentifiers } from "@/accounts/useIdentifiers";
import { useTransactions } from "@/accounts/useTransactions";
import { Money } from "@/accounts/Money";
import { usePayees } from "@/move-money/usePayees";
import { CategoryIcon } from "@/lib/CategoryIcon";
import { groupTransactionsByDate } from "@/lib/groupTransactionsByDate";
import { HeaderWordmark } from "@/theming/HeaderWordmark";
import { cn } from "@/lib/utils";
import { initialsOf, usePrototypeNav } from "./prototypeNav";

type Props = {
  user: UserResponse;
};

export function VariantPocket({ user }: Props) {
  const { screen } = usePrototypeNav();
  if (screen === "login") return <PocketLogin user={user} />;
  if (screen === "pay") return <PocketPay />;
  return <PocketHome user={user} />;
}

function PocketLogin({ user }: { user: UserResponse }) {
  const { navigate } = usePrototypeNav();
  return (
    <div className="flex min-h-full flex-col bg-primary">
      <div className="flex flex-1 flex-col items-center justify-center px-8 text-primary-foreground">
        <HeaderWordmark className="h-10 w-auto" />
        <p className="mt-4 max-w-60 text-center text-sm text-primary-foreground/80">
          The current account that feels like it was made for you.
        </p>
      </div>
      <div className="rounded-t-3xl bg-background p-6 pb-10 shadow-lg">
        <h1 className="text-lg font-semibold tracking-tight">Welcome back</h1>
        <form
          className="mt-4 space-y-3"
          onSubmit={(e) => {
            e.preventDefault();
            navigate("home");
          }}
        >
          <input
            type="email"
            defaultValue={user.email}
            aria-label="Email"
            className="h-12 w-full rounded-xl border border-input bg-card px-4 text-sm outline-none focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/40"
          />
          <button
            type="submit"
            className="h-12 w-full rounded-xl bg-primary text-sm font-semibold text-primary-foreground outline-none hover:bg-primary/90 focus-visible:ring-2 focus-visible:ring-ring/50"
          >
            Send me a code
          </button>
        </form>
        <p className="mt-4 text-center text-xs text-muted-foreground">
          No password. We&apos;ll email you a 6-digit code.
        </p>
      </div>
    </div>
  );
}

function PocketHome({ user }: { user: UserResponse }) {
  const { accounts } = useAccounts();
  const [activeIndex, setActiveIndex] = useState(0);
  const list = accounts ?? [];
  const active = list[Math.min(activeIndex, Math.max(list.length - 1, 0))];

  return (
    <div className="mx-auto flex min-h-full w-full max-w-md flex-col bg-background">
      <header className="flex items-center justify-between px-5 pt-5">
        <HeaderWordmark className="h-5 w-auto text-primary" />
        <span className="flex size-9 items-center justify-center rounded-full bg-accent text-xs font-semibold text-accent-foreground">
          {initialsOf(`${user.firstName ?? ""} ${user.lastName}`)}
        </span>
      </header>
      <div className="px-5 pt-5">
        <div className="relative">
          {list.map((account, index) => (
            <AccountCardFace
              key={account.accountUid}
              account={account}
              position={index - activeIndex}
              onSelect={() => setActiveIndex(index)}
            />
          ))}
          <div className="invisible" aria-hidden="true">
            {active ? (
              <AccountCardFace account={active} position={0} sizer />
            ) : null}
          </div>
        </div>
        {list.length > 1 ? (
          <div className="mt-3 flex justify-center gap-1.5">
            {list.map((account, index) => (
              <button
                key={account.accountUid}
                type="button"
                aria-label={`Show ${account.name}`}
                onClick={() => setActiveIndex(index)}
                className={cn(
                  "size-1.5 rounded-full bg-border",
                  index === activeIndex && "w-4 bg-primary",
                )}
              />
            ))}
          </div>
        ) : null}
      </div>
      <PocketActions />
      {active ? <PocketFeed accountUid={active.accountUid} /> : null}
      <PocketTabBar current="home" />
    </div>
  );
}

function AccountCardFace({
  account,
  position,
  onSelect,
  sizer = false,
}: {
  account: AccountResponse;
  position: number;
  onSelect?: () => void;
  sizer?: boolean;
}) {
  const { balance } = useBalance(account.accountUid);
  const { identifiers } = useIdentifiers(account.accountUid);
  const sortCode =
    identifiers?.bankIdentifier ??
    identifiers?.accountIdentifiers[0]?.bankIdentifier ??
    "";
  const accountNumber =
    identifiers?.accountIdentifier ??
    identifiers?.accountIdentifiers[0]?.accountIdentifier ??
    "";
  const behind = position !== 0;

  return (
    <button
      type="button"
      onClick={onSelect}
      aria-label={account.name}
      className={cn(
        "absolute inset-x-0 top-0 block w-full rounded-2xl bg-primary p-5 text-left text-primary-foreground shadow-md outline-none focus-visible:ring-2 focus-visible:ring-ring/60 motion-safe:transition-transform",
        behind && "scale-95 -translate-y-3.5 opacity-70",
        position < 0 && "pointer-events-none opacity-0",
        sizer && "static",
      )}
      style={{ zIndex: 10 - Math.abs(position) }}
    >
      <div className="flex items-start justify-between">
        <span className="text-sm font-medium text-primary-foreground/80">
          {account.name}
        </span>
        <span className="rounded-full bg-primary-foreground/15 px-2 py-0.5 text-[0.65rem] font-semibold tracking-wide uppercase">
          {account.currency}
        </span>
      </div>
      <p className="mt-6 text-3xl font-semibold tracking-tight tabular-nums">
        {balance ? <Money minorUnits={balance.amountMinorUnits} /> : "—"}
      </p>
      <p className="mt-4 text-xs text-primary-foreground/70 tabular-nums">
        {sortCode} · {accountNumber}
      </p>
    </button>
  );
}

function PocketActions() {
  const { navigate } = usePrototypeNav();
  return (
    <div className="flex justify-center gap-3 px-5 pt-6">
      <button
        type="button"
        onClick={() => navigate("pay")}
        className="flex items-center gap-1.5 rounded-full bg-secondary px-4 py-2 text-sm font-medium text-secondary-foreground outline-none hover:bg-secondary/80 focus-visible:ring-2 focus-visible:ring-ring/50"
      >
        <ArrowUpRight aria-hidden="true" className="size-4" />
        Pay
      </button>
      <button
        type="button"
        className="flex items-center gap-1.5 rounded-full bg-secondary px-4 py-2 text-sm font-medium text-secondary-foreground outline-none hover:bg-secondary/80 focus-visible:ring-2 focus-visible:ring-ring/50"
      >
        <ArrowDownLeft aria-hidden="true" className="size-4" />
        Request
      </button>
      <button
        type="button"
        className="flex items-center gap-1.5 rounded-full bg-secondary px-4 py-2 text-sm font-medium text-secondary-foreground outline-none hover:bg-secondary/80 focus-visible:ring-2 focus-visible:ring-ring/50"
      >
        Details
      </button>
    </div>
  );
}

function PocketFeed({ accountUid }: { accountUid: string }) {
  const { transactions } = useTransactions(accountUid);
  const groups = groupTransactionsByDate(transactions ?? [], new Date());

  return (
    <div className="flex-1 px-5 pt-7 pb-40">
      {groups.map((group) => (
        <section key={group.label} className="pt-4 first:pt-0">
          <h2 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            {group.label}
          </h2>
          <ul className="mt-1">
            {group.items.map((tx) => (
              <li key={tx.entryId} className="flex items-center gap-3 py-2.5">
                <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-accent text-accent-foreground">
                  <CategoryIcon category={tx.category} className="size-4.5" />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-medium">
                    {tx.merchantName ?? tx.description ?? "Transaction"}
                  </span>
                  {tx.merchantName && tx.description ? (
                    <span className="block truncate text-xs text-muted-foreground">
                      {tx.description}
                    </span>
                  ) : null}
                </span>
                <span className="shrink-0 text-sm font-semibold tabular-nums">
                  <Money minorUnits={tx.amountMinorUnits} signed="always" />
                </span>
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}

function PocketPay() {
  const { payees } = usePayees();
  const { navigate } = usePrototypeNav();
  const [amount, setAmount] = useState("0");
  const [selectedId, setSelectedId] = useState<string | null>(null);

  function press(key: string) {
    setAmount((current) => {
      if (key === "⌫") return current.length > 1 ? current.slice(0, -1) : "0";
      if (key === "." && current.includes(".")) return current;
      if (current === "0" && key !== ".") return key;
      const [, decimals] = current.split(".");
      if (decimals && decimals.length >= 2) return current;
      return current + key;
    });
  }

  return (
    <div className="mx-auto flex min-h-full w-full max-w-md flex-col bg-background">
      <header className="px-5 pt-5">
        <h1 className="text-lg font-semibold tracking-tight">Pay</h1>
      </header>
      <div className="px-5 pt-4">
        <ul className="flex gap-4 overflow-x-auto pb-1">
          {(payees ?? []).map((payee) => (
            <li key={payee.payeeId} className="shrink-0">
              <button
                type="button"
                onClick={() => setSelectedId(payee.payeeId)}
                className="flex w-16 flex-col items-center gap-1.5 outline-none focus-visible:ring-2 focus-visible:ring-ring/50"
              >
                <span
                  className={cn(
                    "flex size-12 items-center justify-center rounded-full bg-secondary text-sm font-semibold text-secondary-foreground",
                    selectedId === payee.payeeId &&
                      "bg-primary text-primary-foreground",
                  )}
                >
                  {initialsOf(payee.name)}
                </span>
                <span className="w-full truncate text-center text-xs">
                  {payee.name.split(" ")[0]}
                </span>
              </button>
            </li>
          ))}
        </ul>
      </div>
      <div className="flex flex-1 flex-col items-center justify-center px-5">
        <p className="text-5xl font-semibold tracking-tight tabular-nums">
          £{amount}
        </p>
        <p className="mt-2 text-xs text-muted-foreground">
          {selectedId
            ? "Prototype — payments are read-only here"
            : "Pick a payee above"}
        </p>
      </div>
      <div className="grid grid-cols-3 gap-2 px-8 pb-6">
        {["1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "⌫"].map(
          (key) => (
            <button
              key={key}
              type="button"
              onClick={() => press(key)}
              className="h-13 rounded-xl text-lg font-medium outline-none hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring/50"
            >
              {key}
            </button>
          ),
        )}
      </div>
      <div className="px-5 pb-32">
        <button
          type="button"
          disabled={!selectedId}
          onClick={() => navigate("home")}
          className="h-12 w-full rounded-xl bg-primary text-sm font-semibold text-primary-foreground outline-none hover:bg-primary/90 focus-visible:ring-2 focus-visible:ring-ring/50 disabled:bg-muted disabled:text-muted-foreground"
        >
          Pay
        </button>
      </div>
      <PocketTabBar current="pay" />
    </div>
  );
}

function PocketTabBar({ current }: { current: "home" | "pay" }) {
  const { navigate } = usePrototypeNav();
  return (
    <nav className="fixed bottom-16 left-1/2 z-40 flex -translate-x-1/2 items-center gap-1 rounded-full border border-border bg-card/95 px-2 py-1.5 shadow-md backdrop-blur">
      <button
        type="button"
        onClick={() => navigate("home")}
        aria-label="Home"
        className={cn(
          "flex items-center gap-1.5 rounded-full px-3.5 py-1.5 text-sm font-medium text-muted-foreground outline-none focus-visible:ring-2 focus-visible:ring-ring/50",
          current === "home" && "bg-accent text-accent-foreground",
        )}
      >
        <House aria-hidden="true" className="size-4" />
        Home
      </button>
      <button
        type="button"
        onClick={() => navigate("pay")}
        aria-label="Pay"
        className={cn(
          "flex items-center gap-1.5 rounded-full px-3.5 py-1.5 text-sm font-medium text-muted-foreground outline-none focus-visible:ring-2 focus-visible:ring-ring/50",
          current === "pay" && "bg-accent text-accent-foreground",
        )}
      >
        <ArrowUpRight aria-hidden="true" className="size-4" />
        Pay
      </button>
      <button
        type="button"
        aria-label="Profile"
        className="flex items-center gap-1.5 rounded-full px-3.5 py-1.5 text-sm font-medium text-muted-foreground outline-none focus-visible:ring-2 focus-visible:ring-ring/50"
      >
        <UserRound aria-hidden="true" className="size-4" />
      </button>
    </nav>
  );
}
