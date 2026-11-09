import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider as NextThemesProvider } from "next-themes";
import { ModeToggle } from "./ModeToggle";

beforeEach(() => {
  window.localStorage.clear();
  document.documentElement.classList.remove("light", "dark");
  document.documentElement.removeAttribute("style");
});

afterEach(() => {
  window.localStorage.clear();
  document.documentElement.classList.remove("light", "dark");
  document.documentElement.removeAttribute("style");
});

function renderToggle(defaultTheme: "light" | "dark" | "system" = "system") {
  return render(
    <NextThemesProvider
      attribute="class"
      defaultTheme={defaultTheme}
      enableSystem
      disableTransitionOnChange
      storageKey="malai-mode"
    >
      <ModeToggle />
    </NextThemesProvider>,
  );
}

describe("ModeToggle", () => {
  it("renders a radiogroup labelled 'Mode' with Light, Dark, System options", () => {
    renderToggle();
    expect(
      screen.getByRole("radiogroup", { name: "Mode" }),
    ).toBeInTheDocument();
    for (const id of ["light", "dark", "system"] as const) {
      expect(screen.getByTestId(`mode-toggle-item-${id}`)).toBeInTheDocument();
    }
  });

  it("marks the default mode aria-checked=true and the others aria-checked=false", () => {
    renderToggle("system");
    expect(screen.getByTestId("mode-toggle-item-system")).toHaveAttribute(
      "aria-checked",
      "true",
    );
    expect(screen.getByTestId("mode-toggle-item-light")).toHaveAttribute(
      "aria-checked",
      "false",
    );
    expect(screen.getByTestId("mode-toggle-item-dark")).toHaveAttribute(
      "aria-checked",
      "false",
    );
  });

  it("selecting Dark applies the dark class to <html> and persists in storage", async () => {
    const user = userEvent.setup();
    renderToggle("light");
    await user.click(screen.getByTestId("mode-toggle-item-dark"));
    expect(document.documentElement.classList.contains("dark")).toBe(true);
    expect(window.localStorage.getItem("malai-mode")).toBe("dark");
    expect(screen.getByTestId("mode-toggle-item-dark")).toHaveAttribute(
      "aria-checked",
      "true",
    );
  });

  it("selecting Light removes the dark class from <html>", async () => {
    const user = userEvent.setup();
    renderToggle("dark");
    await user.click(screen.getByTestId("mode-toggle-item-light"));
    expect(document.documentElement.classList.contains("dark")).toBe(false);
    expect(window.localStorage.getItem("malai-mode")).toBe("light");
  });

  it("selecting System writes 'system' to storage", async () => {
    const user = userEvent.setup();
    renderToggle("light");
    await user.click(screen.getByTestId("mode-toggle-item-system"));
    expect(window.localStorage.getItem("malai-mode")).toBe("system");
    expect(screen.getByTestId("mode-toggle-item-system")).toHaveAttribute(
      "aria-checked",
      "true",
    );
  });
});
