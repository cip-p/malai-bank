import { create } from "zustand";

type ActiveHolderOverrideState = {
  override: string | null;
  setOverride: (accountHolderUid: string | null) => void;
};

export const useActiveHolderOverrideStore = create<ActiveHolderOverrideState>()(
  (set) => ({
    override: null,
    setOverride: (accountHolderUid) => set({ override: accountHolderUid }),
  }),
);
