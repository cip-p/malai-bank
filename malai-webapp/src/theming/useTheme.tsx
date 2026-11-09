import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";

export type ThemeId = "malai" | "halcyon" | "linden";

export type Theme = {
  id: ThemeId;
  label: string;
};

export const THEMES: readonly Theme[] = [
  { id: "malai", label: "Malai" },
  { id: "halcyon", label: "Halcyon" },
  { id: "linden", label: "Linden" },
] as const;

export const DEFAULT_THEME: ThemeId = "malai";
const STORAGE_KEY = "malai-theme";

const KNOWN_IDS = new Set<string>(THEMES.map((t) => t.id));

function isThemeId(value: unknown): value is ThemeId {
  return typeof value === "string" && KNOWN_IDS.has(value);
}

function readStoredTheme(): ThemeId {
  if (typeof window === "undefined") return DEFAULT_THEME;
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    return isThemeId(raw) ? raw : DEFAULT_THEME;
  } catch {
    return DEFAULT_THEME;
  }
}

export function applyThemeAttribute(theme: ThemeId): void {
  if (typeof document === "undefined") return;
  document.documentElement.setAttribute("data-theme", theme);
}

type ThemeContextValue = {
  theme: ThemeId;
  setTheme: (next: ThemeId) => void;
  themes: readonly Theme[];
  pin: (pinned: ThemeId) => () => void;
};

const ThemeContext = createContext<ThemeContextValue | null>(null);

type ThemeProviderProps = {
  children: ReactNode;
};

export function ThemeProvider({ children }: ThemeProviderProps) {
  const [theme, setThemeState] = useState<ThemeId>(() => readStoredTheme());
  const [pinned, setPinned] = useState<ThemeId | null>(null);

  useEffect(() => {
    applyThemeAttribute(pinned ?? theme);
  }, [pinned, theme]);

  const pin = useCallback((next: ThemeId) => {
    setPinned(next);
    return () => setPinned(null);
  }, []);

  const setTheme = useCallback((next: ThemeId) => {
    if (!isThemeId(next)) return;
    setThemeState(next);
    try {
      window.localStorage.setItem(STORAGE_KEY, next);
    } catch {
      void 0;
    }
  }, []);

  const value = useMemo<ThemeContextValue>(
    () => ({ theme, setTheme, themes: THEMES, pin }),
    [theme, setTheme, pin],
  );

  return (
    <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
  );
}

export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext);
  if (!ctx) {
    throw new Error("useTheme must be used within a ThemeProvider");
  }
  return ctx;
}
