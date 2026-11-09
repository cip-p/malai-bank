import { startOfDay } from "date-fns";
import type { TransactionItemResponse } from "@/api/generated/types.gen";
import { formatRelativeDateGroup } from "./formatRelativeDateGroup";

export type TransactionGroup = {
  label: string;
  items: TransactionItemResponse[];
};

export function groupTransactionsByDate(
  transactions: TransactionItemResponse[],
  now: Date,
): TransactionGroup[] {
  const groups: TransactionGroup[] = [];
  let currentKey: number | null = null;

  for (const tx of transactions) {
    const dayStart = startOfDay(new Date(tx.postedAt)).getTime();
    if (dayStart !== currentKey) {
      groups.push({
        label: formatRelativeDateGroup(new Date(dayStart), now),
        items: [],
      });
      currentKey = dayStart;
    }
    groups[groups.length - 1].items.push(tx);
  }

  return groups;
}
