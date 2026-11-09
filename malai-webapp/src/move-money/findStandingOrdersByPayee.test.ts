import { describe, expect, it } from "vitest";
import type { StandingOrderResponse } from "@/api/generated/types.gen";
import { findStandingOrdersByPayee } from "./findStandingOrdersByPayee";

const baseSo = (
  overrides: Partial<StandingOrderResponse>,
): StandingOrderResponse => ({
  standingOrderId: "so-1",
  accountId: "acc-1",
  payeeName: "Thames Water",
  payeeSortCode: "12-34-56",
  payeeAccountNumber: "12345678",
  amountMinorUnits: 1000,
  currency: "GBP",
  frequency: "MONTHLY",
  status: "ACTIVE",
  startDate: "2026-01-15",
  ...overrides,
});

describe("findStandingOrdersByPayee", () => {
  it("returns [] for an empty input array", () => {
    expect(findStandingOrdersByPayee([], "payee-a")).toEqual([]);
  });

  it("returns [] when no SO references the payee", () => {
    const sos = [
      baseSo({ payeeId: "payee-x" }),
      baseSo({ payeeId: "payee-y" }),
    ];
    expect(findStandingOrdersByPayee(sos, "payee-a")).toEqual([]);
  });

  it("returns [] when SOs have no payeeId at all", () => {
    const sos = [baseSo({}), baseSo({ standingOrderId: "so-2" })];
    expect(findStandingOrdersByPayee(sos, "payee-a")).toEqual([]);
  });

  it("returns the single SO that matches", () => {
    const match = baseSo({ standingOrderId: "so-match", payeeId: "payee-a" });
    const others = [baseSo({ payeeId: "payee-b" })];
    expect(findStandingOrdersByPayee([...others, match], "payee-a")).toEqual([
      match,
    ]);
  });

  it("returns every SO that matches when there are multiple", () => {
    const m1 = baseSo({ standingOrderId: "so-1", payeeId: "payee-a" });
    const m2 = baseSo({ standingOrderId: "so-2", payeeId: "payee-a" });
    const other = baseSo({ standingOrderId: "so-3", payeeId: "payee-b" });
    expect(findStandingOrdersByPayee([m1, other, m2], "payee-a")).toEqual([
      m1,
      m2,
    ]);
  });
});
