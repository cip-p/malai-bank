import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { InlineThemeSwitcher } from "./InlineThemeSwitcher";
import { ThemeProvider } from "./useTheme";

const STORAGE_KEY = "malai-theme";

beforeEach(() => {
  window.localStorage.clear();
  document.documentElement.removeAttribute("data-theme");
});

afterEach(() => {
  window.localStorage.clear();
  document.documentElement.removeAttribute("data-theme");
});

function renderSwitcher() {
  return render(
    <ThemeProvider>
      <InlineThemeSwitcher />
    </ThemeProvider>,
  );
}

describe("InlineThemeSwitcher", () => {
  it("renders a radiogroup labelled 'Theme'", () => {
    renderSwitcher();
    const group = screen.getByRole("radiogroup", { name: "Theme" });
    expect(group).toBeInTheDocument();
    expect(group).toHaveAttribute("data-testid", "inline-theme-switcher");
  });

  it("renders one radio per theme with the right label and swatch data-theme", () => {
    renderSwitcher();
    for (const id of ["malai", "halcyon", "linden"] as const) {
      expect(screen.getByTestId(`inline-theme-item-${id}`)).toBeInTheDocument();
      expect(screen.getByTestId(`inline-theme-swatch-${id}`)).toHaveAttribute(
        "data-theme",
        id,
      );
    }
    expect(screen.getByRole("radio", { name: /Malai/ })).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: /Halcyon/ })).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: /Linden/ })).toBeInTheDocument();
  });

  it("marks the active theme aria-checked=true and the others aria-checked=false", () => {
    renderSwitcher();
    expect(screen.getByTestId("inline-theme-item-malai")).toHaveAttribute(
      "aria-checked",
      "true",
    );
    expect(screen.getByTestId("inline-theme-item-halcyon")).toHaveAttribute(
      "aria-checked",
      "false",
    );
    expect(screen.getByTestId("inline-theme-item-linden")).toHaveAttribute(
      "aria-checked",
      "false",
    );
    expect(screen.getByTestId("inline-theme-check-malai")).toBeInTheDocument();
    expect(
      screen.queryByTestId("inline-theme-check-halcyon"),
    ).not.toBeInTheDocument();
  });

  it("selecting a theme applies it to <html> and persists in localStorage", async () => {
    const user = userEvent.setup();
    renderSwitcher();
    await user.click(screen.getByTestId("inline-theme-item-halcyon"));
    expect(document.documentElement.getAttribute("data-theme")).toBe("halcyon");
    expect(window.localStorage.getItem(STORAGE_KEY)).toBe("halcyon");
    expect(screen.getByTestId("inline-theme-item-halcyon")).toHaveAttribute(
      "aria-checked",
      "true",
    );
  });
});
