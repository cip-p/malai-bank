const REASONS: Record<string, string> = {
  CARD_FROZEN: "Card frozen",
  CARD_CANCELLED: "Card cancelled",
  INSUFFICIENT_FUNDS: "Insufficient funds",
  OVER_LIMIT: "Over your credit limit",
};

export function formatDeclineReason(reason: string | null | undefined): string {
  if (!reason) return "Declined";
  return REASONS[reason] ?? "Declined";
}

const SCHEME_CODE_TO_REASON: Record<string, string> = {
  "62": "CARD_FROZEN",
  "04": "CARD_CANCELLED",
  "51": "INSUFFICIENT_FUNDS",
  "61": "OVER_LIMIT",
};

export function formatSchemeDeclineCode(
  code: string | null | undefined,
): string {
  if (!code) return "Declined";
  return formatDeclineReason(SCHEME_CODE_TO_REASON[code] ?? code);
}
