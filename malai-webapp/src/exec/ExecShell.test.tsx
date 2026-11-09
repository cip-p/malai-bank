import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";
import type { UserResponse } from "@/api/generated/types.gen";
import { ThemeProvider } from "@/theming/useTheme";
import { ExecShell } from "./ExecShell";

const signoutSpy = vi.fn();
const useSignoutMock = vi.fn(() => ({ signout: signoutSpy, isPending: false }));
vi.mock("@/auth/useSignout", () => ({
  useSignout: () => useSignoutMock(),
}));

function makeUser(roles: UserResponse["roles"]): UserResponse {
  return {
    firstName: "Ada",
    lastName: "Lovelace",
    email: "ada@example.com",
    roles,
  };
}

const EVALUATOR = makeUser(["CUSTOMER", "ADMIN"]);

beforeEach(() => {
  signoutSpy.mockReset();
  window.localStorage.setItem("malai-theme", "halcyon");
});

afterEach(() => {
  window.localStorage.removeItem("malai-theme");
  document.documentElement.setAttribute("data-theme", "malai");
});

function Stage({ children }: { children: ReactNode }) {
  return (
    <ThemeProvider>
      <MemoryRouter initialEntries={["/exec"]}>{children}</MemoryRouter>
    </ThemeProvider>
  );
}

function renderShell(user: UserResponse = EVALUATOR) {
  return render(
    <Stage>
      <ExecShell user={user}>
        <div>exec content</div>
      </ExecShell>
    </Stage>,
  );
}

describe("ExecShell theming", () => {
  it("pins the default token set while mounted and restores the persisted brand on leave", () => {
    const view = render(<Stage>banking</Stage>);
    expect(document.documentElement.getAttribute("data-theme")).toBe("halcyon");
    view.rerender(
      <Stage>
        <ExecShell user={EVALUATOR}>
          <div>exec content</div>
        </ExecShell>
      </Stage>,
    );
    expect(document.documentElement.getAttribute("data-theme")).toBe("malai");
    view.rerender(<Stage>banking</Stage>);
    expect(document.documentElement.getAttribute("data-theme")).toBe("halcyon");
  });

  it("offers no theme switcher", () => {
    renderShell();
    expect(screen.queryByTestId("theme-switcher")).not.toBeInTheDocument();
  });
});

describe("ExecShell chrome", () => {
  it("renders the children inside the main landmark with a skip link", () => {
    renderShell();
    expect(screen.getByText("exec content")).toBeInTheDocument();
    expect(screen.getByRole("main")).toContainElement(
      screen.getByText("exec content"),
    );
    expect(
      screen.getByRole("link", { name: "Skip to main content" }),
    ).toBeInTheDocument();
  });

  it("identifies the Exec surface in the header", () => {
    renderShell();
    expect(screen.getByTestId("exec-header")).toHaveTextContent("Exec");
  });

  it("renders no customer bottom tab bar", () => {
    renderShell();
    expect(screen.queryByTestId("bottom-tab-bar")).not.toBeInTheDocument();
  });
});

describe("ExecShell user menu", () => {
  it("offers Banking, Ops, and sign out to the customer+admin evaluator", async () => {
    const user = userEvent.setup();
    renderShell();
    await user.click(screen.getByTestId("exec-user-menu-trigger"));
    const items = await screen.findAllByRole("menuitem");
    expect(items.map((i) => i.textContent)).toEqual([
      "Banking",
      "Ops",
      "Sign out",
    ]);
    expect(screen.getByTestId("exec-user-menu-item-banking")).toHaveAttribute(
      "href",
      "/",
    );
    expect(screen.getByTestId("exec-user-menu-item-ops")).toHaveAttribute(
      "href",
      "/ops",
    );
  });

  it("shows the user's display name on the trigger", () => {
    renderShell();
    expect(screen.getByTestId("exec-user-menu-trigger")).toHaveTextContent(
      "Ada Lovelace",
    );
  });

  it("omits the Banking row for a staff-only user", async () => {
    const user = userEvent.setup();
    renderShell(makeUser(["ADMIN"]));
    await user.click(screen.getByTestId("exec-user-menu-trigger"));
    await screen.findByTestId("exec-user-menu-item-ops");
    expect(
      screen.queryByTestId("exec-user-menu-item-banking"),
    ).not.toBeInTheDocument();
    expect(
      screen.getByTestId("exec-user-menu-item-signout"),
    ).toBeInTheDocument();
  });

  it("offers no other surface to a manager-only user, only sign out", async () => {
    const user = userEvent.setup();
    renderShell(makeUser(["MANAGER"]));
    await user.click(screen.getByTestId("exec-user-menu-trigger"));
    const items = await screen.findAllByRole("menuitem");
    expect(items.map((i) => i.textContent)).toEqual(["Sign out"]);
    expect(
      screen.queryByTestId("exec-user-menu-item-banking"),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTestId("exec-user-menu-item-ops"),
    ).not.toBeInTheDocument();
  });

  it("signs out from the menu", async () => {
    const user = userEvent.setup();
    renderShell();
    await user.click(screen.getByTestId("exec-user-menu-trigger"));
    await user.click(screen.getByTestId("exec-user-menu-item-signout"));
    expect(signoutSpy).toHaveBeenCalledTimes(1);
  });
});
