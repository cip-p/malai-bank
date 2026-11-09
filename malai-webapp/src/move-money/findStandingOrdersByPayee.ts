import type { StandingOrderResponse } from "@/api/generated/types.gen";

export function findStandingOrdersByPayee(
  sos: StandingOrderResponse[],
  payeeId: string,
): StandingOrderResponse[] {
  return sos.filter((so) => so.payeeId === payeeId);
}
