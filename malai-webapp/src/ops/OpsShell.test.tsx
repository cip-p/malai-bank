import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";
import type { UserResponse } from "@/api/generated/types.gen";
import { ThemeProvider } from "@/theming/useTheme";
import { OpsShell } from "./OpsShell";

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

function Stage({
  children,
  path = "/ops",
}: {
  children: ReactNode;
  path?: string;
}) {
  return (
    <ThemeProvider>
      <MemoryRouter initialEntries={[path]}>{children}</MemoryRouter>
    </ThemeProvider>
  );
}

function renderShell(user: UserResponse = EVALUATOR) {
  return render(
    <Stage>
      <OpsShell user={user}>
        <div>ops content</div>
      </OpsShell>
    </Stage>,
  );
}

describe("OpsShell theming", () => {
  it("pins the default token set while mounted and restores the persisted brand on leave", () => {
    const view = render(<Stage>banking</Stage>);
    expect(document.documentElement.getAttribute("data-theme")).toBe("halcyon");
    view.rerender(
      <Stage>
        <OpsShell user={EVALUATOR}>
          <div>ops content</div>
        </OpsShell>
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

describe("OpsShell chrome", () => {
  it("renders the children inside the main landmark with a skip link", () => {
    renderShell();
    expect(screen.getByText("ops content")).toBeInTheDocument();
    expect(screen.getByRole("main")).toContainElement(
      screen.getByText("ops content"),
    );
    expect(
      screen.getByRole("link", { name: "Skip to main content" }),
    ).toBeInTheDocument();
  });

  it("identifies the Ops surface in the header", () => {
    renderShell();
    expect(screen.getByTestId("ops-header")).toHaveTextContent("Ops");
  });

  it("navigates with a Worklist link marked current on the worklist", () => {
    renderShell();
    const link = screen.getByTestId("ops-nav-link-worklist");
    expect(link).toHaveAttribute("href", "/ops");
    expect(link).toHaveAttribute("aria-current", "page");
    expect(screen.getByTestId("ops-nav-link-chain")).not.toHaveAttribute(
      "aria-current",
    );
    expect(screen.getByTestId("ops-nav-link-cases")).not.toHaveAttribute(
      "aria-current",
    );
  });

  it("seats Cases between the Worklist and the Chain", () => {
    renderShell();
    const labels = screen
      .getAllByRole("link")
      .filter((link) =>
        link.getAttribute("data-testid")?.startsWith("ops-nav-"),
      )
      .map((link) => link.textContent);
    expect(labels).toEqual(["Worklist", "Cases", "Chain"]);
    expect(screen.getByTestId("ops-nav-link-cases")).toHaveAttribute(
      "href",
      "/ops/cases",
    );
  });

  it("marks Cases current on the case list, a case, and a decision opened from one", () => {
    for (const path of [
      "/ops/cases",
      "/ops/cases/1a2b3c4d",
      "/ops/cases/1a2b3c4d/decisions/9e9e9e9e",
    ]) {
      const view = render(
        <Stage path={path}>
          <OpsShell user={EVALUATOR}>
            <div>ops content</div>
          </OpsShell>
        </Stage>,
      );
      expect(screen.getByTestId("ops-nav-link-cases")).toHaveAttribute(
        "aria-current",
        "page",
      );
      expect(screen.getByTestId("ops-nav-link-worklist")).not.toHaveAttribute(
        "aria-current",
      );
      view.unmount();
    }
  });

  it("navigates with a Chain link marked current on the chain page", () => {
    render(
      <Stage path="/ops/chain">
        <OpsShell user={EVALUATOR}>
          <div>ops content</div>
        </OpsShell>
      </Stage>,
    );
    const link = screen.getByTestId("ops-nav-link-chain");
    expect(link).toHaveAttribute("href", "/ops/chain");
    expect(link).toHaveAttribute("aria-current", "page");
    expect(screen.getByTestId("ops-nav-link-worklist")).not.toHaveAttribute(
      "aria-current",
    );
  });

  it("renders no customer bottom tab bar", () => {
    renderShell();
    expect(screen.queryByTestId("bottom-tab-bar")).not.toBeInTheDocument();
  });
});

describe("OpsShell user menu", () => {
  it("offers Banking, Exec, and sign out to the customer+admin evaluator", async () => {
    const user = userEvent.setup();
    renderShell();
    await user.click(screen.getByTestId("ops-user-menu-trigger"));
    const items = await screen.findAllByRole("menuitem");
    expect(items.map((i) => i.textContent)).toEqual([
      "Banking",
      "Exec",
      "Sign out",
    ]);
    expect(screen.getByTestId("ops-user-menu-item-banking")).toHaveAttribute(
      "href",
      "/",
    );
    expect(screen.getByTestId("ops-user-menu-item-exec")).toHaveAttribute(
      "href",
      "/exec",
    );
  });

  it("shows the user's display name on the trigger", () => {
    renderShell();
    expect(screen.getByTestId("ops-user-menu-trigger")).toHaveTextContent(
      "Ada Lovelace",
    );
  });

  it("omits the Banking row for a staff-only user", async () => {
    const user = userEvent.setup();
    renderShell(makeUser(["ADMIN"]));
    await user.click(screen.getByTestId("ops-user-menu-trigger"));
    await screen.findByTestId("ops-user-menu-item-exec");
    expect(
      screen.queryByTestId("ops-user-menu-item-banking"),
    ).not.toBeInTheDocument();
    expect(
      screen.getByTestId("ops-user-menu-item-signout"),
    ).toBeInTheDocument();
  });

  it("signs out from the menu", async () => {
    const user = userEvent.setup();
    renderShell();
    await user.click(screen.getByTestId("ops-user-menu-trigger"));
    await user.click(screen.getByTestId("ops-user-menu-item-signout"));
    expect(signoutSpy).toHaveBeenCalledTimes(1);
  });
});
