import { useEffect } from "react";
import { create } from "zustand";

type PageTitleState = {
  title: string;
  setTitle: (title: string) => void;
};

export const pageTitleStore = create<PageTitleState>((set) => ({
  title: "",
  setTitle: (title) => set({ title }),
}));

export function usePageTitle(title: string): void {
  const setTitle = pageTitleStore((s) => s.setTitle);
  useEffect(() => {
    setTitle(title);
    return () => setTitle("");
  }, [title, setTitle]);
}

export function useActivePageTitle(): string {
  return pageTitleStore((s) => s.title);
}
