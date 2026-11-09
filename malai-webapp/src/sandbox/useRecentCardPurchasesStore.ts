import { create } from "zustand";
import type { Currency } from "@/api/generated/types.gen";

export type RecentCardPurchase = {
  authorisationId: string;
  accountUid: string;
  cardId: string;
  merchantName: string | null;
  amountMinorUnits: number;
  currency: Currency;
};

const MAX_RECENT = 10;

type RecentCardPurchasesState = {
  purchases: RecentCardPurchase[];
  record: (purchase: RecentCardPurchase) => void;
};

export const useRecentCardPurchasesStore = create<RecentCardPurchasesState>(
  (set) => ({
    purchases: [],
    record: (purchase) =>
      set((state) => {
        if (
          state.purchases.some(
            (p) => p.authorisationId === purchase.authorisationId,
          )
        ) {
          return state;
        }
        return {
          purchases: [purchase, ...state.purchases].slice(0, MAX_RECENT),
        };
      }),
  }),
);
