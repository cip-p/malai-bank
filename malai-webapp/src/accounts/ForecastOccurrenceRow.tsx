import { Link } from "react-router-dom";
import { format, parseISO } from "date-fns";
import type { Currency, ForecastOccurrence } from "@/api/generated/types.gen";
import { Money } from "./Money";
import { forecastOccurrenceHref } from "./forecastOccurrenceHref";

type Props = {
  occurrence: ForecastOccurrence;
  currency: Currency;
};

const TYPE_LABEL: Record<ForecastOccurrence["type"], string> = {
  STANDING_ORDER: "Standing order",
  DIRECT_DEBIT: "Direct Debit",
};

export function ForecastOccurrenceRow({ occurrence, currency }: Props) {
  const dateLabel = format(parseISO(occurrence.date), "d MMM");

  return (
    <li data-testid="forecast-occurrence-row">
      <Link
        to={forecastOccurrenceHref(occurrence)}
        className="flex items-center gap-4 rounded-sm py-3 outline-none hover:bg-muted/40 focus-visible:ring-2 focus-visible:ring-ring/50"
      >
        <span
          data-testid="forecast-occurrence-date"
          className="w-14 shrink-0 text-sm tabular-nums text-muted-foreground"
        >
          {dateLabel}
        </span>
        <span className="min-w-0 flex-1">
          <span
            data-testid="forecast-occurrence-name"
            className="block truncate font-medium"
          >
            {occurrence.counterpartyName}
          </span>
          <span
            data-testid="forecast-occurrence-type"
            className="mt-0.5 block text-xs text-muted-foreground"
          >
            {TYPE_LABEL[occurrence.type]}
          </span>
        </span>
        <span
          data-testid="forecast-occurrence-amount"
          className="shrink-0 text-right font-medium tabular-nums"
        >
          <Money minorUnits={occurrence.amountMinorUnits} currency={currency} />
        </span>
      </Link>
    </li>
  );
}
