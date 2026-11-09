import type {
  Currency,
  SpendingCategoryTotal,
} from "@/api/generated/types.gen";
import { categoryLabel } from "@/lib/categories";
import { CategoryIcon } from "@/lib/CategoryIcon";
import { Money } from "./Money";

type Props = {
  categories: SpendingCategoryTotal[];
  totalMinorUnits: number;
  currency: Currency;
};

export function shareOf(part: number, whole: number): number {
  if (whole <= 0) return 0;
  return (part / whole) * 100;
}

export function SpendingBars({ categories, totalMinorUnits, currency }: Props) {
  const ranked = [...categories].sort(
    (a, b) => b.totalMinorUnits - a.totalMinorUnits,
  );

  return (
    <div data-testid="spending-breakdown">
      <p className="text-sm text-muted-foreground">Spent</p>
      <p
        data-testid="spending-total"
        className="mt-1 text-xl font-semibold tabular-nums"
      >
        <Money
          minorUnits={totalMinorUnits}
          currency={currency}
          signed="never"
        />
      </p>

      <ul data-testid="spending-rows" className="mt-6 space-y-5">
        {ranked.map((entry) => {
          const share = shareOf(entry.totalMinorUnits, totalMinorUnits);
          return (
            <li
              key={entry.category}
              data-testid="spending-row"
              data-category={entry.category}
              className="flex items-start gap-3"
            >
              <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-muted text-muted-foreground">
                <CategoryIcon category={entry.category} className="h-5 w-5" />
              </span>
              <span className="min-w-0 flex-1">
                <span className="flex items-baseline justify-between gap-4">
                  <span className="truncate font-medium">
                    {categoryLabel(entry.category)}
                  </span>
                  <span
                    data-testid="spending-row-amount"
                    className="shrink-0 font-medium tabular-nums"
                  >
                    <Money
                      minorUnits={entry.totalMinorUnits}
                      currency={currency}
                      signed="never"
                    />
                  </span>
                </span>
                <span
                  aria-hidden="true"
                  className="mt-2 block h-1.5 w-full overflow-hidden rounded-full bg-muted"
                >
                  <span
                    data-testid="spending-row-bar"
                    className="block h-full rounded-full bg-primary"
                    style={{ width: `${share}%` }}
                  />
                </span>
                <span
                  data-testid="spending-row-share"
                  className="mt-1.5 block text-xs text-muted-foreground tabular-nums"
                >
                  {Math.round(share)}% · {entry.transactionCount}{" "}
                  {entry.transactionCount === 1
                    ? "transaction"
                    : "transactions"}
                </span>
              </span>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
