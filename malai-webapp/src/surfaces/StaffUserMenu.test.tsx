import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { ThemeProvider as NextThemesProvider } from "next-themes";
import type { UserResponse } from "@/api/generated/types.gen";
import { StaffUserMenu } from "./StaffUserMenu";

vi.mock("@/auth/useSignout", () => ({
  useSignout: () => ({ signout: vi.fn(), isPending: false }),
}));

const MANAGER: UserResponse = {
  firstName: "Grace",
  lastName: "Hopper",
  email: "grace@example.com",
  roles: ["MANAGER"],
};

afterEach(() => {
  window.localStorage.removeItem("malai-mode");
  document.documentElement.classList.remove("dark");
});

function renderMenu() {
  return render(
    <NextThemesProvider
      attribute="class"
      defaultTheme="system"
      enableSystem
      storageKey="malai-mode"
    >
      <MemoryRouter initialEntries={["/exec"]}>
        <StaffUserMenu user={MANAGER} currentSurface="exec" />
      </MemoryRouter>
    </NextThemesProvider>,
  );
}

describe("StaffUserMenu mode control", () => {
  it("offers Light, Dark and System as a radio group inside the menu", async () => {
    const user = userEvent.setup();
    renderMenu();
    await user.click(screen.getByTestId("exec-user-menu-trigger"));
    const options = await screen.findAllByRole("menuitemradio");
    expect(options.map((o) => o.textContent)).toEqual([
      "Light",
      "Dark",
      "System",
    ]);
    expect(screen.getByTestId("exec-mode-item-system")).toHaveAttribute(
      "aria-checked",
      "true",
    );
  });

  it("switches the app to dark mode from a staff surface", async () => {
    const user = userEvent.setup();
    renderMenu();
    await user.click(screen.getByTestId("exec-user-menu-trigger"));
    await user.click(await screen.findByTestId("exec-mode-item-dark"));
    expect(document.documentElement).toHaveClass("dark");
    expect(window.localStorage.getItem("malai-mode")).toBe("dark");
  });
});
