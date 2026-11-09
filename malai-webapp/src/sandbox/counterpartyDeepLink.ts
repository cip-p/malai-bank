import type { CounterpartyResponse } from "@/api/generated/types.gen";

export type CounterpartyDeepLinkTarget =
  | "save-as-payee"
  | "send-a-payment"
  | "receive-a-payment";

export function counterpartyDeepLink(
  counterparty: CounterpartyResponse,
  target: CounterpartyDeepLinkTarget,
): string {
  const sortCode = counterparty.sortCode;
  const accountNumber = counterparty.accountNumber;
  const encodedName = encodeURIComponent(counterparty.nameOnRecord).replace(
    /'/g,
    "%27",
  );
  switch (target) {
    case "save-as-payee":
      return `/move/payees/new?sortCode=${sortCode}&accountNumber=${accountNumber}&name=${encodedName}`;
    case "send-a-payment":
      return `/move/send?destSortCode=${sortCode}&destAccountNumber=${accountNumber}&destName=${encodedName}`;
    case "receive-a-payment":
      return `/sandbox/inbound-payments?originatorSortCode=${sortCode}&originatorAccountNumber=${accountNumber}&originatorName=${encodedName}`;
  }
}
