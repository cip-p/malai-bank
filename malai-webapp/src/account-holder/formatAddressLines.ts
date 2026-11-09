import type { AddressV2Response } from "@/api/generated/types.gen";

export function formatAddressLines(address: AddressV2Response): string[] {
  return [
    address.line1,
    address.line2,
    address.line3,
    address.postTown,
    address.postCode,
  ]
    .map((part) => part?.trim() ?? "")
    .filter((part) => part !== "");
}
