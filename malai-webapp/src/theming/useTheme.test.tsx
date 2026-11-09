import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { act, render, renderHook, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider, THEMES, useTheme } from "./useTheme";

const STORAGE_KEY = "malai-theme";

beforeEach(() => {
  window.localStorage.clear();
  document.documentElement.removeAttribute("data-theme");
});

afterEach(() => {
  window.localStorage.clear();
  document.documentElement.removeAttribute("data-theme");
});

function wrapper({ children }: { children: React.ReactNode }) {
  return <ThemeProvider>{children}</ThemeProvider>;
}

describe("THEMES", () => {
  it("lists the three named themes in catalogue order", () => {
    expect(THEMES.map((t) => t.id)).toEqual(["malai", "halcyon", "linden"]);
  });

  it("each entry has a human label", () => {
    for (const theme of THEMES) {
      expect(theme.label).toMatch(/^[A-Z]/);
    }
  });
});

describe("ThemeProvider + useTheme", () => {
  it("defaults to malai when localStorage is empty", () => {
    const { result } = renderHook(() => useTheme(), { wrapper });
    expect(result.current.theme).toBe("malai");
    expect(document.documentElement.getAttribute("data-theme")).toBe("malai");
  });

  it("falls back to malai when the stored value is not a known theme", () => {
    window.localStorage.setItem(STORAGE_KEY, "rainbow");
    const { result } = renderHook(() => useTheme(), { wrapper });
    expect(result.current.theme).toBe("malai");
    expect(document.documentElement.getAttribute("data-theme")).toBe("malai");
  });

  it("rehydrates the stored theme on mount", () => {
    window.localStorage.setItem(STORAGE_KEY, "halcyon");
    const { result } = renderHook(() => useTheme(), { wrapper });
    expect(result.current.theme).toBe("halcyon");
    expect(document.documentElement.getAttribute("data-theme")).toBe("halcyon");
  });

  it("setTheme updates state, the data-theme attribute, and localStorage", () => {
    const { result } = renderHook(() => useTheme(), { wrapper });
    act(() => result.current.setTheme("linden"));
    expect(result.current.theme).toBe("linden");
    expect(document.documentElement.getAttribute("data-theme")).toBe("linden");
    expect(window.localStorage.getItem(STORAGE_KEY)).toBe("linden");
  });

  it("exposes the themes catalogue", () => {
    const { result } = renderHook(() => useTheme(), { wrapper });
    expect(result.current.themes).toEqual(THEMES);
  });

  it("setTheme refuses unknown values", () => {
    const { result } = renderHook(() => useTheme(), { wrapper });
    act(() => {
      result.current.setTheme("rainbow" as never);
    });
    expect(result.current.theme).toBe("malai");
    expect(document.documentElement.getAttribute("data-theme")).toBe("malai");
    expect(window.localStorage.getItem(STORAGE_KEY)).not.toBe("rainbow");
  });

  it("subscribers in two components share the same active theme", async () => {
    function Probe({ id }: { id: string }) {
      const { theme, setTheme } = useTheme();
      return (
        <div>
          <span data-testid={`theme-${id}`}>{theme}</span>
          <button
            type="button"
            data-testid={`switch-${id}`}
            onClick={() => setTheme("halcyon")}
          >
            switch
          </button>
        </div>
      );
    }
    render(
      <ThemeProvider>
        <Probe id="a" />
        <Probe id="b" />
      </ThemeProvider>,
    );
    expect(screen.getByTestId("theme-a")).toHaveTextContent("malai");
    expect(screen.getByTestId("theme-b")).toHaveTextContent("malai");
    await userEvent.click(screen.getByTestId("switch-a"));
    expect(screen.getByTestId("theme-a")).toHaveTextContent("halcyon");
    expect(screen.getByTestId("theme-b")).toHaveTextContent("halcyon");
  });

  it("throws a useful error when useTheme is used outside ThemeProvider", () => {
    expect(() => renderHook(() => useTheme())).toThrowError(
      /useTheme.*ThemeProvider/,
    );
  });
});
