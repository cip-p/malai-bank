import type { PaymentState } from "@/api/generated/types.gen";

const POLL_MS: Record<PaymentState, number | null> = {
  PENDING: 1_500,
  REFERRED: 5_000,
  SETTLED: null,
  RETURNED: null,
  DECLINED: null,
};

export function pollIntervalFor(state: PaymentState): number | null {
  return POLL_MS[state];
}

export function isInFlight(state: PaymentState): boolean {
  return POLL_MS[state] !== null;
}
