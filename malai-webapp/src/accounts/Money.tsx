import type { Currency } from "@/api/generated/types.gen";
import { formatCurrency } from "@/lib/formatCurrency";
import { cn } from "@/lib/utils";

type SignedMode = "always" | "negative" | "never";

type Props = {
  minorUnits: number;
  currency?: Currency;
  signed?: SignedMode;
  className?: string;
};

export function Money({
  minorUnits,
  currency = "GBP",
  signed = "negative",
  className,
}: Props) {
  const display = formatDisplay(minorUnits, currency, signed);
  const tone =
    signed === "always" && minorUnits > 0 ? "text-positive" : undefined;

  return (
    <span data-testid="money" className={cn("tabular-nums", tone, className)}>
      {display}
    </span>
  );
}

function formatDisplay(
  minorUnits: number,
  currency: Currency,
  signed: SignedMode,
): string {
  if (signed === "never") {
    return formatCurrency(Math.abs(minorUnits), currency);
  }
  if (signed === "always" && minorUnits > 0) {
    return `+${formatCurrency(minorUnits, currency)}`;
  }
  return formatCurrency(minorUnits, currency);
}
