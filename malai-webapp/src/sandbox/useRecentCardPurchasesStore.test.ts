import { beforeEach, describe, expect, it } from "vitest";
import {
  useRecentCardPurchasesStore,
  type RecentCardPurchase,
} from "./useRecentCardPurchasesStore";

function purchase(id: string): RecentCardPurchase {
  return {
    authorisationId: id,
    accountUid: "22222222-2222-2222-2222-222222222222",
    cardId: "33333333-3333-3333-3333-333333333333",
    merchantName: "Fern & Forage",
    amountMinorUnits: -1250,
    currency: "GBP",
  };
}

describe("useRecentCardPurchasesStore", () => {
  beforeEach(() => {
    useRecentCardPurchasesStore.setState({ purchases: [] });
  });

  it("prepends newly recorded purchases", () => {
    const { record } = useRecentCardPurchasesStore.getState();
    record(purchase("a"));
    record(purchase("b"));
    expect(
      useRecentCardPurchasesStore
        .getState()
        .purchases.map((p) => p.authorisationId),
    ).toEqual(["b", "a"]);
  });

  it("ignores a purchase already recorded", () => {
    const { record } = useRecentCardPurchasesStore.getState();
    record(purchase("a"));
    record(purchase("a"));
    expect(useRecentCardPurchasesStore.getState().purchases).toHaveLength(1);
  });

  it("caps the list at ten purchases", () => {
    const { record } = useRecentCardPurchasesStore.getState();
    for (let i = 0; i < 12; i += 1) {
      record(purchase(`p${i}`));
    }
    const ids = useRecentCardPurchasesStore
      .getState()
      .purchases.map((p) => p.authorisationId);
    expect(ids).toHaveLength(10);
    expect(ids[0]).toBe("p11");
  });
});
