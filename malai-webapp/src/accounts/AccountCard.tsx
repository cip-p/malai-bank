import { Link } from "react-router-dom";
import type {
  AccountResponse,
  AccountIdentifiersResponse,
  CreditSummaryResponse,
  TransactionItemResponse,
} from "@/api/generated/types.gen";
import { useCreditSummary } from "@/cards/useCreditSummary";
import { Money } from "./Money";
import { useBalance } from "./useBalance";
import { useBalanceVisibility } from "./useBalanceVisibility";
import { useIdentifiers } from "./useIdentifiers";
import { useTransactions } from "./useTransactions";

const MASKED = "£·····";

type Props = {
  account: AccountResponse;
};

export function AccountCard({ account }: Props) {
  const isCredit = account.accountType === "CREDIT_CARD";
  const balanceQuery = useBalance(isCredit ? "" : account.accountUid);
  const creditQuery = useCreditSummary(isCredit ? account.accountUid : "");
  const identifiersQuery = useIdentifiers(account.accountUid);
  const txQuery = useTransactions(account.accountUid);
  const hidden = useBalanceVisibility((s) => s.hidden);

  const latest =
    txQuery.transactions?.find((tx) => tx.status !== "DECLINED") ?? null;

  return (
    <Link
      to={`/accounts/${account.accountUid}`}
      data-testid="account-card"
      className="group block rounded-xl border border-border bg-card p-4 shadow-xs outline-none hover:border-ring/40 hover:shadow-sm focus-visible:ring-2 focus-visible:ring-ring/50 motion-safe:transition-[border-color,box-shadow]"
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <span className="block truncate font-medium">{account.name}</span>
          <span className="mt-1 block">
            {isCredit ? (
              <CreditLine query={creditQuery} hidden={hidden} />
            ) : identifiersQuery.isPending ? (
              <span
                data-testid="account-card-identifiers-skeleton"
                aria-busy="true"
                className="block h-4 w-40 rounded-md bg-muted/40 motion-safe:animate-pulse"
              >
                <span className="sr-only">Loading identifiers</span>
              </span>
            ) : identifiersQuery.isError || !identifiersQuery.identifiers ? (
              <span
                data-testid="account-card-identifiers-error"
                className="block text-sm text-muted-foreground"
              >
                —
              </span>
            ) : (
              <IdentifiersLine identifiers={identifiersQuery.identifiers} />
            )}
          </span>
        </div>
        <span className="shrink-0 text-right">
          {isCredit ? (
            <CreditBalance query={creditQuery} hidden={hidden} />
          ) : balanceQuery.isPending ? (
            <span
              data-testid="account-card-balance-skeleton"
              aria-busy="true"
              className="block h-7 w-32 rounded-md bg-muted/40 motion-safe:animate-pulse"
            >
              <span className="sr-only">Loading balance</span>
            </span>
          ) : balanceQuery.isError || !balanceQuery.balance ? (
            <span
              data-testid="account-card-balance-error"
              className="block text-xl font-semibold text-muted-foreground"
            >
              —
            </span>
          ) : hidden ? (
            <span
              data-testid="account-card-balance"
              className="block text-xl font-semibold tabular-nums"
            >
              {MASKED}
            </span>
          ) : (
            <span
              data-testid="account-card-balance"
              className="block text-xl font-semibold tabular-nums"
            >
              <Money
                minorUnits={balanceQuery.balance.availableBalanceMinorUnits}
              />
            </span>
          )}
        </span>
      </div>

      {latest ? <LatestTransactionLine tx={latest} hidden={hidden} /> : null}
    </Link>
  );
}

type CreditQuery = {
  summary: CreditSummaryResponse | null;
  isPending: boolean;
  isError: boolean;
};

function CreditBalance({
  query,
  hidden,
}: {
  query: CreditQuery;
  hidden: boolean;
}) {
  if (query.isPending) {
    return (
      <span
        data-testid="account-card-owed-skeleton"
        aria-busy="true"
        className="block h-7 w-32 rounded-md bg-muted/40 motion-safe:animate-pulse"
      >
        <span className="sr-only">Loading amount owed</span>
      </span>
    );
  }
  if (query.isError || !query.summary) {
    return (
      <span
        data-testid="account-card-owed-error"
        className="block text-xl font-semibold text-muted-foreground"
      >
        —
      </span>
    );
  }
  return (
    <span
      data-testid="account-card-owed"
      className="block text-xl font-semibold tabular-nums"
    >
      {hidden ? (
        MASKED
      ) : (
        <Money
          minorUnits={-query.summary.owedMinorUnits}
          currency={query.summary.currency}
        />
      )}
    </span>
  );
}

function CreditLine({
  query,
  hidden,
}: {
  query: CreditQuery;
  hidden: boolean;
}) {
  if (query.isPending || query.isError || !query.summary) {
    return (
      <span className="block text-sm text-muted-foreground">Credit card</span>
    );
  }
  return (
    <span
      data-testid="account-card-credit-line"
      className="block text-sm text-muted-foreground tabular-nums"
    >
      {hidden ? (
        `Available credit ${MASKED}`
      ) : (
        <>
          Available credit{" "}
          <Money
            minorUnits={query.summary.availableCreditMinorUnits}
            currency={query.summary.currency}
          />
        </>
      )}{" "}
      · Limit{" "}
      <Money
        minorUnits={query.summary.creditLimitMinorUnits}
        currency={query.summary.currency}
      />
    </span>
  );
}

function IdentifiersLine({
  identifiers,
}: {
  identifiers: AccountIdentifiersResponse;
}) {
  const primary = identifiers.accountIdentifiers[0];
  const sortCode = identifiers.bankIdentifier ?? primary?.bankIdentifier ?? "";
  const accountNumber =
    identifiers.accountIdentifier ?? primary?.accountIdentifier ?? "";

  return (
    <span
      data-testid="account-card-identifiers"
      className="block text-sm text-muted-foreground tabular-nums"
    >
      {sortCode} · {accountNumber}
    </span>
  );
}

function LatestTransactionLine({
  tx,
  hidden,
}: {
  tx: TransactionItemResponse;
  hidden: boolean;
}) {
  const counterparty = tx.merchantName ?? tx.description ?? "Transaction";

  return (
    <p
      data-testid="account-card-latest-transaction"
      className="mt-4 flex items-center justify-between gap-3 border-t border-border pt-3 text-sm"
    >
      <span className="min-w-0 truncate text-muted-foreground">
        {counterparty}
      </span>
      {hidden ? (
        <span className="shrink-0 tabular-nums text-muted-foreground">
          {MASKED}
        </span>
      ) : (
        <Money minorUnits={tx.amountMinorUnits} signed="always" />
      )}
    </p>
  );
}
