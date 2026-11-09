import type { ReturnReason } from "@/api/generated/types.gen";

export function formatReturnReason(
  reason: ReturnReason | null | undefined,
): string {
  switch (reason) {
    case "ACCOUNT_CLOSED":
      return "The recipient's account has been closed.";
    case "ACCOUNT_NAME_MISMATCH":
      return "The name you entered doesn't match the account.";
    case "RECIPIENT_BANK_UNAVAILABLE":
      return "The recipient's bank isn't available right now.";
    case "BENEFICIARY_DECEASED":
      return "The recipient's bank told us this account can no longer receive payments.";
    case "INVALID_ACCOUNT":
      return "These account details don't match a real account.";
    default:
      return "The recipient's bank returned this payment.";
  }
}
