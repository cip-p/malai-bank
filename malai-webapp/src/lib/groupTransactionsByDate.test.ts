import { describe, expect, it } from "vitest";
import type { TransactionItemResponse } from "@/api/generated/types.gen";
import { groupTransactionsByDate } from "./groupTransactionsByDate";

function tx(
  id: string,
  postedAt: string,
  amountMinorUnits = -100,
): TransactionItemResponse {
  return {
    entryId: id,
    transactionId: id,
    postedAt,
    currency: "GBP",
    amountMinorUnits,
    runningBalanceMinorUnits: 0,
  };
}

const NOW = new Date("2026-05-19T12:00:00Z");

describe("groupTransactionsByDate", () => {
  it("returns an empty array on empty input", () => {
    expect(groupTransactionsByDate([], NOW)).toEqual([]);
  });

  it("buckets a single transaction under 'Today'", () => {
    const t = tx("1", "2026-05-19T09:00:00Z");
    expect(groupTransactionsByDate([t], NOW)).toEqual([
      { label: "Today", items: [t] },
    ]);
  });

  it("preserves newest-first ordering within a single group", () => {
    const a = tx("a", "2026-05-19T10:00:00Z");
    const b = tx("b", "2026-05-19T09:00:00Z");
    const c = tx("c", "2026-05-19T08:00:00Z");
    const groups = groupTransactionsByDate([a, b, c], NOW);
    expect(groups).toHaveLength(1);
    expect(groups[0]).toEqual({ label: "Today", items: [a, b, c] });
  });

  it("creates multiple groups in the order encountered", () => {
    const today = tx("t", "2026-05-19T09:00:00Z");
    const yesterday = tx("y", "2026-05-18T09:00:00Z");
    const earlier = tx("e", "2026-05-14T09:00:00Z");
    const groups = groupTransactionsByDate([today, yesterday, earlier], NOW);
    expect(groups.map((g) => g.label)).toEqual([
      "Today",
      "Yesterday",
      "14 May",
    ]);
    expect(groups.map((g) => g.items)).toEqual([
      [today],
      [yesterday],
      [earlier],
    ]);
  });

  it("uses prior-year label for transactions in earlier years", () => {
    const t = tx("p", "2024-04-02T09:00:00Z");
    const groups = groupTransactionsByDate([t], NOW);
    expect(groups).toEqual([{ label: "2 Apr 2024", items: [t] }]);
  });
});
