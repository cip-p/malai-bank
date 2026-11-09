import type { Currency } from "@/api/generated/types.gen";

const UNICODE_MINUS = "−";
const ASCII_HYPHEN_MINUS = "-";

export function formatCurrency(
  amountMinorUnits: number,
  currency: Currency,
): string {
  const major = (amountMinorUnits === 0 ? 0 : amountMinorUnits) / 100;
  const formatted = new Intl.NumberFormat("en-GB", {
    style: "currency",
    currency,
  }).format(major);
  return formatted.startsWith(ASCII_HYPHEN_MINUS)
    ? `${UNICODE_MINUS}${formatted.slice(1)}`
    : formatted;
}
