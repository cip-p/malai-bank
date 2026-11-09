import type { Currency } from "@/api/generated/types.gen";
import { formatCurrency } from "@/lib/formatCurrency";

type Props = {
  amountMinorUnits: number;
  currency: Currency;
  hidden: boolean;
};

const MASKED = "£·····";

export function BalanceText({ amountMinorUnits, currency, hidden }: Props) {
  return (
    <span>{hidden ? MASKED : formatCurrency(amountMinorUnits, currency)}</span>
  );
}
