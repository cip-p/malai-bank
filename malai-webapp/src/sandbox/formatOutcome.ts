import type { CounterpartyResponse } from "@/api/generated/types.gen";

export function formatOutcome(counterparty: CounterpartyResponse): string {
  switch (counterparty.outboundOutcome) {
    case "SETTLES":
      return "Settles";
    case "RETURNS":
      return counterparty.returnReason
        ? `Returns (${counterparty.returnReason})`
        : "Returns";
    case "STUCK_PENDING":
      return "Stays in progress";
  }
}
