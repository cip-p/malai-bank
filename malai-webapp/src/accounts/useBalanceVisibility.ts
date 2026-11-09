import { create } from "zustand";
import { persist, createJSONStorage } from "zustand/middleware";

type BalanceVisibilityState = {
  hidden: boolean;
  toggle: () => void;
  setHidden: (hidden: boolean) => void;
};

export const BALANCE_VISIBILITY_STORAGE_KEY = "malai:balance-visibility";

export const useBalanceVisibility = create<BalanceVisibilityState>()(
  persist(
    (set) => ({
      hidden: false,
      toggle: () => set((s) => ({ hidden: !s.hidden })),
      setHidden: (hidden) => set({ hidden }),
    }),
    {
      name: BALANCE_VISIBILITY_STORAGE_KEY,
      storage: createJSONStorage(() => sessionStorage),
      partialize: (state) => ({ hidden: state.hidden }),
    },
  ),
);
