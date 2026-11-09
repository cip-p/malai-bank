import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeSwitcher } from "./ThemeSwitcher";
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
      <ThemeSwitcher />
    </ThemeProvider>,
  );
}

async function openMenu() {
  renderSwitcher();
  const user = userEvent.setup();
  await user.click(screen.getByTestId("theme-switcher-trigger"));
  return user;
}

describe("ThemeSwitcher", () => {
  it("renders a trigger that names the active theme and is hidden below md:", () => {
    renderSwitcher();
    const trigger = screen.getByTestId("theme-switcher-trigger");
    expect(trigger).toHaveAttribute("aria-expanded", "false");
    expect(trigger).toHaveClass("hidden");
    expect(trigger).toHaveClass("md:inline-flex");
    expect(screen.getByTestId("theme-switcher-active-label")).toHaveTextContent(
      "Malai",
    );
  });

  it("opens a menu listing all three themes with swatches and a check on the active one", async () => {
    await openMenu();
    const menu = await screen.findByTestId("theme-switcher-menu");
    expect(menu).toBeInTheDocument();

    for (const id of ["malai", "halcyon", "linden"] as const) {
      expect(
        screen.getByTestId(`theme-switcher-item-${id}`),
      ).toBeInTheDocument();
      const swatch = screen.getByTestId(`theme-switcher-swatch-${id}`);
      expect(swatch).toHaveAttribute("data-theme", id);
    }
    expect(
      screen.getByTestId("theme-switcher-check-malai"),
    ).toBeInTheDocument();
    expect(
      screen.queryByTestId("theme-switcher-check-halcyon"),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTestId("theme-switcher-check-linden"),
    ).not.toBeInTheDocument();
  });

  it("selecting a theme updates document.documentElement[data-theme] and localStorage", async () => {
    const user = await openMenu();
    await user.click(screen.getByTestId("theme-switcher-item-halcyon"));
    expect(document.documentElement.getAttribute("data-theme")).toBe("halcyon");
    expect(window.localStorage.getItem(STORAGE_KEY)).toBe("halcyon");
  });

  it("selecting a theme updates the trigger label and the menu check on next open", async () => {
    const user = await openMenu();
    await user.click(screen.getByTestId("theme-switcher-item-linden"));
    expect(screen.getByTestId("theme-switcher-active-label")).toHaveTextContent(
      "Linden",
    );

    await user.click(screen.getByTestId("theme-switcher-trigger"));
    expect(
      await screen.findByTestId("theme-switcher-check-linden"),
    ).toBeInTheDocument();
    expect(
      screen.queryByTestId("theme-switcher-check-malai"),
    ).not.toBeInTheDocument();
  });

  it("each menu item carries aria-checked reflecting the active theme", async () => {
    await openMenu();
    expect(screen.getByTestId("theme-switcher-item-malai")).toHaveAttribute(
      "aria-checked",
      "true",
    );
    expect(screen.getByTestId("theme-switcher-item-halcyon")).toHaveAttribute(
      "aria-checked",
      "false",
    );
    expect(screen.getByTestId("theme-switcher-item-linden")).toHaveAttribute(
      "aria-checked",
      "false",
    );
  });
});
